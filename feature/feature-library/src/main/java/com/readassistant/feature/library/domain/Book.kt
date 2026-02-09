package com.readassistant.feature.library.domain

data class Book(
    val id: Long = 0,
    val filePath: String,
    val format: BookFormat,
    val title: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val totalChapters: Int = 0,
    val fileSize: Long = 0
)

enum class BookFormat(val extensions: List<String>, val mimeTypes: List<String>) {
    EPUB(listOf("epub"), listOf("application/epub+zip")),
    PDF(listOf("pdf"), listOf("application/pdf")),
    MOBI(listOf("mobi"), listOf("application/x-mobipocket-ebook")),
    AZW3(listOf("azw3", "azw"), listOf("application/vnd.amazon.ebook")),
    FB2(listOf("fb2"), listOf("application/x-fictionbook+xml")),
    TXT(listOf("txt"), listOf("text/plain")),
    HTML(listOf("html", "htm"), listOf("text/html")),
    CBZ(listOf("cbz"), listOf("application/x-cbz")),
    CBR(listOf("cbr"), listOf("application/x-cbr"));

    companion object {
        fun fromExtension(ext: String): BookFormat? =
            entries.find { ext.lowercase() in it.extensions }

        fun fromMimeType(mime: String): BookFormat? =
            entries.find { mime.lowercase() in it.mimeTypes }
    }
}
