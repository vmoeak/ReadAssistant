package com.readassistant.feature.reader.data.content

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic HTML content loader for articles, FB2, TXT, and HTML book formats.
 */
@Singleton
class HtmlContentLoader @Inject constructor() {

    suspend fun loadContent(htmlContent: String): String {
        return htmlContent
    }

    fun wrapInReaderTemplate(title: String, content: String): String {
        return """
            <article>
                <h1>$title</h1>
                $content
            </article>
        """.trimIndent()
    }
}
