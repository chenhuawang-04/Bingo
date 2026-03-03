package com.xty.englishhelper.domain.model

enum class PoolStrategy {
    BALANCED,
    BALANCED_WITH_AI,
    QUALITY_FIRST;

    val dbValue get() = when (this) {
        BALANCED, BALANCED_WITH_AI -> "BALANCED"
        QUALITY_FIRST -> "QUALITY_FIRST"
    }
    val algorithmVersion get() = when (this) {
        BALANCED, BALANCED_WITH_AI -> "BALANCED_v1"
        QUALITY_FIRST -> "QF_v1"
    }
}
