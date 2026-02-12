# ReadAssistant - Agent Instructions

> AI-assisted reading app for Android. Supports EPUB, PDF, MOBI, FB2, TXT, HTML, CBZ/CBR books, RSS feeds, and web articles with bilingual translation and LLM-powered Q&A.

## !! HIGH PRIORITY - Build Environment

**Before building, locate JAVA_HOME from the workspace first.** Run this to auto-detect:

```bash
# First look for JDK bundled in the repo, fall back to system JDK only if not found
if [ -d "$PWD/temp_java/jdk-17.0.10+7/Contents/Home" ]; then
  export JAVA_HOME="$PWD/temp_java/jdk-17.0.10+7/Contents/Home"
elif [ -d "$PWD/temp_java/jdk-17.0.10+7" ]; then
  export JAVA_HOME="$PWD/temp_java/jdk-17.0.10+7"
elif LOCAL_JDK=$(find "$PWD" -maxdepth 3 -type d -name "jdk-*" -print -quit 2>/dev/null) && [ -n "$LOCAL_JDK" ]; then
  # Scan workspace for any JDK directory
  [ -d "$LOCAL_JDK/Contents/Home" ] && export JAVA_HOME="$LOCAL_JDK/Contents/Home" || export JAVA_HOME="$LOCAL_JDK"
else
  echo "WARNING: No local JDK found in workspace, falling back to system JDK"
fi
```

**Priority order**: workspace bundled JDK > workspace any JDK > system JDK. Always prefer the workspace copy.

## !! HIGH PRIORITY - Post-Change Verification Workflow

**Every time you finish modifying code, you MUST follow this loop:**

1. **Build**: `JAVA_HOME="$PWD/temp_java/jdk-17.0.10+7/Contents/Home" ./gradlew assembleDebug`
2. **Check device**: `adb devices` — verify a device/emulator is connected
3. **Install**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. **Self-test**: Use `adb shell am start -n com.readassistant/.MainActivity` to launch the app, then verify the change works via `adb logcat` or UI automation
5. **If the issue persists**: Analyze logs, fix the code, and **repeat from step 1** until the problem is resolved
6. **Do NOT consider a task complete until the app runs on-device without the reported issue**

```bash
# Quick verification one-liner
export JAVA_HOME="$PWD/temp_java/jdk-17.0.10+7/Contents/Home" && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.readassistant/.MainActivity
```

> This is non-negotiable. Code that only compiles but hasn't been verified on a real device is not "done".

## Quick Reference

- **Language**: Kotlin only (no Java)
- **Min SDK**: 26 | **Target/Compile SDK**: 34
- **Build**: Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`)
- **DI**: Hilt 2.50 with KSP
- **UI**: Jetpack Compose + Material3
- **DB**: Room 2.6.1 (database: `readassistant.db`)
- **Async**: Kotlin Coroutines + StateFlow
- **Java**: JDK 17

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew :app:installDebug      # Install on connected device
./gradlew kspDebugKotlin         # Run KSP annotation processing (Hilt/Room)
./gradlew lint                   # Run Android lint
```

## Module Structure

```
app/                          # Single-activity host, NavGraph, Hilt entry point
core/
  core-domain/                # Pure domain models (no framework deps)
  core-data/                  # Room DB, DAOs (10 tables), DataStore preferences
  core-network/               # OkHttp/Retrofit DI module
  core-ui/                    # Shared Compose components, theme (Light/Sepia/Dark)
  core-llm/                   # LLM abstraction: 5 providers (OpenAI, Claude, Gemini, DeepSeek, Custom)
feature/
  feature-library/            # Book management, parsers (EPUB/PDF/MOBI/FB2/TXT/HTML/CBZ)
  feature-reader/             # Reading UI: NativeBookReader (Compose) + WebViewReader
  feature-rss/                # RSS feed sync (WorkManager), feed/article list
  feature-webarticle/         # Web article extraction (Jsoup), save/manage
  feature-translation/        # Bilingual side-by-side translation (semaphore-limited)
  feature-chat/               # AI Q&A bottom sheet with streaming SSE
  feature-settings/           # User preferences, LLM provider config
```

### Module Dependency Rules

