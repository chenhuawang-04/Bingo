package com.xty.englishhelper.domain.usecase.dictionary

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllDictionariesUseCase @Inject constructor(
    private val repository: DictionaryRepository
) {
    operator fun invoke(): Flow<List<Dictionary>> = repository.getAllDictionaries()
}

class GetDictionaryByIdUseCase @Inject constructor(
    private val repository: DictionaryRepository
) {
    suspend operator fun invoke(id: Long): Dictionary? = repository.getDictionaryById(id)
}

class CreateDictionaryUseCase @Inject constructor(
    private val repository: DictionaryRepository
) {
    suspend operator fun invoke(name: String, description: String, color: Int): Long {
        val dictionary = Dictionary(
            name = name,
            description = description,
            color = color
        )
        return repository.insertDictionary(dictionary)
    }
}

class UpdateDictionaryUseCase @Inject constructor(
    private val repository: DictionaryRepository
) {
    suspend operator fun invoke(dictionary: Dictionary) = repository.updateDictionary(dictionary)
}

class DeleteDictionaryUseCase @Inject constructor(
    private val repository: DictionaryRepository
) {
    suspend operator fun invoke(id: Long) = repository.deleteDictionary(id)
}
