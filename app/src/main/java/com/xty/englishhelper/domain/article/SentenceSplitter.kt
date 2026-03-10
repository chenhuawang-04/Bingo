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

object SmartParagraphSplitter {
    fun split(content: String): List<String> {
        // If content already has blank-line paragraph breaks, use them
        val blankLineSplit = content.split(Regex("\\n\\s*\\n"))
        if (blankLineSplit.size > 1) {
            return blankLineSplit.map { it.trim() }.filter { it.isNotBlank() }
        }

        // If content has single newlines, treat each as a paragraph
        val newLineSplit = content.split("\n")
        if (newLineSplit.size > 1) {
            return newLineSplit.map { it.trim() }.filter { it.isNotBlank() }
        }

        // Otherwise, split by sentence groups (~3-5 sentences per paragraph)
        val sentenceEnders = Regex("(?<=[.!?])\\s+(?=[A-Z])")
        val sentences = sentenceEnders.split(content).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.size <= 4) return listOf(content.trim())

        val paragraphs = mutableListOf<String>()
        val buffer = mutableListOf<String>()
        for (sentence in sentences) {
            buffer.add(sentence)
            if (buffer.size >= 4) {
                paragraphs.add(buffer.joinToString(" "))
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) {
            paragraphs.add(buffer.joinToString(" "))
        }
        return paragraphs
    }
}
