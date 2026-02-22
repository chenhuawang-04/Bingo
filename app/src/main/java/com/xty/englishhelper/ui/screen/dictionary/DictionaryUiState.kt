package com.xty.englishhelper.ui.screen.dictionary

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails

data class DictionaryUiState(
    val dictionary: Dictionary? = null,
    val words: List<WordDetails> = emptyList(),
    val units: List<StudyUnit> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val deleteTarget: WordDetails? = null,
    val showCreateUnitDialog: Boolean = false,
    val newUnitName: String = "",
    val error: String? = null,
    val currentPage: Int = 0,
    val pageSize: Int = 10
) {
    val totalPages: Int get() = if (words.isEmpty()) 1 else (words.size + pageSize - 1) / pageSize
    val pagedWords: List<WordDetails> get() = words.drop(currentPage * pageSize).take(pageSize)
}
