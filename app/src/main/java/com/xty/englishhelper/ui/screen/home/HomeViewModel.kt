package com.xty.englishhelper.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.usecase.dictionary.CreateDictionaryUseCase
import com.xty.englishhelper.domain.usecase.dictionary.DeleteDictionaryUseCase
import com.xty.englishhelper.domain.usecase.dictionary.GetAllDictionariesUseCase
import com.xty.englishhelper.ui.theme.DictionaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllDictionaries: GetAllDictionariesUseCase,
    private val createDictionary: CreateDictionaryUseCase,
    private val deleteDictionary: DeleteDictionaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDictionaries()
    }

    private fun loadDictionaries() {
        viewModelScope.launch {
            getAllDictionaries()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { dictionaries ->
                    _uiState.update { it.copy(dictionaries = dictionaries, isLoading = false) }
                }
        }
    }

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                newDictName = "",
                newDictDesc = "",
                selectedColorIndex = (0 until DictionaryColors.size).random()
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(newDictName = name) }
    }

    fun onDescChange(desc: String) {
        _uiState.update { it.copy(newDictDesc = desc) }
    }

    fun onColorSelect(index: Int) {
        _uiState.update { it.copy(selectedColorIndex = index) }
    }

    fun confirmCreate() {
        val state = _uiState.value
        if (state.newDictName.isBlank()) return
        viewModelScope.launch {
            val color = DictionaryColors[state.selectedColorIndex].value.toLong().toInt()
            createDictionary(state.newDictName.trim(), state.newDictDesc.trim(), color)
            _uiState.update { it.copy(showCreateDialog = false) }
        }
    }

    fun showDeleteConfirm(dict: com.xty.englishhelper.domain.model.Dictionary) {
        _uiState.update { it.copy(deleteTarget = dict) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            deleteDictionary(target.id)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
