package com.xty.englishhelper.ui.screen.study

import com.xty.englishhelper.domain.model.StudyMode

data class StudySetupUiState(
    val unitItems: List<UnitStudyInfo> = emptyList(),
    val selectedUnitIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedMode: StudyMode = StudyMode.NORMAL
)

data class UnitStudyInfo(
    val unitId: Long,
    val unitName: String,
    val wordCount: Int,
    val dueCount: Int,
    val newCount: Int
)
