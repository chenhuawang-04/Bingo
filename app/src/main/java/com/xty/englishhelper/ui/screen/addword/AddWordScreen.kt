package com.xty.englishhelper.ui.screen.addword

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(
    onBack: () -> Unit,
    viewModel: AddWordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑单词" else "添加单词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.isSaving && state.spelling.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AddWordContent(
            state = state,
            isWide = isWide,
            onSpellingChange = viewModel::onSpellingChange,
            onPhoneticChange = viewModel::onPhoneticChange,
            onRootExplanationChange = viewModel::onRootExplanationChange,
            onToggleUnit = viewModel::toggleUnitSelection,
            onOrganizeWithAi = viewModel::organizeWithAi,
            onMeaningChange = viewModel::onMeaningChange,
            onAddMeaning = viewModel::addMeaning,
            onRemoveMeaning = viewModel::removeMeaning,
            onDecompositionPartChange = viewModel::onDecompositionPartChange,
            onAddDecompositionPart = viewModel::addDecompositionPart,
            onRemoveDecompositionPart = viewModel::removeDecompositionPart,
            onInflectionChange = viewModel::onInflectionChange,
            onAddInflection = viewModel::addInflection,
            onRemoveInflection = viewModel::removeInflection,
            onSynonymChange = viewModel::onSynonymChange,
            onAddSynonym = viewModel::addSynonym,
            onRemoveSynonym = viewModel::removeSynonym,
            onSimilarWordChange = viewModel::onSimilarWordChange,
            onAddSimilarWord = viewModel::addSimilarWord,
            onRemoveSimilarWord = viewModel::removeSimilarWord,
            onCognateChange = viewModel::onCognateChange,
            onAddCognate = viewModel::addCognate,
            onRemoveCognate = viewModel::removeCognate,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
