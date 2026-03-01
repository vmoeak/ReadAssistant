# 书籍秒开优化方案

> 目标：点击书籍后，读者界面秒开（<200ms 感知延迟），参考 legado 实现。

## 实施进度

| 优化项 | 状态 | 说明 |
|--------|------|------|
| 1.1 normalizeEntries/buildAnchorMap 优化 | ✅ 完成 | 预计算数据后同步 `remember` 即可（~5ms），无需 Jsoup |
| 1.2 预计算 NormalizedEntry 存入缓存 | ✅ 完成 | `buildFromHtml` 阶段提取 plainText/linkSpans/anchorIds |
| 1.3 SharedPreferences 异步化 | ✅ 完成 | `lazy` 初始化 + IO 线程读取 |
| 2.1 章节级加载（三章滑动窗口） | ⏸ 推迟 | 架构改动过大，需要将段落系统改为按章节加载 |
| 2.2 流式分页 | ✅ 完成 | 大书优先显示前几页，后台继续分页 |
| 3.1 ActiveBookState 单例 | ✅ 完成 | `@Singleton` 持有当前书籍状态，跨 ViewModel 生命周期 |
| 3.2 修复 prewarm 竞争 | ✅ 完成 | prewarm → ActiveBookState → ViewModel 无竞争 |
| 3.3 分页结果缓存 | ✅ 完成 | `PageCache` 单例缓存 entries/pages/anchorMap，二次打开零计算 |
| 4.1 延迟图片提取 | ✅ 完成 | 跳过已存在磁盘的图片，避免重复读取 zip |
| 4.2 增量 spine 解析 | ⏸ 推迟 | EPUB 解析只首次运行，后续走缓存 |
| 5.1 初始页码无动画跳转 | ✅ 完成 | 首次 seek 用 `scrollToPage`（无动画），后续用 `animateScrollToPage` |
| 5.2 Pager 正确初始页 | ✅ 完成 | `rememberPagerState(initialPage = startPage)` + `alpha(0f)` 防止闪白 |
| 5.3 导航动画移除 | ✅ 完成 | Reader 路由 `EnterTransition.None` / `ExitTransition.None`，消除 ~300ms 过渡 |

---

## 当前瓶颈分析

通过完整追踪从「点击书籍 → 内容显示」的链路，发现以下瓶颈：

### P0 - 主线程阻塞（直接导致卡顿）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | **`normalizeEntries()` 在主线程跑 Jsoup.parse()** | `NativeBookReader.kt:152` | 每个段落都调一次 `Jsoup.parse()`，500 段落 = 500 次解析，在 `remember {}` 中同步执行 |
| 2 | **`buildAnchorMap()` 在主线程跑 Jsoup.parse()** | `NativeBookReader.kt:153` | 同上，又遍历所有段落做 HTML 解析 |
| 3 | **SharedPreferences 在构造函数同步读取** | `ReaderViewModel.kt:32` | `getSharedPreferences()` 首次访问会加载整个文件到内存 |

### P1 - 加载路径过长（间接导致延迟）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 4 | **整本书一次性加载** | `BookReadingRepository.kt:118-160` | 不分章节，把整本书的 HTML → 段落全部加载完才显示 |
| 5 | **分页必须等全部段落就绪** | `NativeBookReader.kt:181-197` | `paginateEntries()` 依赖完整的 entries 列表 |
| 6 | **prewarm 与 ViewModel init 存在竞争** | `LibraryScreen.kt:76` vs `ReaderViewModel.kt:51` | 点击时触发 prewarm 和导航同时进行，但 ViewModel init 的 `tryLoadFromMemoryCache` 可能在 prewarm 完成前执行，导致 cache miss |
| 7 | **EPUB 首次打开要提取所有图片到磁盘** | `EpubParser.kt:342-368` | 遍历所有 spine 条目，每张图片都 `materializeImageFile()` |

---

## 优化方案（按优先级排序）

### 阶段一：消除主线程卡顿（预计效果：消除明显顿感）

#### 1.1 将 normalizeEntries / buildAnchorMap 移到后台线程

**现状**：`remember(paragraphs) { normalizeEntries(paragraphs) }` 在首次 composition 时同步执行。

**方案**：改用 `produceState` + `Dispatchers.Default`，与分页计算并行。

```kotlin
// Before (主线程阻塞)
val entries = remember(paragraphs) { normalizeEntries(paragraphs) }

// After (后台计算)
val entriesState = produceState(emptyList(), paragraphs) {
    value = withContext(Dispatchers.Default) { normalizeEntries(paragraphs) }
}
```

**文件**：`NativeBookReader.kt`

