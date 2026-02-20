package com.xty.englishhelper.ui.screen.unitdetail

import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails

data class UnitDetailUiState(
    val unit: StudyUnit? = null,
    val wordsInUnit: List<WordDetails> = emptyList(),
    val allWordsInDictionary: List<WordDetails> = emptyList(),
    val wordIdsInUnit: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val showRenameDialog: Boolean = false,
    val renameText: String = "",
    val showRepeatCountDialog: Boolean = false,
    val repeatCountText: String = "",
    val showAddWordsDialog: Boolean = false,
    val addWordsSelection: Set<Long> = emptySet(),
    val showDeleteConfirm: Boolean = false,
    val removeWordTarget: WordDetails? = null,
    val error: String? = null
)
