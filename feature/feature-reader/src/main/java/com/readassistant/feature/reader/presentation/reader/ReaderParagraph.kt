package com.readassistant.feature.reader.presentation.reader

data class ReaderParagraph(
    val index: Int,
    val text: String,
    val isHeading: Boolean = false,
    val html: String = "",
    val imageSrc: String? = null
)