#### 1.2 预计算 NormalizedEntry，存入缓存

**现状**：每次打开书都要从 `ReaderParagraph` → `NormalizedEntry` 做 Jsoup 解析。

**方案**：在 `BookParagraphCache.buildFromHtml()` 阶段就提取好纯文本、链接 spans 等信息，存入 `CachedParagraph`。`NativeBookReader` 直接使用，无需再次 Jsoup 解析。

**涉及文件**：
- `BookParagraphCache.kt` - `buildFromHtml()` 增加字段提取
- `CachedParagraph` data class - 新增 `plainText`, `linkSpans`, `anchorIds` 字段
- `NativeBookReader.kt` - `normalizeEntries()` 直接使用预计算数据

#### 1.3 SharedPreferences 改为异步读取

**现状**：`ReaderViewModel` 构造函数中 `appContext.getSharedPreferences(...)` 同步加载。

**方案**：改用 DataStore（项目已有依赖）或在 `Dispatchers.IO` 上延迟读取，页码恢复用 `LaunchedEffect` 异步处理。

**文件**：`ReaderViewModel.kt`

---

### 阶段二：渐进式加载（预计效果：大书也秒开）

#### 2.1 章节级加载 - 优先加载当前章节

**现状**：`loadBookForReading()` 一次性加载整本书所有段落。

**方案**：借鉴 legado 的三章滑动窗口模式：
1. 将 `CachedBookContent` 改为按章节组织：`Map<Int, List<CachedParagraph>>`
2. 打开书时只加载**当前章节 + 前后各一章**
3. 翻页时异步预加载下一章
4. 用 `Channel` 实现流式加载，当前章节就绪即可渲染

**加载顺序**：
```
当前章节 (立即加载) → 下一章 (预加载) → 上一章 (预加载) → 其余章节 (懒加载)
```

**涉及文件**：
- `BookReadingRepository.kt` - 拆分为章节级加载
- `BookParagraphCache.kt` - 按章节存储/读取
- `ReaderViewModel.kt` - 章节级状态管理
- `NativeBookReader.kt` - 支持增量段落追加

#### 2.2 流式分页 - 先显示第一页

**现状**：`paginateEntries()` 必须拿到所有 entries 才开始分页。

**方案**：借鉴 legado 的 `layoutChannel` 模式：
1. 分页器逐页产出，通过 `Channel<ReaderPage>` 流式发射
2. 第一页就绪后立即渲染，后续页在后台继续计算
3. `HorizontalPager` 的 `pageCount` 动态增长

```kotlin
// 流式分页
val pagesFlow = channelFlow {
    var currentPage = mutableListOf<NormalizedEntry>()
    var currentHeight = 0f
    for (entry in entries) {
        val h = measureEntryHeight(entry, ...)
        if (currentHeight + h > pageHeight && currentPage.isNotEmpty()) {
            send(ReaderPage(currentPage.toList()))  // 产出一页，UI 可以立即显示
            currentPage = mutableListOf()
            currentHeight = 0f
        }
        currentPage.add(entry)
        currentHeight += h
    }
    if (currentPage.isNotEmpty()) send(ReaderPage(currentPage))
}
```

**文件**：`NativeBookReader.kt`

---

### 阶段三：缓存与预热增强（预计效果：二次打开真正零延迟）

#### 3.1 持久化阅读状态单例

**现状**：每次打开书都要创建新的 `ReaderViewModel`，重新从 DB 查询进度。

**方案**：借鉴 legado 的 `ReadBook` object 模式，创建一个 `ActiveBookState` 单例（Hilt `@Singleton`）：
- 持有当前打开书籍的完整状态（段落、章节、进度、分页结果）
- 再次打开同一本书时直接复用，零加载
- 切换书籍时才重置

```kotlin
@Singleton
class ActiveBookState @Inject constructor() {
    var bookId: Long = -1
    var paragraphs: List<CachedParagraph> = emptyList()
    var chapters: List<CachedChapter> = emptyList()
    var pages: List<ReaderPage> = emptyList()  // 分页结果也缓存
    var currentPageIndex: Int = 0

    fun isBookLoaded(id: Long): Boolean = bookId == id && paragraphs.isNotEmpty()
}
```

**涉及文件**：
- 新建 `ActiveBookState.kt`（core-domain 或 feature-reader）
- `ReaderViewModel.kt` - 优先从 ActiveBookState 取数据

#### 3.2 修复 prewarm 竞争条件

**现状**：点击时 prewarm 和 navigate 同时触发，但 ViewModel init 可能在 prewarm 完成前就检查缓存。

