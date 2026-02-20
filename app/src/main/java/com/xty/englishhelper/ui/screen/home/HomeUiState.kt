package com.xty.englishhelper.ui.screen.home

import com.xty.englishhelper.domain.model.Dictionary

data class HomeUiState(
    val dictionaries: List<Dictionary> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val deleteTarget: Dictionary? = null,
    val newDictName: String = "",
    val newDictDesc: String = "",
    val selectedColorIndex: Int = 0,
    val error: String? = null
)
