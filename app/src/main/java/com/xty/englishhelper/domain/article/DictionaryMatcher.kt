package com.xty.englishhelper.domain.article

data class WordRef(
    val wordId: Long,
    val dictionaryId: Long,
    val normalizedSpelling: String
)

data class WordMatchResult(
    val wordId: Long,
    val dictionaryId: Long,
    val sentenceIndex: Int,
    val matchedToken: String
)

class DictionaryMatcher(
    words: List<WordRef>,
    private val inflectionMap: Map<Long, List<String>>
) {
    private val index: Map<String, List<WordRef>> = buildIndex(words)

    private fun buildIndex(words: List<WordRef>): Map<String, List<WordRef>> {
        val idx = mutableMapOf<String, MutableList<WordRef>>()
        words.forEach { word ->
            // Add the normalized spelling
            idx.getOrPut(word.normalizedSpelling) { mutableListOf() }.add(word)

            // Add all inflection forms
            val inflections = inflectionMap[word.wordId] ?: emptyList()
            inflections.forEach { form ->
                val normalized = form.lowercase().trim()
                idx.getOrPut(normalized) { mutableListOf() }.add(word)
            }
        }
        return idx
    }

    fun match(tokens: List<TokenOccurrence>): List<WordMatchResult> {
        val matches = mutableListOf<WordMatchResult>()
        val seen = mutableSetOf<Triple<Int, String, Long>>() // sentenceIndex, normalizedToken, wordId

        tokens.forEach { token ->
            val candidates = index[token.normalizedToken] ?: return@forEach
            candidates.forEach { wordRef ->
                val key = Triple(token.sentenceIndex, token.normalizedToken, wordRef.wordId)
                if (key !in seen) {
                    seen.add(key)
                    matches.add(
                        WordMatchResult(
                            wordId = wordRef.wordId,
                            dictionaryId = wordRef.dictionaryId,
                            sentenceIndex = token.sentenceIndex,
                            matchedToken = token.token
                        )
                    )
                }
            }
        }
        return matches
    }
}
