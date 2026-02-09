package com.readassistant.feature.rss.data

import com.prof18.rssparser.RssParser
import com.readassistant.core.data.db.entity.ArticleEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFeedFetcher @Inject constructor() {
    private val parser = RssParser()

    suspend fun fetchFeed(url: String): FetchResult {
        val channel = parser.getRssChannel(url)
        return FetchResult(
            title = channel.title ?: "",
            description = channel.description ?: "",
            imageUrl = channel.image?.url,
            articles = channel.items.map { item ->
                ArticleEntity(feedId = 0, title = item.title ?: "", link = item.link ?: "", description = item.description ?: "", content = item.content ?: "", author = item.author ?: "", imageUrl = item.image, publishedAt = item.pubDate?.let { parseDate(it) } ?: System.currentTimeMillis())
            }
        )
    }

    private fun parseDate(dateStr: String): Long = try {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH).parse(dateStr)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }

    data class FetchResult(val title: String, val description: String, val imageUrl: String?, val articles: List<ArticleEntity>)
}
