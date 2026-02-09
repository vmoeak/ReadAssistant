package com.readassistant.feature.webarticle.data.extractor

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

class ContentCleaner {
    fun clean(html: String, baseUrl: String): String {
        val safe = Safelist.relaxed().addAttributes("img", "src", "alt").addAttributes("a", "href")
        var cleaned = Jsoup.clean(html, baseUrl, safe)
        val doc = Jsoup.parse(cleaned, baseUrl)
        doc.select("img").forEach { it.attr("src", it.attr("abs:src")) }
        doc.select("a").forEach { it.attr("href", it.attr("abs:href")) }
        doc.select("p:empty,div:empty,span:empty").remove()
        return doc.body()?.html() ?: cleaned
    }
}
