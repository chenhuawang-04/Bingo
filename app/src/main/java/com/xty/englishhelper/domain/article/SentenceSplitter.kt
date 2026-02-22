package com.xty.englishhelper.domain.article

data class SentenceSpan(
    val text: String,
    val charStart: Int,
    val charEnd: Int
)

object SentenceSplitter {
    private val ABBREVIATIONS = setOf(
        "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "st.", "vs.", "etc.", "e.g.", "i.e."
    )

    fun split(content: String): List<SentenceSpan> {
        val sentences = mutableListOf<SentenceSpan>()
        var currentStart = 0
        var i = 0

        while (i < content.length) {
            val char = content[i]
            // Look for sentence-ending punctuation
            if (char in setOf('.', '!', '?')) {
                // Check if it's an abbreviation
                val before = content.substring(maxOf(0, i - 4), i).lowercase()
                val isAbbrev = ABBREVIATIONS.any { before.endsWith(it) }

                if (!isAbbrev && i + 1 < content.length) {
                    val nextChar = content[i + 1]
                    // Sentence boundary if followed by whitespace and next word starts with uppercase
                    if (nextChar.isWhitespace()) {
                        var nextWordStart = i + 2
                        while (nextWordStart < content.length && content[nextWordStart].isWhitespace()) {
                            nextWordStart++
                        }
                        if (nextWordStart < content.length && content[nextWordStart].isUpperCase()) {
                            val sentenceText = content.substring(currentStart, i + 1).trim()
                            if (sentenceText.isNotEmpty()) {
                                sentences.add(SentenceSpan(sentenceText, currentStart, i + 1))
                            }
                            currentStart = nextWordStart
                            i = nextWordStart - 1
                        }
                    }
                } else if (isAbbrev) {
                    // Skip abbreviation period
                }
            }
            i++
        }

        // Add remaining text
        if (currentStart < content.length) {
            val remaining = content.substring(currentStart).trim()
            if (remaining.isNotEmpty()) {
                sentences.add(SentenceSpan(remaining, currentStart, content.length))
            }
        }

        return sentences
    }
}
