package com.readassistant.core.llm.streaming

class SseParser {
    private var buffer = StringBuilder()

    data class SseEvent(
        val event: String = "",
        val data: String = "",
        val id: String = ""
    )

    fun parse(line: String): SseEvent? {
        if (line.isEmpty()) {
            val event = parseBuffer()
            buffer = StringBuilder()
            return event
        }
        buffer.appendLine(line)
        return null
    }

    private fun parseBuffer(): SseEvent? {
        if (buffer.isEmpty()) return null
        var event = ""
        val dataLines = mutableListOf<String>()
        var id = ""
        buffer.lines().forEach { line ->
            when {
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataLines.add(line.removePrefix("data:").trim())
                line.startsWith("id:") -> id = line.removePrefix("id:").trim()
            }
        }
        if (dataLines.isEmpty() && event.isEmpty()) return null
        return SseEvent(event = event, data = dataLines.joinToString("\n"), id = id)
    }

    fun reset() {
        buffer = StringBuilder()
    }
}
