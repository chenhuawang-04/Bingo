package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
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
    private val dictionaryRepository: DictionaryRepository
) {
    suspend operator fun invoke(word: WordDetails): Long {
        val id = if (word.id == 0L) {
            wordRepository.insertWord(word)
        } else {
            wordRepository.updateWord(word)
            word.id
        }
        dictionaryRepository.updateWordCount(word.dictionaryId)
        return id
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
