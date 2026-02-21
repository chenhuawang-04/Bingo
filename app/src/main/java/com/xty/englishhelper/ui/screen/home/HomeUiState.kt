package com.xty.englishhelper.ui.screen.home

import com.xty.englishhelper.domain.model.Dictionary

data class DashboardStats(
    val averageRetention: Double = 0.0,
    val dueCount: Int = 0,
    val reviewedToday: Int = 0,
    val todayTotal: Int = 0,
    val estimatedClearHours: Double? = null,
    val hasData: Boolean = false
)

data class HomeUiState(
    val dictionaries: List<Dictionary> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val deleteTarget: Dictionary? = null,
    val newDictName: String = "",
    val newDictDesc: String = "",
    val selectedColorIndex: Int = 0,
    val error: String? = null,
    val dashboard: DashboardStats = DashboardStats()
)
