package com.xty.englishhelper.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.debug.AiDebugEvent
import com.xty.englishhelper.data.debug.AiDebugManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiDebugViewModel @Inject constructor(
    private val debugManager: AiDebugManager
) : ViewModel() {

    private val _queue = MutableStateFlow<List<AiDebugEvent>>(emptyList())
    val queue: StateFlow<List<AiDebugEvent>> = _queue.asStateFlow()

    init {
        viewModelScope.launch {
            debugManager.events.collect { event ->
                _queue.update { it + event }
            }
        }
    }

    fun dismissCurrent() {
        _queue.update { list ->
            if (list.isEmpty()) list else list.drop(1)
        }
    }

    fun clearAll() {
        _queue.value = emptyList()
    }
}
