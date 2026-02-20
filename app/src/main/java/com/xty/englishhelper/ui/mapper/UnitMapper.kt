package com.xty.englishhelper.ui.mapper

import com.xty.englishhelper.data.local.entity.UnitEntity
import com.xty.englishhelper.data.local.entity.WordStudyStateEntity
import com.xty.englishhelper.data.local.relation.UnitWithWordCount
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordStudyState

fun UnitEntity.toDomain(wordCount: Int = 0) = StudyUnit(
    id = id,
    dictionaryId = dictionaryId,
    name = name,
    defaultRepeatCount = defaultRepeatCount,
    wordCount = wordCount,
    createdAt = createdAt
)

fun UnitWithWordCount.toDomain() = StudyUnit(
    id = id,
    dictionaryId = dictionaryId,
    name = name,
    defaultRepeatCount = defaultRepeatCount,
    wordCount = wordCount,
    createdAt = createdAt
)

fun StudyUnit.toEntity() = UnitEntity(
    id = id,
    dictionaryId = dictionaryId,
    name = name,
    defaultRepeatCount = defaultRepeatCount,
    createdAt = createdAt
)

fun WordStudyStateEntity.toDomain() = WordStudyState(
    wordId = wordId,
    remainingReviews = remainingReviews,
    easeLevel = easeLevel,
    nextReviewAt = nextReviewAt,
    lastReviewedAt = lastReviewedAt
)

fun WordStudyState.toEntity() = WordStudyStateEntity(
    wordId = wordId,
    remainingReviews = remainingReviews,
    easeLevel = easeLevel,
    nextReviewAt = nextReviewAt,
    lastReviewedAt = lastReviewedAt
)
