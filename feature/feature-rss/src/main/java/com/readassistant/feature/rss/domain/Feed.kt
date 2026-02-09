package com.readassistant.feature.rss.domain

data class Feed(
    val id: Long = 0,
    val url: String,
    val title: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val unreadCount: Int = 0
)

data class FeedArticle(
    val id: Long = 0,
    val feedId: Long,
    val title: String = "",
    val link: String = "",
    val description: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val author: String? = null,
    val publishedAt: Long = 0,
    val isRead: Boolean = false,
    val isStarred: Boolean = false
)
