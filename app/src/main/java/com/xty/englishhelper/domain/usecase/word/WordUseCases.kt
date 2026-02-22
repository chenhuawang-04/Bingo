package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.article.LinkWordToArticlesUseCase
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class GetWordsByDictionaryUseCase @Inject constructor(
    private val repository: WordRepository
) {
    operator fun invoke(dictionaryId: Long): Flow<List<WordDetails>> =
        repository.getWordsByDictionary(dictionaryId)
}

class SearchWordsUseCase @Inject constructor(
    private val repository: WordRepository
) {
    operator fun invoke(dictionaryId: Long, query: String): Flow<List<WordDetails>> =
        repository.searchWords(dictionaryId, query)
}

class GetWordByIdUseCase @Inject constructor(
    private val repository: WordRepository
) {
    suspend operator fun invoke(wordId: Long): WordDetails? = repository.getWordById(wordId)
}

class SaveWordUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val linkWordToArticles: LinkWordToArticlesUseCase
) {
    suspend operator fun invoke(word: WordDetails): Long {
        val normalized = word.spelling.trim().lowercase()
        val wordWithNormalized = word.copy(normalizedSpelling = normalized)

        val savedId = if (word.id == 0L) {
            // Insert mode: check for existing word with same normalized spelling
            val existing = wordRepository.findByNormalizedSpelling(word.dictionaryId, normalized)
            if (existing != null) {
                // Upsert: update using existing id and wordUid
                val merged = wordWithNormalized.copy(
                    id = existing.id,
                    wordUid = existing.wordUid
                )
                wordRepository.updateWord(merged)
                wordRepository.recomputeAssociations(existing.id, word.dictionaryId)
                existing.id
            } else {
                // Truly new: generate UUID
                val newWord = wordWithNormalized.copy(wordUid = UUID.randomUUID().toString())
                val id = wordRepository.insertWord(newWord)
                dictionaryRepository.updateWordCount(word.dictionaryId)
                wordRepository.recomputeAssociations(id, word.dictionaryId)
                id
            }
        } else {
            // Edit mode: fetch existing record to preserve wordUid and createdAt
            val existing = wordRepository.getWordById(word.id)
            val preserved = wordWithNormalized.copy(
                wordUid = word.wordUid.ifBlank { existing?.wordUid ?: "" },
                createdAt = existing?.createdAt ?: word.createdAt
            )
            wordRepository.updateWord(preserved)
            dictionaryRepository.updateWordCount(word.dictionaryId)
            wordRepository.recomputeAssociations(word.id, word.dictionaryId)
            word.id
        }

        // Link word to articles asynchronously (non-blocking)
        try {
            val savedWord = wordRepository.getWordById(savedId)
            if (savedWord != null) {
                linkWordToArticles(savedId, word.dictionaryId, word.spelling, word.inflections)
            }
        } catch (e: Exception) {
            // Linkage failure is non-critical, don't affect word save
        }

        return savedId
    }
}

class DeleteWordUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val dictionaryRepository: DictionaryRepository
) {
    suspend operator fun invoke(wordId: Long, dictionaryId: Long) {
        wordRepository.deleteWord(wordId)
        dictionaryRepository.updateWordCount(dictionaryId)
    }
}

class ResolveLinkedWordsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(dictionaryId: Long, spellings: List<String>): Map<String, Long> {
        val normalized = spellings.filter { it.isNotBlank() }.map { it.trim().lowercase() }.distinct()
        if (normalized.isEmpty()) return emptyMap()
        return wordRepository.findExistingWordIds(dictionaryId, normalized)
    }
}

class GetAssociatedWordsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(wordId: Long): List<AssociatedWordInfo> =
        wordRepository.getAssociatedWords(wordId)
}
