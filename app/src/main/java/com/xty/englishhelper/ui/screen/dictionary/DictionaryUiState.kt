package com.xty.englishhelper.ui.screen.dictionary

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.organize.OrganizeTask

enum class EntryPresenceFilter {
    ANY,
    PRESENT,
    MISSING
}

data class DictionaryWordFilter(
    val phonetic: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val meanings: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val rootExplanation: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val decomposition: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val synonyms: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val similarWords: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val cognates: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val inflections: EntryPresenceFilter = EntryPresenceFilter.ANY,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val startsWith: String = ""
)

data class DictionaryUiState(
    val dictionary: Dictionary? = null,
    val words: List<WordDetails> = emptyList(),
    val filteredWords: List<WordDetails> = emptyList(),
    val units: List<StudyUnit> = emptyList(),
    val searchQuery: String = "",
    val wordFilter: DictionaryWordFilter = DictionaryWordFilter(),
    val showFilterDialog: Boolean = false,
    val isBatchMode: Boolean = false,
    val selectedWordIds: Set<Long> = emptySet(),
    val showBatchDeleteConfirm: Boolean = false,
    val isLoading: Boolean = true,
    val deleteTarget: WordDetails? = null,
    val showCreateUnitDialog: Boolean = false,
    val newUnitName: String = "",
    val error: String? = null,
    val currentPage: Int = 0,
    val pageSize: Int = 10,
    val unitCurrentPage: Int = 0,
    val unitPageSize: Int = 8,
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
    val totalPages: Int get() = if (filteredWords.isEmpty()) 1 else (filteredWords.size + pageSize - 1) / pageSize
    val pagedWords: List<WordDetails> get() = filteredWords.drop(currentPage * pageSize).take(pageSize)
    val totalUnitPages: Int get() = if (units.isEmpty()) 1 else (units.size + unitPageSize - 1) / unitPageSize
    val pagedUnits: List<StudyUnit> get() = units.drop(unitCurrentPage * unitPageSize).take(unitPageSize)
    val organizingCount: Int get() = organizeTasks.count { it.value.status == com.xty.englishhelper.domain.organize.OrganizeTaskStatus.ORGANIZING }
    val hasActiveWordFilter: Boolean get() = wordFilter != DictionaryWordFilter()
    val selectedFilteredCount: Int get() = filteredWords.count { it.id in selectedWordIds }
}
