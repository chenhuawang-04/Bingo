package com.xty.englishhelper.domain.model

enum class MorphemeRole { PREFIX, ROOT, SUFFIX, STEM, LINKING, OTHER }

data class DecompositionPart(
    val segment: String,
    val role: MorphemeRole,
    val meaning: String = ""
)
