package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.WordAssociationEntity
import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.data.mapper.commonSegmentsToJson
import com.xty.englishhelper.data.mapper.parseCommonSegments
import com.xty.englishhelper.data.mapper.parseDecomposition
import com.xty.englishhelper.data.mapper.toCognateEntities
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.mapper.toEntity
import com.xty.englishhelper.data.mapper.toSimilarWordEntities
import com.xty.englishhelper.data.mapper.toSynonymEntities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class WordRepositoryImpl @Inject constructor(
    private val wordDao: WordDao
) : WordRepository {

    override fun getWordsByDictionary(dictionaryId: Long): Flow<List<WordDetails>> =
        wordDao.getWordsByDictionary(dictionaryId).map { list -> list.map { it.toDomain() } }

    override fun searchWords(dictionaryId: Long, query: String): Flow<List<WordDetails>> =
        wordDao.searchWords(dictionaryId, query).map { list -> list.map { it.toDomain() } }

    override suspend fun getWordById(wordId: Long): WordDetails? =
        wordDao.getWordById(wordId)?.toDomain()

    override suspend fun insertWord(word: WordDetails): Long {
        val wordId = wordDao.insertWord(word.toEntity())
        saveRelatedEntities(word, wordId)
        return wordId
    }

    override suspend fun updateWord(word: WordDetails) {
        wordDao.updateWord(word.toEntity())
        // Clear and re-insert related entities
        wordDao.deleteSynonymsByWordId(word.id)
        wordDao.deleteSimilarWordsByWordId(word.id)
        wordDao.deleteCognatesByWordId(word.id)
        saveRelatedEntities(word, word.id)
    }

    override suspend fun deleteWord(wordId: Long) =
        wordDao.deleteWord(wordId)

    override suspend fun findByNormalizedSpelling(dictionaryId: Long, normalizedSpelling: String): WordDetails? =
        wordDao.findByNormalizedSpelling(dictionaryId, normalizedSpelling)?.toDomain()

    override suspend fun findExistingWordIds(dictionaryId: Long, normalizedSpellings: List<String>): Map<String, Long> {
        if (normalizedSpellings.isEmpty()) return emptyMap()
        // Room IN query has a limit of 999 parameters; chunk if needed
        return normalizedSpellings.chunked(900).flatMap { chunk ->
            wordDao.findExistingWordIds(dictionaryId, chunk)
        }.associate { it.normalizedSpelling to it.id }
    }

    override suspend fun getAssociatedWords(wordId: Long): List<AssociatedWordInfo> {
        return wordDao.getAssociationsForWord(wordId).map {
            AssociatedWordInfo(
                wordId = it.wordId,
                spelling = it.spelling,
                similarity = it.similarity,
                commonSegments = parseCommonSegments(it.commonSegmentsJson)
            )
        }
    }

    override suspend fun recomputeAssociations(wordId: Long, dictionaryId: Long) {
        val word = wordDao.getWordById(wordId) ?: return
        val decomposition = parseDecomposition(word.word.decompositionJson)
        val thisSegments = decomposition.map { it.segment.lowercase() }.toSet()

        if (thisSegments.isEmpty()) {
            wordDao.deleteAssociationsForWord(wordId)
            return
        }

        val thisRootSegments = decomposition
            .filter { it.role == MorphemeRole.ROOT }
            .map { it.segment.lowercase() }
            .toSet()

        val candidates = wordDao.getAllDecompositionsInDictionary(dictionaryId, wordId)
        val associations = mutableListOf<WordAssociationEntity>()

        for (candidate in candidates) {
            val otherParts = parseDecomposition(candidate.decompositionJson)
            val otherSegments = otherParts.map { it.segment.lowercase() }.toSet()
            if (otherSegments.isEmpty()) continue

            val commonAll = thisSegments.intersect(otherSegments)
            if (commonAll.isEmpty()) continue

            val union = thisSegments.union(otherSegments)
            val jaccard = commonAll.size.toFloat() / union.size.toFloat()

            val otherRootSegments = otherParts
                .filter { it.role == MorphemeRole.ROOT }
                .map { it.segment.lowercase() }
                .toSet()
            val commonRoots = thisRootSegments.intersect(otherRootSegments)
            val rootBonus = 0.15f * commonRoots.size
            val similarity = min(jaccard + rootBonus, 1.0f)

            if (similarity >= 0.25f) {
                val commonJson = commonSegmentsToJson(commonAll.toList())
                associations.add(
                    WordAssociationEntity(wordId, candidate.id, similarity, commonJson)
                )
                associations.add(
                    WordAssociationEntity(candidate.id, wordId, similarity, commonJson)
                )
            }
        }

        wordDao.deleteAssociationsForWord(wordId)
        if (associations.isNotEmpty()) {
            associations.chunked(500).forEach { chunk ->
                wordDao.insertAssociations(chunk)
            }
        }
    }

    override suspend fun recomputeAllAssociationsForDictionary(dictionaryId: Long) {
        // Use a dummy excludeWordId of -1 to get all words
        val allWords = wordDao.getAllDecompositionsInDictionary(dictionaryId, -1)
        wordDao.deleteAllAssociationsInDictionary(dictionaryId)

        if (allWords.size < 2) return

        data class ParsedWord(val id: Long, val segments: Set<String>, val rootSegments: Set<String>)

        val parsed = allWords.mapNotNull { w ->
            val parts = parseDecomposition(w.decompositionJson)
            val segments = parts.map { it.segment.lowercase() }.toSet()
            if (segments.isEmpty()) return@mapNotNull null
            val rootSegments = parts.filter { it.role == MorphemeRole.ROOT }.map { it.segment.lowercase() }.toSet()
            ParsedWord(w.id, segments, rootSegments)
        }

        val associations = mutableListOf<WordAssociationEntity>()

        for (i in parsed.indices) {
            for (j in i + 1 until parsed.size) {
                val a = parsed[i]
                val b = parsed[j]

                val commonAll = a.segments.intersect(b.segments)
                if (commonAll.isEmpty()) continue

                val union = a.segments.union(b.segments)
                val jaccard = commonAll.size.toFloat() / union.size.toFloat()

                val commonRoots = a.rootSegments.intersect(b.rootSegments)
                val rootBonus = 0.15f * commonRoots.size
                val similarity = min(jaccard + rootBonus, 1.0f)

                if (similarity >= 0.25f) {
                    val commonJson = commonSegmentsToJson(commonAll.toList())
                    associations.add(WordAssociationEntity(a.id, b.id, similarity, commonJson))
                    associations.add(WordAssociationEntity(b.id, a.id, similarity, commonJson))
                }
            }

            // Batch insert periodically to avoid excessive memory
            if (associations.size >= 1000) {
                wordDao.insertAssociations(associations.toList())
                associations.clear()
            }
        }

        if (associations.isNotEmpty()) {
            associations.chunked(500).forEach { chunk ->
                wordDao.insertAssociations(chunk)
            }
        }
    }

    private suspend fun saveRelatedEntities(word: WordDetails, wordId: Long) {
        if (word.synonyms.isNotEmpty()) {
            wordDao.insertSynonyms(word.toSynonymEntities(wordId))
        }
        if (word.similarWords.isNotEmpty()) {
            wordDao.insertSimilarWords(word.toSimilarWordEntities(wordId))
        }
        if (word.cognates.isNotEmpty()) {
            wordDao.insertCognates(word.toCognateEntities(wordId))
        }
    }
}
