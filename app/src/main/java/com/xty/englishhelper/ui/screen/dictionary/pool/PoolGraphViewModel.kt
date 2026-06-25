package com.xty.englishhelper.ui.screen.dictionary.pool

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordGraph
import com.xty.englishhelper.domain.model.WordGraphEdgeDetail
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

/** 关系大图渲染页的 VM：装配关系图 → 后台布局 → 缓存；处理孤立词开关与节点/关系选中。 */
@HiltViewModel
class PoolGraphViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wordPoolRepository: WordPoolRepository
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L
    private val focusClusterId: Int = savedStateHandle["focusClusterId"] ?: -1

    private var graph: WordGraph? = null
    private var layoutGeneration = 0

    private val _uiState = MutableStateFlow(PoolGraphUiState(initialFocusClusterId = focusClusterId))
    val uiState: StateFlow<PoolGraphUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val g = withContext(Dispatchers.IO) { wordPoolRepository.getWordRelationGraph(dictionaryId) }
                graph = g
                val layout = withContext(Dispatchers.Default) {
                    PoolGraphLayout.build(g, includeIsolated = _uiState.value.includeIsolated)
                }
                layout
            }.onSuccess { layout ->
                _uiState.update { it.copy(isLoading = false, layout = layout) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun setIncludeIsolated(include: Boolean) {
        val g = graph ?: return
        if (_uiState.value.includeIsolated == include) return
        val generation = ++layoutGeneration
        _uiState.update { it.copy(includeIsolated = include) }
        viewModelScope.launch {
            val layout = withContext(Dispatchers.Default) { PoolGraphLayout.build(g, includeIsolated = include) }
            _uiState.update { st ->
                if (generation == layoutGeneration && st.includeIsolated == include) {
                    st.copy(layout = layout)
                } else {
                    st
                }
            }
        }
    }

    /** 点中某节点：先用内存图同步给出关系列表，再异步懒加载释义。 */
    fun selectNode(nodeIndex: Int) {
        val g = graph ?: return
        val layout = _uiState.value.layout ?: return
        if (nodeIndex !in g.nodes.indices) return
        val node = g.nodes[nodeIndex]

        val relations = layout.nodeEdges[nodeIndex].map { edgeIndex ->
            val e = g.edges[edgeIndex]
            val other = if (e.aIndex == nodeIndex) e.bIndex else e.aIndex
            RelationRow(
                edgeIndex = edgeIndex,
                otherSpelling = g.nodes[other].spelling,
                type = e.type,
                relationStrength = e.relationStrength
            )
        }.sortedByDescending { it.relationStrength }

        _uiState.update {
            it.copy(
                selectedNode = SelectedNodeState(
                    nodeIndex = nodeIndex,
                    wordId = node.wordId,
                    spelling = node.spelling,
                    detail = null,
                    relations = relations
                ),
                selectedEdge = null
            )
        }

        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) { wordPoolRepository.getWordDetail(node.wordId) }
            _uiState.update { st ->
                val sel = st.selectedNode
                if (sel != null && sel.nodeIndex == nodeIndex && sel.detail == null) {
                    st.copy(selectedNode = sel.copy(detail = detail))
                } else st
            }
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selectedNode = null) }

    fun selectEdge(edgeIndex: Int) {
        val g = graph ?: return
        if (edgeIndex !in g.edges.indices) return
        val edgeId = g.edges[edgeIndex].edgeId
        _uiState.update {
            it.copy(
                selectedEdge = SelectedEdgeState(
                    edgeIndex = edgeIndex,
                    isLoading = true
                )
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { wordPoolRepository.getWordGraphEdgeDetail(edgeId) }
            }.onSuccess { detail ->
                _uiState.update { st ->
                    val selected = st.selectedEdge
                    if (selected != null && selected.edgeIndex == edgeIndex) {
                        selected.copy(
                            detail = detail,
                            isLoading = false,
                            error = if (detail == null) "关系详情不存在" else null
                        ).let { st.copy(selectedEdge = it) }
                    } else {
                        st
                    }
                }
            }.onFailure { error ->
                _uiState.update { st ->
                    val selected = st.selectedEdge
                    if (selected != null && selected.edgeIndex == edgeIndex) {
                        st.copy(
                            selectedEdge = selected.copy(
                                isLoading = false,
                                error = error.message ?: "加载失败"
                            )
                        )
                    } else {
                        st
                    }
                }
            }
        }
    }

    fun clearEdge() = _uiState.update { it.copy(selectedEdge = null) }
}

data class PoolGraphUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val layout: PoolGraphLayout? = null,
    val includeIsolated: Boolean = true,
    val initialFocusClusterId: Int = -1,
    val selectedNode: SelectedNodeState? = null,
    /** 当前查看详情的关系边（edgeIndex 指向 layout.graph.edges）。 */
    val selectedEdge: SelectedEdgeState? = null
)

data class SelectedNodeState(
    val nodeIndex: Int,
    val wordId: Long,
    val spelling: String,
    val detail: WordDetails?,
    val relations: List<RelationRow>
)

data class RelationRow(
    val edgeIndex: Int,
    val otherSpelling: String,
    val type: EdgeType,
    val relationStrength: Int
)

data class SelectedEdgeState(
    val edgeIndex: Int,
    val detail: WordGraphEdgeDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
