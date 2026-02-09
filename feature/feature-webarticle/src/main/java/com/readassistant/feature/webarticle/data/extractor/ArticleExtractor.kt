package com.readassistant.feature.webarticle.data.extractor

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleExtractor @Inject constructor() {
    data class ExtractionResult(val title: String, val content: String, val textContent: String, val author: String, val imageUrl: String?, val siteName: String)

    suspend fun extract(url: String): ExtractionResult {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36").timeout(15000).get()
        val title = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }
        val author = doc.select("meta[name=author]").attr("content")
        val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank { null }
        val siteName = doc.select("meta[property=og:site_name]").attr("content").ifBlank { try { java.net.URI(url).host.removePrefix("www.") } catch (_: Exception) { "" } }
        val content = extractContent(doc)
        val cleaned = ContentCleaner().clean(content, url)
        return ExtractionResult(title = title, content = cleaned, textContent = Jsoup.parse(cleaned).text(), author = author, imageUrl = imageUrl, siteName = siteName)
    }

    private fun extractContent(doc: Document): String {
        for (sel in listOf("article", "[role=main]", ".post-content", ".article-content", ".entry-content", "main")) {
            val el = doc.select(sel).first(); if (el != null && el.text().length > 200) return el.html()
        }
        val body = doc.body() ?: return doc.html()
        body.select("nav,header,footer,aside,.sidebar,.menu,.comments,.ad,script,style,iframe").remove()
        return body.html()
    }
}
