package com.xty.englishhelper.ui.screen.dictionary

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.organize.OrganizeTask

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
    val pageSize: Int = 10,
    val poolCount: Int = 0,
    val isRebuildingPools: Boolean = false,
    val rebuildProgress: Pair<Int, Int>? = null,
    val rebuildError: String? = null,
    val showQfConfirmDialog: Boolean = false,
    val qfWordCount: Int = 0,
    val outdatedStrategies: Set<String> = emptySet(),
    val organizeTasks: Map<Long, OrganizeTask> = emptyMap(),
    val organizingWordIds: Set<Long> = emptySet(),
    val showOrganizeDetailDialog: Boolean = false
) {
    val totalPages: Int get() = if (words.isEmpty()) 1 else (words.size + pageSize - 1) / pageSize
    val pagedWords: List<WordDetails> get() = words.drop(currentPage * pageSize).take(pageSize)
    val organizingCount: Int get() = organizeTasks.count { it.value.status == com.xty.englishhelper.domain.organize.OrganizeTaskStatus.ORGANIZING }
}
