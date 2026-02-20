package com.xty.englishhelper.data.mapper

import com.xty.englishhelper.data.local.entity.DictionaryEntity
import com.xty.englishhelper.domain.model.Dictionary

fun DictionaryEntity.toDomain() = Dictionary(
    id = id,
    name = name,
    description = description,
    color = color,
    wordCount = wordCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Dictionary.toEntity() = DictionaryEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    wordCount = wordCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)
