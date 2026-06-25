package com.xty.englishhelper.data.mapper

import com.xty.englishhelper.data.local.entity.UnitEntity
import com.xty.englishhelper.data.local.entity.WordStudyStateEntity
import com.xty.englishhelper.data.local.relation.UnitWithWordCount
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordStudyState

fun UnitEntity.toDomain(wordCount: Int = 0) = StudyUnit(
    id = id,
    dictionaryId = dictionaryId,
    unitUid = unitUid,
    name = name,
    defaultRepeatCount = defaultRepeatCount,
    wordCount = wordCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun UnitWithWordCount.toDomain() = StudyUnit(
    id = id,
    dictionaryId = dictionaryId,
    unitUid = unitUid,
    name = name,
    defaultRepeatCount = defaultRepeatCount,
    wordCount = wordCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StudyUnit.toEntity() = UnitEntity(
    id = id,
    dictionaryId = dictionaryId,
    unitUid = unitUid,
    name = name,
    defaultRepeatCount = defaultRepeatCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WordStudyStateEntity.toDomain() = WordStudyState(
    wordId = wordId,
    studyMode = runCatching { StudyMode.valueOf(studyMode) }.getOrDefault(StudyMode.NORMAL),
    state = state,
    step = step,
    stability = stability,
    difficulty = difficulty,
    due = due,
    lastReviewAt = lastReviewAt,
    reps = reps,
    lapses = lapses
)

fun WordStudyState.toEntity() = WordStudyStateEntity(
    wordId = wordId,
    studyMode = studyMode.name,
    state = state,
    step = step,
    stability = stability,
    difficulty = difficulty,
    due = due,
    lastReviewAt = lastReviewAt,
    reps = reps,
    lapses = lapses
)
