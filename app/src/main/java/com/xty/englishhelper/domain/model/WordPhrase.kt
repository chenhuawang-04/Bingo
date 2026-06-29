package com.xty.englishhelper.domain.model

data class WordPhraseTag(
    val id: Long = 0,
    val tagUid: String = "",
    val dictionaryId: Long,
    val name: String,
    val normalizedName: String = "",
    val description: String = "",
    val source: String = WordPhraseSource.AI.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class WordPhrase(
    val id: Long = 0,
    val phraseUid: String = "",
    val wordId: Long,
    val dictionaryId: Long,
    val phrase: String,
    val normalizedPhrase: String = "",
    val meaning: String = "",
    val example: String = "",
    val usageNote: String = "",
    val register: String? = null,
    val difficulty: String? = null,
    val confidence: Float = 0.8f,
    val source: String = WordPhraseSource.AI.name,
    val model: String? = null,
    val practiceCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val organizedAt: Long = System.currentTimeMillis()
)

data class WordPhraseWithTags(
    val phrase: WordPhrase,
    val tags: List<WordPhraseTag> = emptyList()
)

data class WritingPracticePhraseCandidate(
    val phraseId: Long,
    val wordId: Long,
    val dictionaryId: Long,
    val word: String,
    val phrase: String,
    val meaning: String = "",
    val example: String = "",
    val usageNote: String = "",
    val practiceCount: Int = 0,
    val tags: List<WordPhraseTag> = emptyList()
)

data class WritingPracticePhraseRequirement(
    val phraseId: Long,
    val phrase: String,
    val reason: String = "",
    val practiceCount: Int = 0
)

data class WritingPracticePhraseUsage(
    val requirement: WritingPracticePhraseRequirement,
    val used: Boolean
)

data class WordPhraseCandidate(
    val phrase: String,
    val meaning: String = "",
    val example: String = "",
    val usageNote: String = "",
    val register: String? = null,
    val difficulty: String? = null,
    val confidence: Float = 0.8f,
    val tags: List<WordPhraseTagCandidate> = emptyList()
)

data class WordPhraseTagCandidate(
    val name: String,
    val description: String = ""
)

data class WordPhraseOrganizeResult(
    val phrases: List<WordPhraseCandidate> = emptyList()
)

data class WordPhraseStats(
    val phraseCount: Int = 0,
    val tagCount: Int = 0,
    val organizedWordCount: Int = 0,
    val totalWordCount: Int = 0
)

data class WordPhraseSyncSnapshot(
    val tags: List<WordPhraseTag> = emptyList(),
    val phrases: List<WordPhraseSyncItem> = emptyList()
)

data class WordPhraseSyncItem(
    val phrase: WordPhrase,
    val wordUid: String,
    val tagUids: List<String> = emptyList()
)

enum class WordPhraseSource {
    AI,
    USER,
    SYSTEM
}

enum class WordPhraseOrganizeStatus {
    SUCCESS,
    EMPTY,
    FAILED
}
