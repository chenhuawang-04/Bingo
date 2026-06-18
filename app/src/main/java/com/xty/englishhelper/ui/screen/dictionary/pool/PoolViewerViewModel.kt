package com.xty.englishhelper.ui.screen.dictionary.pool

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.WordGraph
import com.xty.englishhelper.domain.repository.WordPoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 词池总览（仪表盘 + 画廊）。装配关系图（边图）一次，供仪表盘统计与簇卡片展示。 */
@HiltViewModel
class PoolViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wordPoolRepository: WordPoolRepository
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(PoolViewerUiState())
    val uiState: StateFlow<PoolViewerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) { wordPoolRepository.getWordRelationGraph(dictionaryId) }
            }.onSuccess { graph ->
                _uiState.update { it.copy(isLoading = false, graph = graph) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }
}

data class PoolViewerUiState(
    val isLoading: Boolean = true,
    val graph: WordGraph? = null,
    val error: String? = null
)
