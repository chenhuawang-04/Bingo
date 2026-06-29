package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.PhraseTagLinkProjection
import com.xty.englishhelper.data.local.dao.PhraseTagRefProjection
import com.xty.englishhelper.data.local.dao.WordPhraseDao
import com.xty.englishhelper.data.local.dao.WordPhrasePracticeProjection
import com.xty.englishhelper.data.local.entity.WordPhraseEntity
import com.xty.englishhelper.data.local.entity.WordPhraseOrganizeMarkEntity
import com.xty.englishhelper.data.local.entity.WordPhraseTagCrossRef
import com.xty.englishhelper.data.local.entity.WordPhraseTagEntity
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhrase
import com.xty.englishhelper.domain.model.WordPhraseCandidate
import com.xty.englishhelper.domain.model.WordPhraseOrganizeResult
import com.xty.englishhelper.domain.model.WordPhraseOrganizeStatus
import com.xty.englishhelper.domain.model.WordPhraseSyncItem
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordPhraseTag
import com.xty.englishhelper.domain.model.WordPhraseTagCandidate
import com.xty.englishhelper.domain.repository.TransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPhraseRepositoryImplTest {

    private val transactionRunner = object : TransactionRunner {
        override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
    }

    @Test
    fun `mergeSnapshot aliases incoming tag uid when same normalized local tag exists`() = runTest {
        val dao = FakeWordPhraseDao().apply {
            seedTag(
                WordPhraseTagEntity(
                    id = 1,
                    tagUid = "local-tag",
                    dictionaryId = 1,
                    name = "写作表达",
                    normalizedName = "写作表达"
                )
            )
        }
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        repository.mergeSnapshot(
            dictionaryId = 1,
            snapshot = WordPhraseSyncSnapshot(
                tags = listOf(
                    WordPhraseTag(
                        tagUid = "cloud-tag",
                        dictionaryId = 1,
                        name = "写作表达",
                        normalizedName = "写作表达"
                    )
                ),
                phrases = listOf(
                    WordPhraseSyncItem(
                        wordUid = "word-uid-1",
                        tagUids = listOf("cloud-tag"),
                        phrase = WordPhrase(
                            phraseUid = "phrase-uid-1",
                            wordId = 0,
                            dictionaryId = 0,
                            phrase = "take part in",
                            normalizedPhrase = "take part in"
                        )
                    )
                )
            ),
            wordUidToId = mapOf("word-uid-1" to 10L)
        )

        val savedPhrase = dao.phrases.single { it.dictionaryId == 1L && it.wordId == 10L }
        val savedTag = dao.tags.single { it.id == dao.crossRefs.single().tagId }
        assertEquals("local-tag", savedTag.tagUid)
        assertEquals("写作表达", savedTag.name)
        assertNotEquals("未分类", savedTag.name)
        assertEquals(savedPhrase.id, dao.crossRefs.single().phraseId)
    }

    @Test
    fun `mergeSnapshot keeps same phrase uid isolated by dictionary`() = runTest {
        val dao = FakeWordPhraseDao().apply {
            seedTag(
                WordPhraseTagEntity(
                    id = 1,
                    tagUid = "tag-1",
                    dictionaryId = 1,
                    name = "写作表达",
                    normalizedName = "写作表达"
                )
            )
            seedPhrase(
                WordPhraseEntity(
                    id = 20,
                    phraseUid = "same-phrase-uid",
                    wordId = 200,
                    dictionaryId = 2,
                    phrase = "dict two phrase",
                    normalizedPhrase = "dict two phrase"
                )
            )
        }
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        repository.mergeSnapshot(
            dictionaryId = 1,
            snapshot = WordPhraseSyncSnapshot(
                tags = listOf(
                    WordPhraseTag(
                        tagUid = "tag-1",
                        dictionaryId = 1,
                        name = "写作表达",
                        normalizedName = "写作表达"
                    )
                ),
                phrases = listOf(
                    WordPhraseSyncItem(
                        wordUid = "word-uid-1",
                        tagUids = listOf("tag-1"),
                        phrase = WordPhrase(
                            phraseUid = "same-phrase-uid",
                            wordId = 0,
                            dictionaryId = 0,
                            phrase = "dict one phrase",
                            normalizedPhrase = "dict one phrase"
                        )
                    )
                )
            ),
            wordUidToId = mapOf("word-uid-1" to 10L)
        )

        assertEquals(2, dao.phrases.size)
        assertEquals("dict two phrase", dao.phrases.single { it.dictionaryId == 2L }.phrase)
        assertEquals("dict one phrase", dao.phrases.single { it.dictionaryId == 1L }.phrase)
        assertTrue(dao.crossRefs.all { ref ->
            val phrase = dao.phrases.single { it.id == ref.phraseId }
            val tag = dao.tags.single { it.id == ref.tagId }
            phrase.dictionaryId == tag.dictionaryId
        })
    }

    @Test
    fun `mergeSnapshot is stable when the same snapshot is imported twice`() = runTest {
        val dao = FakeWordPhraseDao()
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)
        val snapshot = WordPhraseSyncSnapshot(
            tags = listOf(
                WordPhraseTag(
                    tagUid = "tag-writing",
                    dictionaryId = 1,
                    name = "写作表达",
                    normalizedName = "写作表达",
                    description = "作文可用表达",
                    createdAt = 100L,
                    updatedAt = 200L
                )
            ),
            phrases = listOf(
                WordPhraseSyncItem(
                    wordUid = "word-uid-1",
                    tagUids = listOf("tag-writing"),
                    phrase = WordPhrase(
                        phraseUid = "phrase-uid-1",
                        wordId = 0,
                        dictionaryId = 0,
                        phrase = "take part in",
                        normalizedPhrase = "take part in",
                        meaning = "参加",
                        example = "Students take part in the discussion.",
                        usageNote = "可用于活动参与场景。",
                        confidence = 0.92f,
                        createdAt = 300L,
                        updatedAt = 400L,
                        organizedAt = 500L
                    )
                )
            )
        )

        repository.mergeSnapshot(
            dictionaryId = 1,
            snapshot = snapshot,
            wordUidToId = mapOf("word-uid-1" to 10L)
        )
        val tagsAfterFirstMerge = dao.tags.toList()
        val phrasesAfterFirstMerge = dao.phrases.toList()
        val refsAfterFirstMerge = dao.crossRefs.toList()

        repository.mergeSnapshot(
            dictionaryId = 1,
            snapshot = snapshot,
            wordUidToId = mapOf("word-uid-1" to 10L)
        )

        assertEquals(tagsAfterFirstMerge, dao.tags)
        assertEquals(phrasesAfterFirstMerge, dao.phrases)
        assertEquals(refsAfterFirstMerge, dao.crossRefs)
    }

    @Test
    fun `mergeSnapshot preserves phrase practice count and never lowers it`() = runTest {
        val dao = FakeWordPhraseDao()
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        repository.mergeSnapshot(
            dictionaryId = 1,
            snapshot = WordPhraseSyncSnapshot(
                phrases = listOf(
                    WordPhraseSyncItem(
                        wordUid = "word-uid-1",
                        phrase = WordPhrase(
                            phraseUid = "phrase-uid-1",
                            wordId = 0,
                            dictionaryId = 0,
                            phrase = "take part in",
                            normalizedPhrase = "take part in",
                            practiceCount = 7,
                            updatedAt = 100L
                        )
                    )
                )
            ),
            wordUidToId = mapOf("word-uid-1" to 10L)
        )

        assertEquals(7, dao.phrases.single().practiceCount)

        repository.mergeSnapshot(
            dictionaryId = 1,
            snapshot = WordPhraseSyncSnapshot(
                phrases = listOf(
                    WordPhraseSyncItem(
                        wordUid = "word-uid-1",
                        phrase = WordPhrase(
                            phraseUid = "phrase-uid-1",
                            wordId = 0,
                            dictionaryId = 0,
                            phrase = "take part in",
                            normalizedPhrase = "take part in",
                            practiceCount = 2,
                            updatedAt = 200L
                        )
                    )
                )
            ),
            wordUidToId = mapOf("word-uid-1" to 10L)
        )

        assertEquals(7, dao.phrases.single().practiceCount)
    }

    @Test
    fun `incrementPracticeCounts increments distinct positive ids only`() = runTest {
        val dao = FakeWordPhraseDao().apply {
            seedPhrase(
                WordPhraseEntity(
                    id = 10,
                    phraseUid = "phrase-uid-1",
                    wordId = 100,
                    dictionaryId = 1,
                    phrase = "take part in",
                    normalizedPhrase = "take part in",
                    practiceCount = 3,
                    updatedAt = 100L
                )
            )
            seedPhrase(
                WordPhraseEntity(
                    id = 20,
                    phraseUid = "phrase-uid-2",
                    wordId = 200,
                    dictionaryId = 1,
                    phrase = "play a role in",
                    normalizedPhrase = "play a role in",
                    practiceCount = 5,
                    updatedAt = 100L
                )
            )
        }
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        repository.incrementPracticeCounts(listOf(10L, 10L, 0L, -1L))

        assertEquals(4, dao.phrases.single { it.id == 10L }.practiceCount)
        assertEquals(5, dao.phrases.single { it.id == 20L }.practiceCount)
        assertTrue(dao.phrases.single { it.id == 10L }.updatedAt > 100L)
    }

    @Test
    fun `getWritingPracticeCandidates maps practice count and tags`() = runTest {
        val dao = FakeWordPhraseDao().apply {
            seedTag(
                WordPhraseTagEntity(
                    id = 1,
                    tagUid = "tag-writing",
                    dictionaryId = 1,
                    name = "写作表达",
                    normalizedName = "写作表达"
                )
            )
            seedPhrase(
                WordPhraseEntity(
                    id = 10,
                    phraseUid = "phrase-uid-1",
                    wordId = 100,
                    dictionaryId = 1,
                    phrase = "take part in",
                    normalizedPhrase = "take part in",
                    meaning = "参加",
                    practiceCount = 4
                )
            )
            crossRefs += WordPhraseTagCrossRef(phraseId = 10, tagId = 1)
        }
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        val candidates = repository.getWritingPracticeCandidates(limit = 10, offset = 0)

        assertEquals(1, candidates.size)
        assertEquals(10L, candidates.single().phraseId)
        assertEquals("word-100", candidates.single().word)
        assertEquals(4, candidates.single().practiceCount)
        assertEquals(listOf("tag-writing"), candidates.single().tags.map { it.tagUid })
    }

    @Test
    fun `saveAiResult marks explicit empty result as empty and skips later`() = runTest {
        val dao = FakeWordPhraseDao()
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        val status = repository.saveAiResult(
            dictionaryId = 1,
            word = WordDetails(id = 10, dictionaryId = 1, spelling = "apple"),
            result = WordPhraseOrganizeResult(phrases = emptyList()),
            modelName = "test-model"
        )

        assertEquals(WordPhraseOrganizeStatus.EMPTY, status)
        assertEquals("EMPTY", dao.markForWord(10)?.status)
        assertTrue(repository.shouldSkipWord(10))
    }

    @Test
    fun `saveAiResult marks non-empty all-invalid result as failed and retryable`() = runTest {
        val dao = FakeWordPhraseDao()
        val repository = WordPhraseRepositoryImpl(dao, transactionRunner)

        val status = repository.saveAiResult(
            dictionaryId = 1,
            word = WordDetails(id = 10, dictionaryId = 1, spelling = "apple"),
            result = WordPhraseOrganizeResult(
                phrases = listOf(
                    WordPhraseCandidate(
                        phrase = "12345",
                        tags = listOf(WordPhraseTagCandidate("写作表达"))
                    )
                )
            ),
            modelName = "test-model"
        )

        assertEquals(WordPhraseOrganizeStatus.FAILED, status)
        assertEquals("FAILED", dao.markForWord(10)?.status)
        assertEquals(0, dao.phrases.size)
        assertTrue(!repository.shouldSkipWord(10))
    }

    private class FakeWordPhraseDao : WordPhraseDao {
        val tags = mutableListOf<WordPhraseTagEntity>()
        val phrases = mutableListOf<WordPhraseEntity>()
        val crossRefs = mutableListOf<WordPhraseTagCrossRef>()
        private val marks = mutableListOf<WordPhraseOrganizeMarkEntity>()
        private var nextTagId = 1L
        private var nextPhraseId = 1L

        fun seedTag(tag: WordPhraseTagEntity) {
            tags += tag
            nextTagId = maxOf(nextTagId, tag.id + 1)
        }

        fun seedPhrase(phrase: WordPhraseEntity) {
            phrases += phrase
            nextPhraseId = maxOf(nextPhraseId, phrase.id + 1)
        }

        fun markForWord(wordId: Long): WordPhraseOrganizeMarkEntity? =
            marks.firstOrNull { it.wordId == wordId }

        override suspend fun getTagsByDictionary(dictionaryId: Long): List<WordPhraseTagEntity> =
            tags.filter { it.dictionaryId == dictionaryId }.sortedBy { it.name }

        override suspend fun getTagByNormalizedName(dictionaryId: Long, normalizedName: String): WordPhraseTagEntity? =
            tags.firstOrNull { it.dictionaryId == dictionaryId && it.normalizedName == normalizedName }

        override suspend fun getTagByUid(dictionaryId: Long, tagUid: String): WordPhraseTagEntity? =
            tags.firstOrNull { it.dictionaryId == dictionaryId && it.tagUid == tagUid }

        override suspend fun insertTag(tag: WordPhraseTagEntity): Long {
            if (tags.any { it.dictionaryId == tag.dictionaryId && it.tagUid == tag.tagUid }) return -1L
            if (tags.any { it.dictionaryId == tag.dictionaryId && it.normalizedName == tag.normalizedName }) return -1L
            val id = nextTagId++
            tags += tag.copy(id = id)
            return id
        }

        override suspend fun updateTag(tag: WordPhraseTagEntity) {
            tags.replaceFirst({ it.id == tag.id }, tag)
        }

        override suspend fun getPhrasesByWord(wordId: Long): List<WordPhraseEntity> =
            phrases.filter { it.wordId == wordId }.sortedBy { it.phrase }

        override suspend fun getPhrasesByDictionary(dictionaryId: Long): List<WordPhraseEntity> =
            phrases.filter { it.dictionaryId == dictionaryId }.sortedWith(compareBy({ it.wordId }, { it.phrase }))

        override suspend fun getWritingPracticeCandidates(limit: Int, offset: Int): List<WordPhrasePracticeProjection> =
            phrases
                .sortedWith(compareBy({ it.dictionaryId }, { it.wordId }, { it.practiceCount }, { it.phrase }))
                .drop(offset)
                .take(limit)
                .map { phrase ->
                    WordPhrasePracticeProjection(
                        phrase = phrase,
                        wordSpelling = "word-${phrase.wordId}"
                    )
                }

        override suspend fun getPhraseByWordAndNormalized(wordId: Long, normalizedPhrase: String): WordPhraseEntity? =
            phrases.firstOrNull { it.wordId == wordId && it.normalizedPhrase == normalizedPhrase }

        override suspend fun getPhraseByUid(dictionaryId: Long, phraseUid: String): WordPhraseEntity? =
            phrases.firstOrNull { it.dictionaryId == dictionaryId && it.phraseUid == phraseUid }

        override suspend fun insertPhrase(phrase: WordPhraseEntity): Long {
            if (phrases.any { it.dictionaryId == phrase.dictionaryId && it.phraseUid == phrase.phraseUid }) return -1L
            if (phrases.any { it.wordId == phrase.wordId && it.normalizedPhrase == phrase.normalizedPhrase }) return -1L
            val id = nextPhraseId++
            phrases += phrase.copy(id = id)
            return id
        }

        override suspend fun updatePhrase(phrase: WordPhraseEntity) {
            phrases.replaceFirst({ it.id == phrase.id }, phrase)
        }

        override suspend fun incrementPracticeCounts(phraseIds: List<Long>, updatedAt: Long) {
            val idSet = phraseIds.toSet()
            phrases.replaceAll { phrase ->
                if (phrase.id in idSet) {
                    phrase.copy(
                        practiceCount = phrase.practiceCount + 1,
                        updatedAt = updatedAt
                    )
                } else {
                    phrase
                }
            }
        }

        override suspend fun insertPhraseTagCrossRef(ref: WordPhraseTagCrossRef) {
            if (crossRefs.none { it.phraseId == ref.phraseId && it.tagId == ref.tagId }) {
                crossRefs += ref
            }
        }

        override suspend fun getTagsForPhraseIds(phraseIds: List<Long>): List<PhraseTagLinkProjection> =
            crossRefs.filter { it.phraseId in phraseIds }.mapNotNull { ref ->
                val tag = tags.firstOrNull { it.id == ref.tagId } ?: return@mapNotNull null
                PhraseTagLinkProjection(
                    phraseId = ref.phraseId,
                    id = tag.id,
                    tagUid = tag.tagUid,
                    dictionaryId = tag.dictionaryId,
                    name = tag.name,
                    normalizedName = tag.normalizedName,
                    description = tag.description,
                    source = tag.source,
                    createdAt = tag.createdAt,
                    updatedAt = tag.updatedAt
                )
            }

        override suspend fun getCrossRefsByDictionary(dictionaryId: Long): List<PhraseTagRefProjection> =
            crossRefs.mapNotNull { ref ->
                val phrase = phrases.firstOrNull { it.id == ref.phraseId } ?: return@mapNotNull null
                if (phrase.dictionaryId != dictionaryId) return@mapNotNull null
                PhraseTagRefProjection(ref.phraseId, ref.tagId)
            }

        override suspend fun countPhrasesForWord(wordId: Long): Int =
            phrases.count { it.wordId == wordId }

        override suspend fun countPhrasesByDictionary(dictionaryId: Long): Int =
            phrases.count { it.dictionaryId == dictionaryId }

        override suspend fun countTagsByDictionary(dictionaryId: Long): Int =
            tags.count { it.dictionaryId == dictionaryId }

        override suspend fun countOrganizedWords(dictionaryId: Long): Int {
            val marked = marks
                .filter { it.dictionaryId == dictionaryId && it.status in setOf("SUCCESS", "EMPTY") }
                .map { it.wordId }
            val withPhrases = phrases
                .filter { it.dictionaryId == dictionaryId }
                .map { it.wordId }
            return (marked + withPhrases).toSet().size
        }

        override suspend fun hasFinishedMark(wordId: Long): Int =
            marks.count { it.wordId == wordId && it.status in setOf("SUCCESS", "EMPTY") }

        override suspend fun upsertOrganizeMark(mark: WordPhraseOrganizeMarkEntity) {
            marks.removeAll { it.wordId == mark.wordId }
            marks += mark
        }

        override suspend fun deleteCrossRefsByDictionary(dictionaryId: Long) {
            val phraseIds = phrases.filter { it.dictionaryId == dictionaryId }.map { it.id }.toSet()
            crossRefs.removeAll { it.phraseId in phraseIds }
        }

        override suspend fun deletePhrasesByDictionary(dictionaryId: Long) {
            phrases.removeAll { it.dictionaryId == dictionaryId }
        }

        override suspend fun deleteTagsByDictionary(dictionaryId: Long) {
            tags.removeAll { it.dictionaryId == dictionaryId }
        }

        override suspend fun deleteMarksByDictionary(dictionaryId: Long) {
            marks.removeAll { it.dictionaryId == dictionaryId }
        }

        private fun <T> MutableList<T>.replaceFirst(predicate: (T) -> Boolean, value: T) {
            val index = indexOfFirst(predicate)
            if (index >= 0) this[index] = value
        }
    }
}
