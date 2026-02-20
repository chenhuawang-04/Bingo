package com.xty.englishhelper.ui.screen.dictionary

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.WordDetails

data class DictionaryUiState(
    val dictionary: Dictionary? = null,
    val words: List<WordDetails> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val deleteTarget: WordDetails? = null,
    val error: String? = null
)
