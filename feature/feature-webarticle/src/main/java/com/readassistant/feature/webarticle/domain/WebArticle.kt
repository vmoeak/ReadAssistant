package com.readassistant.feature.webarticle.domain

data class WebArticle(val id: Long = 0, val url: String, val title: String, val content: String = "", val textContent: String = "", val author: String = "", val imageUrl: String? = null, val siteName: String = "", val savedAt: Long = 0)
