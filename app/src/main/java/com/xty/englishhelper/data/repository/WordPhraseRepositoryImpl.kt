package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.PhraseTagLinkProjection
import com.xty.englishhelper.data.local.dao.WordPhraseDao
import com.xty.englishhelper.data.local.entity.WordPhraseEntity
import com.xty.englishhelper.data.local.entity.WordPhraseOrganizeMarkEntity
import com.xty.englishhelper.data.local.entity.WordPhraseTagCrossRef
import com.xty.englishhelper.data.local.entity.WordPhraseTagEntity
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhrase
import com.xty.englishhelper.domain.model.WordPhraseCandidate
import com.xty.englishhelper.domain.model.WordPhraseOrganizeResult
import com.xty.englishhelper.domain.model.WordPhraseOrganizeStatus
import com.xty.englishhelper.domain.model.WordPhraseSource
import com.xty.englishhelper.domain.model.WordPhraseStats
import com.xty.englishhelper.domain.model.WordPhraseSyncItem
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordPhraseTag
import com.xty.englishhelper.domain.model.WordPhraseTagCandidate
import com.xty.englishhelper.domain.model.WordPhraseWithTags
import com.xty.englishhelper.domain.model.WritingPracticePhraseCandidate
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.WordPhraseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordPhraseRepositoryImpl @Inject constructor(
    private val dao: WordPhraseDao,
    private val transactionRunner: TransactionRunner
) : WordPhraseRepository {

    override suspend fun getPhrasesForWord(wordId: Long): List<WordPhraseWithTags> {
        val phrases = dao.getPhrasesByWord(wordId)
        if (phrases.isEmpty()) return emptyList()
        val tagsByPhraseId = dao.getTagsForPhraseIds(phrases.map { it.id })
            .groupBy { it.phraseId }
            .mapValues { (_, links) -> links.map { it.toDomainTag() } }
        return phrases.map { phrase ->
            WordPhraseWithTags(
                phrase = phrase.toDomain(),
                tags = tagsByPhraseId[phrase.id].orEmpty()
            )
        }
    }

    override suspend fun getWritingPracticeCandidates(limit: Int, offset: Int): List<WritingPracticePhraseCandidate> {
        if (limit <= 0 || offset < 0) return emptyList()
        val projections = dao.getWritingPracticeCandidates(limit, offset)
        if (projections.isEmpty()) return emptyList()
        val tagsByPhraseId = dao.getTagsForPhraseIds(projections.map { it.phrase.id })
            .groupBy { it.phraseId }
            .mapValues { (_, links) -> links.map { it.toDomainTag() } }
        return projections.map { item ->
            val phrase = item.phrase
            WritingPracticePhraseCandidate(
                phraseId = phrase.id,
                wordId = phrase.wordId,
                dictionaryId = phrase.dictionaryId,
                word = item.wordSpelling,
                phrase = phrase.phrase,
                meaning = phrase.meaning,
                example = phrase.example,
                usageNote = phrase.usageNote,
                practiceCount = phrase.practiceCount,
                tags = tagsByPhraseId[phrase.id].orEmpty()
            )
        }
    }

    override suspend fun incrementPracticeCounts(phraseIds: List<Long>) {
        val ids = phraseIds.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return
        dao.incrementPracticeCounts(ids, System.currentTimeMillis())
    }

    override suspend fun getExistingTagsForPrompt(dictionaryId: Long, limit: Int): List<WordPhraseTag> {
        if (limit <= 0) return emptyList()
        return dao.getTagsByDictionary(dictionaryId)
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun getStats(dictionaryId: Long, totalWordCount: Int): WordPhraseStats {
        return WordPhraseStats(
            phraseCount = dao.countPhrasesByDictionary(dictionaryId),
            tagCount = dao.countTagsByDictionary(dictionaryId),
            organizedWordCount = dao.countOrganizedWords(dictionaryId),
            totalWordCount = totalWordCount
        )
    }

    override suspend fun shouldSkipWord(wordId: Long): Boolean {
        return dao.hasFinishedMark(wordId) > 0 || dao.countPhrasesForWord(wordId) > 0
    }

    override suspend fun saveAiResult(
        dictionaryId: Long,
        word: WordDetails,
        result: WordPhraseOrganizeResult,
        modelName: String?
    ): WordPhraseOrganizeStatus {
        return transactionRunner.runInTransaction {
            val savedCount = upsertAiPhrases(
                dictionaryId = dictionaryId,
                wordId = word.id,
                candidates = result.phrases,
                modelName = modelName
            )
            val status = when {
                savedCount > 0 -> WordPhraseOrganizeStatus.SUCCESS
                result.phrases.isEmpty() -> WordPhraseOrganizeStatus.EMPTY
                else -> WordPhraseOrganizeStatus.FAILED
            }
            mark(
                dictionaryId = dictionaryId,
                wordId = word.id,
                status = status,
                phraseCount = savedCount,
                errorMessage = if (status == WordPhraseOrganizeStatus.FAILED) {
                    "AI 返回的词组/短语全部无效"
                } else {
                    null
                },
                modelName = modelName
            )
            status
        }
    }

    override suspend fun markFailed(dictionaryId: Long, wordId: Long, errorMessage: String?, modelName: String?) {
        mark(
            dictionaryId = dictionaryId,
            wordId = wordId,
            status = WordPhraseOrganizeStatus.FAILED,
            phraseCount = 0,
            errorMessage = errorMessage?.take(500),
            modelName = modelName
        )
    }

    override suspend fun exportSnapshot(
        dictionaryId: Long,
        wordIdToUid: Map<Long, String>
    ): WordPhraseSyncSnapshot {
        val tags = dao.getTagsByDictionary(dictionaryId).map { it.toDomain() }
        val phrases = dao.getPhrasesByDictionary(dictionaryId)
        if (phrases.isEmpty()) return WordPhraseSyncSnapshot(tags = tags)

        val tagById = tags.associateBy { it.id }
        val refsByPhraseId = dao.getCrossRefsByDictionary(dictionaryId).groupBy { it.phraseId }
        val items = phrases.mapNotNull { phrase ->
            val wordUid = wordIdToUid[phrase.wordId]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tagUids = refsByPhraseId[phrase.id]
                .orEmpty()
                .mapNotNull { ref -> tagById[ref.tagId]?.tagUid?.takeIf(String::isNotBlank) }
                .distinct()
            WordPhraseSyncItem(
                phrase = phrase.toDomain(),
                wordUid = wordUid,
                tagUids = tagUids
            )
        }
        return WordPhraseSyncSnapshot(tags = tags, phrases = items)
    }

    override suspend fun replaceSnapshot(
        dictionaryId: Long,
        snapshot: WordPhraseSyncSnapshot,
        wordUidToId: Map<String, Long>
    ) {
        transactionRunner.runInTransaction {
            dao.deleteCrossRefsByDictionary(dictionaryId)
            dao.deletePhrasesByDictionary(dictionaryId)
            dao.deleteTagsByDictionary(dictionaryId)
            dao.deleteMarksByDictionary(dictionaryId)
            importSnapshotInternal(dictionaryId, snapshot, wordUidToId)
        }
    }

    override suspend fun mergeSnapshot(
        dictionaryId: Long,
        snapshot: WordPhraseSyncSnapshot,
        wordUidToId: Map<String, Long>
    ) {
        transactionRunner.runInTransaction {
            importSnapshotInternal(dictionaryId, snapshot, wordUidToId)
        }
    }

    private suspend fun importSnapshotInternal(
        dictionaryId: Long,
        snapshot: WordPhraseSyncSnapshot,
        wordUidToId: Map<String, Long>
    ) {
        val importedTagsByUid = mutableMapOf<String, WordPhraseTagEntity>()
        snapshot.tags.forEach { tag ->
            val incomingTagUid = tag.tagUid.ifBlank { UUID.randomUUID().toString() }
            val entity = upsertTag(
                dictionaryId = dictionaryId,
                candidate = WordPhraseTagCandidate(tag.name, tag.description),
                source = tag.source.ifBlank { WordPhraseSource.AI.name },
                tagUid = incomingTagUid,
                createdAt = tag.createdAt,
                updatedAt = tag.updatedAt
            )
            importedTagsByUid[incomingTagUid] = entity
            importedTagsByUid[entity.tagUid] = entity
        }

        snapshot.phrases.forEach { item ->
            val wordId = wordUidToId[item.wordUid] ?: return@forEach
            val phrase = item.phrase
            val tagEntities = item.tagUids.mapNotNull { importedTagsByUid[it] }.ifEmpty {
                listOf(ensureUnclassifiedTag(dictionaryId))
            }
            val phraseEntity = upsertPhrase(
                dictionaryId = dictionaryId,
                wordId = wordId,
                candidate = WordPhraseCandidate(
                    phrase = phrase.phrase,
                    meaning = phrase.meaning,
                    example = phrase.example,
                    usageNote = phrase.usageNote,
                    register = phrase.register,
                    difficulty = phrase.difficulty,
                    confidence = phrase.confidence,
                    tags = emptyList()
                ),
                source = phrase.source.ifBlank { WordPhraseSource.AI.name },
                phraseUid = phrase.phraseUid.ifBlank { UUID.randomUUID().toString() },
                modelName = phrase.model,
                practiceCount = phrase.practiceCount,
                createdAt = phrase.createdAt,
                updatedAt = phrase.updatedAt,
                organizedAt = phrase.organizedAt
            )
            tagEntities.forEach { tag ->
                dao.insertPhraseTagCrossRef(
                    WordPhraseTagCrossRef(
                        phraseId = phraseEntity.id,
                        tagId = tag.id,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun upsertAiPhrases(
        dictionaryId: Long,
        wordId: Long,
        candidates: List<WordPhraseCandidate>,
        modelName: String?
    ): Int {
        var saved = 0
        candidates
            .mapNotNull(::sanitizeCandidate)
            .distinctBy { normalizePhrase(it.phrase) }
            .forEach { candidate ->
                val tagEntities = candidate.tags
                    .mapNotNull(::sanitizeTagCandidate)
                    .ifEmpty { listOf(WordPhraseTagCandidate("未分类", "未分类短语")) }
                    .distinctBy { normalizeName(it.name) }
                    .map { tag -> upsertTag(dictionaryId, tag, WordPhraseSource.AI.name) }

                val phrase = upsertPhrase(
                    dictionaryId = dictionaryId,
                    wordId = wordId,
                    candidate = candidate,
                    source = WordPhraseSource.AI.name,
                    modelName = modelName
                )
                tagEntities.forEach { tag ->
                    dao.insertPhraseTagCrossRef(
                        WordPhraseTagCrossRef(
                            phraseId = phrase.id,
                            tagId = tag.id,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                saved += 1
            }
        return saved
    }

    private suspend fun upsertTag(
        dictionaryId: Long,
        candidate: WordPhraseTagCandidate,
        source: String,
        tagUid: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): WordPhraseTagEntity {
        val normalized = normalizeName(candidate.name)
        val byUid = tagUid.takeIf { it.isNotBlank() }?.let { dao.getTagByUid(dictionaryId, it) }
        val existing = byUid ?: dao.getTagByNormalizedName(dictionaryId, normalized)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val incomingUpdatedAt = updatedAt.takeIf { it > 0 }
            val nextTagUid = existing.tagUid.ifBlank { tagUid }
            val nextName = if (candidate.name.isNotBlank() && updatedAt >= existing.updatedAt) {
                candidate.name.trim()
            } else {
                existing.name
            }
            val nextDescription = when {
                existing.description.isBlank() -> candidate.description.trim()
                updatedAt > existing.updatedAt && candidate.description.isNotBlank() -> candidate.description.trim()
                else -> existing.description
            }
            val nextSource = existing.source.ifBlank { source }
            val contentChanged = nextTagUid != existing.tagUid ||
                nextName != existing.name ||
                nextDescription != existing.description ||
                nextSource != existing.source
            val nextUpdatedAt = when {
                contentChanged -> maxOf(existing.updatedAt, incomingUpdatedAt ?: now)
                incomingUpdatedAt != null && incomingUpdatedAt > existing.updatedAt -> incomingUpdatedAt
                else -> existing.updatedAt
            }
            val next = existing.copy(
                tagUid = nextTagUid,
                name = nextName,
                description = nextDescription,
                source = nextSource,
                updatedAt = nextUpdatedAt
            )
            if (next != existing) dao.updateTag(next)
            return next
        }

        val entity = WordPhraseTagEntity(
            tagUid = tagUid.ifBlank { UUID.randomUUID().toString() },
            dictionaryId = dictionaryId,
            name = candidate.name.trim(),
            normalizedName = normalized,
            description = candidate.description.trim(),
            source = source,
            createdAt = createdAt.takeIf { it > 0 } ?: now,
            updatedAt = updatedAt.takeIf { it > 0 } ?: now
        )
        val id = dao.insertTag(entity)
        return if (id > 0) entity.copy(id = id) else dao.getTagByNormalizedName(dictionaryId, normalized)
            ?: throw IllegalStateException("Failed to upsert phrase tag")
    }

    private suspend fun upsertPhrase(
        dictionaryId: Long,
        wordId: Long,
        candidate: WordPhraseCandidate,
        source: String,
        modelName: String?,
        practiceCount: Int = 0,
        phraseUid: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
        organizedAt: Long = System.currentTimeMillis()
    ): WordPhraseEntity {
        val normalized = normalizePhrase(candidate.phrase)
        val byUid = phraseUid.takeIf { it.isNotBlank() }?.let { dao.getPhraseByUid(dictionaryId, it) }
        val existing = byUid ?: dao.getPhraseByWordAndNormalized(wordId, normalized)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val incomingUpdatedAt = updatedAt.takeIf { it > 0 }
            val incomingOrganizedAt = organizedAt.takeIf { it > 0 }
            val nextPhraseUid = existing.phraseUid.ifBlank { phraseUid }
            val nextPhrase = if (candidate.phrase.isNotBlank() && updatedAt >= existing.updatedAt) {
                candidate.phrase.trim()
            } else {
                existing.phrase
            }
            val nextMeaning = mergeText(existing.meaning, candidate.meaning, updatedAt, existing.updatedAt)
            val nextExample = mergeText(existing.example, candidate.example, updatedAt, existing.updatedAt)
            val nextUsageNote = mergeText(existing.usageNote, candidate.usageNote, updatedAt, existing.updatedAt)
            val nextRegister = existing.register ?: candidate.register
            val nextDifficulty = existing.difficulty ?: candidate.difficulty
            val nextConfidence = maxOf(existing.confidence, candidate.confidence.coerceIn(0f, 1f))
            val nextSource = existing.source.ifBlank { source }
            val nextModel = modelName ?: existing.model
            val nextPracticeCount = maxOf(existing.practiceCount, practiceCount.coerceAtLeast(0))
            val contentChanged = nextPhraseUid != existing.phraseUid ||
                nextPhrase != existing.phrase ||
                nextMeaning != existing.meaning ||
                nextExample != existing.example ||
                nextUsageNote != existing.usageNote ||
                nextRegister != existing.register ||
                nextDifficulty != existing.difficulty ||
                nextConfidence != existing.confidence ||
                nextSource != existing.source ||
                nextModel != existing.model ||
                nextPracticeCount != existing.practiceCount
            val nextUpdatedAt = when {
                contentChanged -> maxOf(existing.updatedAt, incomingUpdatedAt ?: now)
                incomingUpdatedAt != null && incomingUpdatedAt > existing.updatedAt -> incomingUpdatedAt
                else -> existing.updatedAt
            }
            val nextOrganizedAt = when {
                contentChanged -> maxOf(existing.organizedAt, incomingOrganizedAt ?: now)
                incomingOrganizedAt != null && incomingOrganizedAt > existing.organizedAt -> incomingOrganizedAt
                else -> existing.organizedAt
            }
            val next = existing.copy(
                phraseUid = nextPhraseUid,
                phrase = nextPhrase,
                meaning = nextMeaning,
                example = nextExample,
                usageNote = nextUsageNote,
                register = nextRegister,
                difficulty = nextDifficulty,
                confidence = nextConfidence,
                source = nextSource,
                model = nextModel,
                practiceCount = nextPracticeCount,
                updatedAt = nextUpdatedAt,
                organizedAt = nextOrganizedAt
            )
            if (next != existing) dao.updatePhrase(next)
            return next
        }

        val entity = WordPhraseEntity(
            phraseUid = phraseUid.ifBlank { UUID.randomUUID().toString() },
            wordId = wordId,
            dictionaryId = dictionaryId,
            phrase = candidate.phrase.trim(),
            normalizedPhrase = normalized,
            meaning = candidate.meaning.trim(),
            example = candidate.example.trim(),
            usageNote = candidate.usageNote.trim(),
            register = candidate.register?.trim()?.takeIf { it.isNotBlank() },
            difficulty = candidate.difficulty?.trim()?.takeIf { it.isNotBlank() },
            confidence = candidate.confidence.coerceIn(0f, 1f),
            source = source,
            model = modelName,
            practiceCount = practiceCount.coerceAtLeast(0),
            createdAt = createdAt.takeIf { it > 0 } ?: now,
            updatedAt = updatedAt.takeIf { it > 0 } ?: now,
            organizedAt = organizedAt.takeIf { it > 0 } ?: now
        )
        val id = dao.insertPhrase(entity)
        return if (id > 0) entity.copy(id = id) else dao.getPhraseByWordAndNormalized(wordId, normalized)
            ?: throw IllegalStateException("Failed to upsert phrase")
    }

    private suspend fun ensureUnclassifiedTag(dictionaryId: Long): WordPhraseTagEntity {
        return upsertTag(
            dictionaryId = dictionaryId,
            candidate = WordPhraseTagCandidate("未分类", "未分类短语"),
            source = WordPhraseSource.SYSTEM.name
        )
    }

    private suspend fun mark(
        dictionaryId: Long,
        wordId: Long,
        status: WordPhraseOrganizeStatus,
        phraseCount: Int,
        errorMessage: String?,
        modelName: String?
    ) {
        dao.upsertOrganizeMark(
            WordPhraseOrganizeMarkEntity(
                wordId = wordId,
                dictionaryId = dictionaryId,
                status = status.name,
                phraseCount = phraseCount,
                errorMessage = errorMessage,
                model = modelName,
                organizedAt = System.currentTimeMillis()
            )
        )
    }

    private fun sanitizeCandidate(candidate: WordPhraseCandidate): WordPhraseCandidate? {
        val phrase = candidate.phrase.replace(Regex("\\s+"), " ").trim().trim('.', ',', ';', ':')
        if (phrase.isBlank()) return null
        if (!phrase.any { it.isLetter() }) return null
        if (phrase.length > 120) return null
        return candidate.copy(
            phrase = phrase,
            meaning = candidate.meaning.trim().take(240),
            example = candidate.example.trim().take(320),
            usageNote = candidate.usageNote.trim().take(320),
            confidence = candidate.confidence.coerceIn(0f, 1f)
        )
    }

    private fun sanitizeTagCandidate(candidate: WordPhraseTagCandidate): WordPhraseTagCandidate? {
        val name = candidate.name.replace(Regex("\\s+"), " ").trim()
        if (name.isBlank()) return null
        if (name.length > 32) return null
        return candidate.copy(
            name = name,
            description = candidate.description.trim().take(160)
        )
    }

    private fun mergeText(existing: String, incoming: String, incomingUpdatedAt: Long, existingUpdatedAt: Long): String {
        val next = incoming.trim()
        return when {
            existing.isBlank() -> next
            next.isBlank() -> existing
            incomingUpdatedAt > existingUpdatedAt -> next
            else -> existing
        }
    }

    private fun normalizeName(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().lowercase()

    private fun normalizePhrase(raw: String): String =
        raw.replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ',', ';', ':')
            .lowercase()

    private fun WordPhraseTagEntity.toDomain(): WordPhraseTag = WordPhraseTag(
        id = id,
        tagUid = tagUid,
        dictionaryId = dictionaryId,
        name = name,
        normalizedName = normalizedName,
        description = description,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PhraseTagLinkProjection.toDomainTag(): WordPhraseTag = WordPhraseTag(
        id = id,
        tagUid = tagUid,
        dictionaryId = dictionaryId,
        name = name,
        normalizedName = normalizedName,
        description = description,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun WordPhraseEntity.toDomain(): WordPhrase = WordPhrase(
        id = id,
        phraseUid = phraseUid,
        wordId = wordId,
        dictionaryId = dictionaryId,
        phrase = phrase,
        normalizedPhrase = normalizedPhrase,
        meaning = meaning,
        example = example,
        usageNote = usageNote,
        register = register,
        difficulty = difficulty,
        confidence = confidence,
        source = source,
        model = model,
        practiceCount = practiceCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        organizedAt = organizedAt
    )
}