- `core-domain` has ZERO framework dependencies (only coroutines-core) - keep it pure
- Feature modules depend on core modules, never on other feature modules (exception: `feature-reader` depends on `feature-translation`, `feature-library`, `feature-chat`)
- All new dependencies MUST be added to `gradle/libs.versions.toml` first
- Use `implementation` for internal deps, `api` only when types are exposed

## Architecture & Patterns

**MVVM + Clean Architecture (adapted)**

- **ViewModel**: Always `@HiltViewModel` + `@Inject constructor`. Expose state via `StateFlow` (private `MutableStateFlow` + public `asStateFlow()`). Use `viewModelScope` for coroutines.
- **UI State**: Single immutable `data class` per screen (e.g., `ReaderUiState`). Update via `_state.update { it.copy(...) }`.
- **Compose Screens**: Stateless composables that receive state + callbacks. Collect state with `collectAsState()`.
- **IO Operations**: Always wrap with `withContext(Dispatchers.IO)`.
- **MVI (ChatViewModel only)**: Uses `Channel<ChatIntent>` for typed intent processing.

### Key Patterns

```kotlin
// ViewModel pattern
@HiltViewModel
class MyViewModel @Inject constructor(
    private val dao: MyDao
) : ViewModel() {
    private val _state = MutableStateFlow(MyUiState())
    val state = _state.asStateFlow()
}

// Screen pattern
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    // ...
}
```

### LLM Provider Pattern

`LlmProvider` interface with implementations in `core-llm`. Uses raw OkHttp (not Retrofit) for SSE streaming. Add new providers by implementing `LlmProvider` and registering in `LlmService`.

### Book Parser Pattern

`BookParser` interface in `feature-library`. Add new formats by implementing the interface and registering in `BookParserFactory`.

### Two-Tier Book Cache

- `BookContentCache`: HTML content cache
- `BookParagraphCache`: Structured paragraphs (memory LRU + disk JSON)

## Coding Conventions

### DO

- Write Kotlin only, follow `kotlin.code.style=official`
- Use Hilt for all DI - annotate modules with `@Module @InstallIn`
- Use Room `@Dao` interfaces for all DB access
- Use `StateFlow` for reactive state, never `LiveData`
- Use `sealed class` / `sealed interface` for type-safe variants
- Use `data class` for state objects
- Use Compose Material3 components and the project's theme system
- Handle errors explicitly with try-catch or `Result`/`NetworkResult`
- Use string resources for user-facing text

### DON'T

- Don't add Java files
- Don't use `LiveData`, `RxJava`, or `Flow.asLiveData()`
- Don't inject DAOs directly in ViewModels for new features (use a repository)
- Don't add dependencies without version catalog entry
- Don't use `System.out.println()` or `android.util.Log` (use a logging abstraction if needed)
- Don't put ViewModel classes in the same file as Screen composables
- Don't create non-exported Room schemas (`exportSchema` is currently false, but don't make it worse)

## Navigation

Single-activity with Jetpack Navigation Compose. Routes defined as `sealed class Screen` in `app/`. Add new screens by:

1. Add a route to the `Screen` sealed class
2. Add a `composable()` entry in `NavGraph`
3. If top-level, add to `BottomNavBar`

## Key Files

| Purpose | Path |
|---------|------|
| App entry + Hilt | `app/src/main/java/.../ReadAssistantApp.kt` |
| Navigation | `app/src/main/java/.../navigation/NavGraph.kt` |
| DI module | `app/src/main/java/.../di/AppModule.kt` |
| Room DB | `core/core-data/src/main/java/.../data/local/AppDatabase.kt` |
| LLM service | `core/core-llm/src/main/java/.../llm/LlmService.kt` |
| Theme | `core/core-ui/src/main/java/.../ui/theme/` |
| Version catalog | `gradle/libs.versions.toml` |
| Manifest | `app/src/main/AndroidManifest.xml` |

## Testing

No tests exist yet. When adding tests:

- Unit tests: `src/test/java/` with JUnit 5 + MockK
- UI tests: `src/androidTest/java/` with Compose testing
- Use Hilt testing utilities for ViewModel tests
- Test ViewModels by collecting StateFlow emissions
