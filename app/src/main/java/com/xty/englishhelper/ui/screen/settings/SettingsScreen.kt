package com.xty.englishhelper.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var modelExpanded by remember { mutableStateOf(false) }

    val availableModels = when (state.provider) {
        AiProvider.ANTHROPIC -> Constants.ANTHROPIC_AVAILABLE_MODELS
        AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_AVAILABLE_MODELS
    }

    val sectionTitle = when (state.provider) {
        AiProvider.ANTHROPIC -> "Anthropic API"
        AiProvider.OPENAI_COMPATIBLE -> "OpenAI Compatible API"
    }

    val baseUrlPlaceholder = when (state.provider) {
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/"
        AiProvider.OPENAI_COMPATIBLE -> "https://api.openai.com/"
    }

    val apiKeyPlaceholder = when (state.provider) {
        AiProvider.ANTHROPIC -> "sk-ant-..."
        AiProvider.OPENAI_COMPATIBLE -> "sk-..."
    }

    LaunchedEffect(state.testResult, state.error) {
        (state.testResult ?: state.error)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 560.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Provider selection
                Text("AI 服务提供商", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.provider == AiProvider.ANTHROPIC,
                        onClick = { viewModel.onProviderChange(AiProvider.ANTHROPIC) },
                        label = { Text("Anthropic") }
                    )
                    FilterChip(
                        selected = state.provider == AiProvider.OPENAI_COMPATIBLE,
                        onClick = { viewModel.onProviderChange(AiProvider.OPENAI_COMPATIBLE) },
                        label = { Text("OpenAI 兼容") }
                    )
                }

                Text(sectionTitle, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text("Base URL") },
                    placeholder = { Text(baseUrlPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text(apiKeyPlaceholder) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val filteredModels = availableModels.filter { (modelId, modelName) ->
                    state.selectedModel.isBlank() ||
                        modelId.contains(state.selectedModel, ignoreCase = true) ||
                        modelName.contains(state.selectedModel, ignoreCase = true)
                }

                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.selectedModel,
                        onValueChange = { value ->
                            viewModel.onModelChange(value)
                            modelExpanded = true
                        },
                        label = { Text("模型") },
                        placeholder = { Text("选择或输入模型名称") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )
                    if (filteredModels.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            filteredModels.forEach { (modelId, modelName) ->
                                DropdownMenuItem(
                                    text = { Text("$modelName ($modelId)") },
                                    onClick = {
                                        viewModel.onModelChange(modelId)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = viewModel::testConnection,
                    enabled = !state.isTesting && state.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("  正在测试…")
                    } else {
                        Text("测试连接")
                    }
                }
            }
        }
    }
}
