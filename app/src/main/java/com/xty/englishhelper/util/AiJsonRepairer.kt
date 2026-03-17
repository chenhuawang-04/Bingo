package com.xty.englishhelper.util

object AiJsonRepairer {
    fun repair(json: String): String {
        if (json.isBlank()) return json
        val sb = StringBuilder(json.length + 16)
        var inString = false
        var escaped = false

        var i = 0
        while (i < json.length) {
            val ch = json[i]
            if (escaped) {
                sb.append(ch)
                escaped = false
                i++
                continue
            }
            when (ch) {
                '\\' -> {
                    sb.append(ch)
                    escaped = true
                }
                '"' -> {
                    if (inString) {
                        val next = nextNonWhitespace(json, i + 1)
                        if (next == null || next == ',' || next == '}' || next == ']' || next == ':') {
                            inString = false
                            sb.append(ch)
                        } else {
                            sb.append("\\\"")
                        }
                    } else {
                        inString = true
                        sb.append(ch)
                    }
                }
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    private fun nextNonWhitespace(text: String, start: Int): Char? {
        var i = start
        while (i < text.length) {
            val ch = text[i]
            if (!ch.isWhitespace()) return ch
            i++
        }
        return null
    }
}
