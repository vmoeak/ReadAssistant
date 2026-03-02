package com.readassistant.feature.rss.data

import com.readassistant.core.data.db.dao.ArticleDao
import com.readassistant.core.data.db.dao.FeedDao
import com.readassistant.core.data.db.entity.FeedEntity
import com.readassistant.feature.rss.domain.Feed
import com.readassistant.feature.rss.domain.FeedArticle
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class FeedImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val invalidCount: Int
)

@Singleton
class RssRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val fetcher: RssFeedFetcher
) {
    fun getAllFeeds(): Flow<List<Feed>> = feedDao.getAllFeeds().map { list -> list.map { it.toDomain() } }
    fun getArticlesByFeed(feedId: Long): Flow<List<FeedArticle>> = articleDao.getArticlesByFeed(feedId).map { list -> list.map { it.toDomain() } }
    fun getStarredArticles(): Flow<List<FeedArticle>> = articleDao.getStarredArticles().map { list -> list.map { it.toDomain() } }

    suspend fun addFeed(url: String): Feed {
        val result = fetcher.fetchFeed(url)
        val entity = FeedEntity(url = url, title = result.title, description = result.description, imageUrl = result.imageUrl, lastFetchedAt = System.currentTimeMillis())
        val feedId = feedDao.insert(entity)
        val articles = result.articles.map { it.copy(feedId = feedId) }
        articleDao.insertAll(articles)
        feedDao.updateUnreadCount(feedId, articles.size)
        return entity.copy(id = feedId, unreadCount = articles.size).toDomain()
    }

    suspend fun refreshFeed(feedId: Long) {
        val feed = feedDao.getFeedById(feedId) ?: return
        val result = fetcher.fetchFeed(feed.url)
        val newArticles = result.articles.map { it.copy(feedId = feedId) }
            .filter { it.link.isNotBlank() }
            .filter { articleDao.getArticleByLink(it.link) == null }
        if (newArticles.isNotEmpty()) articleDao.insertAll(newArticles)
        feedDao.updateUnreadCount(feedId, articleDao.getUnreadCount(feedId))
        feedDao.updateLastFetched(feedId, System.currentTimeMillis())
    }

    suspend fun refreshAllFeeds() {
        val feeds = feedDao.getAllFeedsSync()
        feeds.forEach { try { refreshFeed(it.id) } catch (_: Exception) {} }
    }

    suspend fun exportFeedsAsOpml(): String {
        val feeds = feedDao.getAllFeedsSync()
        val outlines = feeds.joinToString(separator = "\n") { feed ->
            val title = xmlEscape(feed.title.ifBlank { feed.url })
            val url = xmlEscape(feed.url)
            """    <outline text="$title" title="$title" type="rss" xmlUrl="$url"/>"""
        }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<opml version="1.0">""")
            appendLine("  <head>")
            appendLine("    <title>ReadAssistant RSS Feeds</title>")
            appendLine("  </head>")
            appendLine("  <body>")
            appendLine(outlines)
            appendLine("  </body>")
            append("</opml>")
        }
    }

    suspend fun importFeedsFromOpml(content: String): FeedImportResult {
        val entries = parseOpmlOutlines(content)
        var imported = 0
        var skipped = 0
        var invalid = 0
        entries.forEach { (rawUrl, rawTitle) ->
            val normalizedUrl = normalizeImportedUrl(rawUrl)
            if (normalizedUrl == null) {
                invalid += 1
            } else {
                val feed = FeedEntity(
                    url = normalizedUrl,
                    title = rawTitle?.trim().orEmpty(),
                    description = "",
                    imageUrl = null,
                    category = "",
                    unreadCount = 0,
                    lastFetchedAt = 0
                )
                val insertedId = feedDao.insert(feed)
                if (insertedId > 0) {
                    imported += 1
                } else {
                    skipped += 1
                }
            }
        }
        return FeedImportResult(importedCount = imported, skippedCount = skipped, invalidCount = invalid)
    }

    suspend fun deleteFeed(feedId: Long) { feedDao.getFeedById(feedId)?.let { feedDao.delete(it) } }
    suspend fun markArticleRead(articleId: Long) {
        val article = articleDao.getArticleById(articleId) ?: return
        if (!article.isRead) {
            articleDao.updateReadStatus(articleId, true)
            val unreadCount = articleDao.getUnreadCount(article.feedId)
            feedDao.updateUnreadCount(article.feedId, unreadCount)
        }
    }
    suspend fun markAllArticlesRead(feedId: Long) {
        val changed = articleDao.markAllReadByFeed(feedId)
        if (changed > 0) feedDao.updateUnreadCount(feedId, 0)
    }
    suspend fun toggleArticleStar(articleId: Long) { articleDao.getArticleById(articleId)?.let { articleDao.updateStarredStatus(articleId, !it.isStarred) } }
    suspend fun getArticleById(articleId: Long): FeedArticle? = articleDao.getArticleById(articleId)?.toDomain()

    private fun parseOpmlOutlines(content: String): List<Pair<String, String?>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(content.reader())
        }
        val results = mutableListOf<Pair<String, String?>>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val url = parser.getAttributeValue(null, "xmlUrl")
                    ?: parser.getAttributeValue(null, "url")
                    ?: ""
                val title = parser.getAttributeValue(null, "title")
                    ?: parser.getAttributeValue(null, "text")
                results.add(url to title)
            }
            parser.next()
        }
        return results
    }

    private fun normalizeImportedUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("feed://", ignoreCase = true) -> "https://${trimmed.removePrefix("feed://")}"
            else -> null
        }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
