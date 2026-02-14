package com.readassistant.feature.webarticle.data.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleExtractor @Inject constructor() {
    data class ExtractionResult(val title: String, val content: String, val textContent: String, val author: String, val imageUrl: String?, val siteName: String)

    suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        val normalizedUrl = if (!url.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) "https://$url" else url
        val isWechat = normalizedUrl.contains("mp.weixin.qq.com")
        val doc = Jsoup.connect(normalizedUrl)
            .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .timeout(15000)
            .followRedirects(true)
            .get()

        if (isWechat) extractWechat(doc, normalizedUrl) else extractGeneric(doc, normalizedUrl)
    }

    private fun extractGeneric(doc: Document, url: String): ExtractionResult {
        val title = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }
        val author = doc.select("meta[name=author]").attr("content")
        val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank { null }
        val siteName = doc.select("meta[property=og:site_name]").attr("content").ifBlank { try { java.net.URI(url).host.removePrefix("www.") } catch (_: Exception) { "" } }
        val content = extractContent(doc)
        val cleaned = ContentCleaner().clean(content, url)
        return ExtractionResult(title = title, content = cleaned, textContent = Jsoup.parse(cleaned).text(), author = author, imageUrl = imageUrl, siteName = siteName)
    }

    private fun extractWechat(doc: Document, url: String): ExtractionResult {
        // Title: WeChat uses og:title or #activity-name
        val title = doc.select("meta[property=og:title]").attr("content")
            .ifBlank { doc.select("#activity-name").text().trim() }
            .ifBlank { doc.title() }

        // Author: WeChat profile name is in #js_name or meta tag
        val author = doc.select("#js_name").text().trim()
            .ifBlank { doc.select("meta[name=author]").attr("content") }

        val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank { null }
        val siteName = author.ifBlank { "微信公众号" }

        // Content: WeChat article body is in #js_content (has visibility:hidden but HTML is present)
        val contentEl = doc.select("#js_content").first()
        val rawHtml = if (contentEl != null) {
            // Remove visibility:hidden so content renders
            contentEl.attr("style", "")
            // Convert WeChat lazy-loaded images: data-src → src
            contentEl.select("img[data-src]").forEach { img ->
                val dataSrc = img.attr("data-src")
                if (dataSrc.isNotBlank()) {
                    img.attr("src", dataSrc)
                }
            }
            // Remove WeChat UI elements that aren't article content
            contentEl.select(
                "#js_pc_qr_code, .qr_code_pc, .rich_media_tool, .reward_area, " +
                ".like_area, .function_mod, #js_toolbar, #content_bottom_area, " +
                ".video_fill, mpvoice, qqmusic, mp-miniprogram, mp-common-product"
            ).remove()
            contentEl.html()
        } else {
            // Fallback: try other WeChat content selectors
            val fallback = doc.select(".rich_media_content").first()
                ?: doc.select("#img-content").first()
            fallback?.html() ?: extractContent(doc)
        }

        val cleaned = ContentCleaner().clean(rawHtml, url)
        return ExtractionResult(
            title = title,
            content = cleaned,
            textContent = Jsoup.parse(cleaned).text(),
            author = author,
            imageUrl = imageUrl,
            siteName = siteName
        )
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
