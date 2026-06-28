package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseOrganizeResult
import com.xty.englishhelper.domain.model.WordPhraseOrganizeStatus
import com.xty.englishhelper.domain.model.WordPhraseStats
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordPhraseTag
import com.xty.englishhelper.domain.model.WordPhraseWithTags

interface WordPhraseRepository {
    suspend fun getPhrasesForWord(wordId: Long): List<WordPhraseWithTags>
    suspend fun getExistingTagsForPrompt(dictionaryId: Long, limit: Int = 80): List<WordPhraseTag>
    suspend fun getStats(dictionaryId: Long, totalWordCount: Int): WordPhraseStats
    suspend fun shouldSkipWord(wordId: Long): Boolean
    suspend fun saveAiResult(
        dictionaryId: Long,
        word: WordDetails,
        result: WordPhraseOrganizeResult,
        modelName: String?
    ): WordPhraseOrganizeStatus
    suspend fun markFailed(dictionaryId: Long, wordId: Long, errorMessage: String?, modelName: String?)
    suspend fun exportSnapshot(dictionaryId: Long, wordIdToUid: Map<Long, String>): WordPhraseSyncSnapshot
    suspend fun replaceSnapshot(dictionaryId: Long, snapshot: WordPhraseSyncSnapshot, wordUidToId: Map<String, Long>)
    suspend fun mergeSnapshot(dictionaryId: Long, snapshot: WordPhraseSyncSnapshot, wordUidToId: Map<String, Long>)
}
