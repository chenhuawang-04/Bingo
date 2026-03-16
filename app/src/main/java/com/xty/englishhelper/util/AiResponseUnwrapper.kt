package com.xty.englishhelper.util

import org.json.JSONArray
import org.json.JSONObject

object AiResponseUnwrapper {
    fun unwrapJsonEnvelope(jsonText: String): String? {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null

        val choices = root.optJSONArray("choices")
        if (choices != null) {
            val firstChoice = choices.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val content = message?.let { extractMessageContent(it) }
                ?: firstChoice?.optString("text").orEmpty()
            if (content.isNotBlank()) return content
        }

        val contentArray = root.optJSONArray("content")
        if (contentArray != null) {
            val first = contentArray.optJSONObject(0)
            val text = first?.optString("text").orEmpty()
            if (text.isNotBlank()) return text
            val fallback = contentArray.optString(0, "")
            if (fallback.isNotBlank()) return fallback
        }

        val completion = root.optString("completion", "")
        if (completion.isNotBlank()) return completion

        return null
    }

    private fun extractMessageContent(message: JSONObject): String? {
        val contentValue = message.opt("content")
        return when (contentValue) {
            is String -> contentValue
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until contentValue.length()) {
                    val part = contentValue.optJSONObject(i)
                    val text = part?.optString("text").orEmpty()
                    if (text.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(text)
                    }
                }
                if (sb.isNotEmpty()) sb.toString() else null
            }
            else -> null
        }
    }
}
