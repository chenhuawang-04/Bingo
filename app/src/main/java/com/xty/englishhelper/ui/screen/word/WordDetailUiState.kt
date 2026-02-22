package com.xty.englishhelper.ui.screen.word

import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.WordExample

data class WordDetailUiState(
    val word: WordDetails? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val error: String? = null,
    val linkedWordIds: Map<String, Long> = emptyMap(),
    val associatedWords: List<AssociatedWordInfo> = emptyList(),
    val examples: List<WordExample> = emptyList()
)
