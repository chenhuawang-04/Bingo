package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.StudyDao
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.mapper.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val studyDao: StudyDao
) : StudyRepository {

    override suspend fun getStudyState(wordId: Long): WordStudyState? =
        studyDao.getStudyState(wordId)?.toDomain()

    override suspend fun upsertStudyState(state: WordStudyState) =
        studyDao.upsertStudyState(state.toEntity())

    override suspend fun getDueWords(unitIds: List<Long>, now: Long): List<WordDetails> =
        studyDao.getDueWords(unitIds, now).map { it.toDomain() }

    override suspend fun getNewWords(unitIds: List<Long>): List<WordDetails> =
        studyDao.getNewWords(unitIds).map { it.toDomain() }

    override suspend fun countDueWords(unitId: Long, now: Long): Int =
        studyDao.countDueWords(unitId, now)

    override suspend fun countNewWords(unitId: Long): Int =
        studyDao.countNewWords(unitId)

    override suspend fun getStudyStatesForDictionary(dictionaryId: Long): List<WordStudyState> =
        studyDao.getStudyStatesForDictionary(dictionaryId).map { it.toDomain() }

    override suspend fun countAllDueWords(now: Long): Int =
        studyDao.countAllDueWords(now)

    override suspend fun countReviewedToday(todayStart: Long, now: Long): Int =
        studyDao.countReviewedToday(todayStart, now)

    override suspend fun getAllActiveStudyStates(): List<WordStudyState> =
        studyDao.getAllActiveStudyStates().map { it.toDomain() }
}
