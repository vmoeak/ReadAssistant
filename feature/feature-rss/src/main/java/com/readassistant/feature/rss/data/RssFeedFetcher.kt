package com.readassistant.feature.rss.data

import com.prof18.rssparser.RssParser
import com.readassistant.core.data.db.entity.ArticleEntity
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
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

    private fun parseDate(dateStr: String): Long {
        val raw = dateStr.trim()
        if (raw.isBlank()) return System.currentTimeMillis()

        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { ZonedDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .getOrNull()
            ?.let { return it }

        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)?.time
            }.getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }

    data class FetchResult(val title: String, val description: String, val imageUrl: String?, val articles: List<ArticleEntity>)
}
