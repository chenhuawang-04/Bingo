package com.xty.englishhelper.ui.screen.importexport

import com.xty.englishhelper.domain.model.Dictionary

data class ImportExportUiState(
    val dictionaries: List<Dictionary> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
