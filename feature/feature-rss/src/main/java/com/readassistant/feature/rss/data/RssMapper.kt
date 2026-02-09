package com.readassistant.feature.rss.data

import com.readassistant.core.data.db.entity.ArticleEntity
import com.readassistant.core.data.db.entity.FeedEntity
import com.readassistant.feature.rss.domain.Feed
import com.readassistant.feature.rss.domain.FeedArticle

fun FeedEntity.toDomain() = Feed(id = id, url = url, title = title, description = description, imageUrl = imageUrl, category = category, unreadCount = unreadCount, lastFetchedAt = lastFetchedAt)
fun Feed.toEntity() = FeedEntity(id = id, url = url, title = title, description = description, imageUrl = imageUrl, category = category, unreadCount = unreadCount, lastFetchedAt = lastFetchedAt)
fun ArticleEntity.toDomain() = FeedArticle(id = id, feedId = feedId, title = title, link = link, description = description, content = content.ifEmpty { extractedContent }, author = author, imageUrl = imageUrl, publishedAt = publishedAt, isRead = isRead, isStarred = isStarred)
