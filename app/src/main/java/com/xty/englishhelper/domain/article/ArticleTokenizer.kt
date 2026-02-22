package com.xty.englishhelper.domain.article

data class TokenOccurrence(
    val token: String,
    val normalizedToken: String,
    val sentenceIndex: Int
)

data class TokenFrequencyEntry(
    val displayToken: String,
    val frequency: Int
)

object ArticleTokenizer {
    private val PUNCTUATION = setOf(',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '-', '—')

    fun tokenize(sentences: List<SentenceSpan>): List<TokenOccurrence> {
        val tokens = mutableListOf<TokenOccurrence>()
        sentences.forEachIndexed { sentenceIndex, span ->
            val words = span.text.split(Regex("\\s+"))
            words.forEach { word ->
                var clean = word
                while (clean.isNotEmpty() && clean.first() in PUNCTUATION) {
                    clean = clean.drop(1)
                }
                while (clean.isNotEmpty() && clean.last() in PUNCTUATION) {
                    clean = clean.dropLast(1)
                }

                if (clean.isNotEmpty() && clean.first().isLetter()) {
                    val normalized = clean.lowercase().trim()
                    tokens.add(TokenOccurrence(clean, normalized, sentenceIndex))
                }
            }
        }
        return tokens
    }

    fun aggregate(tokens: List<TokenOccurrence>): Map<String, TokenFrequencyEntry> {
        val freqMap = mutableMapOf<String, Pair<String, Int>>() // normalized -> (display, count)
        tokens.forEach { token ->
            val existing = freqMap[token.normalizedToken]
            if (existing != null) {
                freqMap[token.normalizedToken] = existing.first to (existing.second + 1)
            } else {
                freqMap[token.normalizedToken] = token.token to 1
            }
        }
        return freqMap.mapValues { (_, v) -> TokenFrequencyEntry(v.first, v.second) }
    }
}