**方案**：
1. prewarm 结果写入 `ActiveBookState` 单例（而不仅仅是 `BookParagraphCache`）
2. ViewModel 先检查 `ActiveBookState`，命中则零成本
3. 如果未命中，再走正常缓存链路
4. 考虑在 `LibraryScreen` 做更激进的预热：用户长按/悬停时就开始预加载

**文件**：
- `LibraryScreen.kt` - prewarm 写入 ActiveBookState
- `ReaderViewModel.kt` - 优先读取 ActiveBookState

#### 3.3 缓存分页结果

**现状**：每次打开书都要重新测量和分页（`paginateEntries` 涉及大量 `StaticLayout` 创建）。

**方案**：将分页结果（`List<ReaderPage>`）缓存到 `ActiveBookState` 和磁盘。分页结果的 cache key 包含：bookId + fontSize + lineHeight + contentWidth + contentHeight。同样的阅读设置下再次打开，直接使用缓存的分页结果。

**文件**：
- `ActiveBookState.kt` - 缓存分页结果
- 新建 `PageLayoutCache.kt` - 磁盘分页缓存

---

### 阶段四：EPUB 解析优化（预计效果：首次打开大书提速）

#### 4.1 延迟图片提取

**现状**：`EpubParser.extractContent()` 遍历所有 spine 条目，提取每张图片到磁盘。

**方案**：
1. 解析阶段只记录图片引用（zip entry path），不实际提取文件
2. 图片在 `NativeBookReader` 渲染时由 Coil 按需加载
3. 自定义 Coil `Fetcher`，直接从 EPUB zip 中流式读取图片数据

**涉及文件**：
- `EpubParser.kt` - 只记录 `img[src]` 映射
- 新建 `EpubImageFetcher.kt` - Coil 自定义 Fetcher，从 zip 读取

#### 4.2 增量 spine 解析

**现状**：一次性解析所有 spine 条目。

**方案**：配合阶段二的章节级加载，只解析当前需要的 spine 条目：
1. 先解析 OPF 获取 spine 列表和章节信息（很快）
2. 只解析当前章节对应的 spine 条目
3. 其余 spine 条目按需解析

**文件**：`EpubParser.kt`

---

## 实施路线图

```
阶段一（1-2天）── 消除主线程卡顿
  ├─ 1.1 normalizeEntries/buildAnchorMap 移到后台
  ├─ 1.2 预计算 NormalizedEntry 存入缓存
  └─ 1.3 SharedPreferences 异步化

阶段二（3-4天）── 渐进式加载
  ├─ 2.1 章节级加载（三章滑动窗口）
  └─ 2.2 流式分页（先出第一页）

阶段三（2-3天）── 缓存增强
  ├─ 3.1 ActiveBookState 单例
  ├─ 3.2 修复 prewarm 竞争
  └─ 3.3 分页结果缓存

阶段四（2-3天）── EPUB 解析优化
  ├─ 4.1 延迟图片提取 + Coil Fetcher
  └─ 4.2 增量 spine 解析
```

## 预期效果

| 场景 | 当前 | 阶段一后 | 阶段二后 | 阶段三后 |
|------|------|----------|----------|----------|
| 二次打开（内存缓存命中） | ~300-500ms（主线程 Jsoup 阻塞） | ~100ms | ~50ms | ~0ms（直接复用） |
| 二次打开（磁盘缓存命中） | ~500-800ms | ~200ms | ~100ms | ~50ms |
| 首次打开（小书 <1MB） | ~1-2s | ~800ms | ~300ms | ~300ms |
| 首次打开（大书 >10MB） | ~3-5s | ~2-3s | ~500ms（仅加载当前章节） | ~500ms |

## legado 关键经验总结

| 策略 | legado 做法 | 我们的适配 |
|------|------------|-----------|
| 全局单例状态 | `ReadBook` object，跨 Activity 生命周期存活 | `ActiveBookState` @Singleton |
| 延迟初始化 | `IdleHandler` 延迟重度初始化到首帧后 | `produceState` / `LaunchedEffect` 异步化 |
| 三章滑动窗口 | prev/cur/next 三章常驻内存 | 章节级加载 + 预加载前后章 |
| 文件级章节缓存 | 每章一个文件，存在性检查即 cache hit | 已有类似机制，增加章节粒度 |
| 流式分页 | `layoutChannel` 逐页产出 | `channelFlow` 流式分页 |
| 加载去重 | `loadingChapters` + Mutex | 确保 prewarm 和 init 不重复加载 |
| 异步存进度 | `executor.execute{}` 非阻塞保存 | DataStore 异步写入 |
