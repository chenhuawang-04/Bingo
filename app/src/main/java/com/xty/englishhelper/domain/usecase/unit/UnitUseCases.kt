package com.xty.englishhelper.domain.usecase.unit

import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.UnitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUnitsWithWordCountUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    operator fun invoke(dictionaryId: Long): Flow<List<StudyUnit>> =
        repository.getUnitsWithWordCount(dictionaryId)
}

class GetUnitByIdUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long): StudyUnit? = repository.getUnitById(unitId)
}

class CreateUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(dictionaryId: Long, name: String, repeatCount: Int = 2): Long {
        val unit = StudyUnit(
            dictionaryId = dictionaryId,
            name = name,
            defaultRepeatCount = repeatCount
        )
        return repository.insertUnit(unit)
    }
}

class RenameUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long, name: String) =
        repository.updateUnitName(unitId, name)
}

class UpdateRepeatCountUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long, repeatCount: Int) =
        repository.updateRepeatCount(unitId, repeatCount)
}

class DeleteUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long) = repository.deleteUnit(unitId)
}

class AddWordsToUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long, wordIds: List<Long>) =
        repository.addWordsToUnit(unitId, wordIds)
}

class RemoveWordsFromUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long, wordIds: List<Long>) =
        repository.removeWordsFromUnit(unitId, wordIds)
}

class GetWordsInUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    operator fun invoke(unitId: Long): Flow<List<WordDetails>> =
        repository.getWordsInUnit(unitId)
}

class GetWordIdsInUnitUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(unitId: Long): List<Long> =
        repository.getWordIdsInUnit(unitId)
}

class GetUnitIdsForWordUseCase @Inject constructor(
    private val repository: UnitRepository
) {
    suspend operator fun invoke(wordId: Long): List<Long> =
        repository.getUnitIdsForWord(wordId)
}
