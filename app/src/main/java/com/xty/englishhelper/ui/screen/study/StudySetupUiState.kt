package com.xty.englishhelper.ui.screen.study

data class StudySetupUiState(
    val unitItems: List<UnitStudyInfo> = emptyList(),
    val selectedUnitIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class UnitStudyInfo(
    val unitId: Long,
    val unitName: String,
    val wordCount: Int,
    val dueCount: Int,
    val newCount: Int
)
