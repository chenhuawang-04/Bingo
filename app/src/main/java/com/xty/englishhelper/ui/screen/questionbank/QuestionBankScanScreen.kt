package com.xty.englishhelper.ui.screen.questionbank

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankScanScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuestionBankScanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onImagesSelected(uris)
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPdfSelected(it) }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.savedPaperId.collect {
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描试卷") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.phase == ScanPhase.PREVIEW) viewModel.resetToSelect()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (state.phase) {
            ScanPhase.SELECT -> {
                SelectPhaseContent(
                    onSelectImages = { imagePickerLauncher.launch("image/*") },
                    onSelectPdf = { pdfPickerLauncher.launch("application/pdf") },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
            ScanPhase.SCANNING -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        if (state.isCompressing) {
                            Text(
                                "正在压缩图片…",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "压缩完成后将开始识别",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text("正在识别试卷…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            ScanPhase.PREVIEW -> {
                PreviewPhaseContent(
                    state = state,
                    onTitleChange = viewModel::updatePaperTitle,
                    onGroupSectionLabelChange = viewModel::updateGroupSectionLabel,
                    onGroupSourceUrlChange = viewModel::updateGroupSourceUrl,
                    onQuestionTextChange = viewModel::updateQuestionText,
                    onSave = viewModel::save,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

@Composable
private fun SelectPhaseContent(
    onSelectImages: () -> Unit,
    onSelectPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("选择试卷来源", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSelectImages,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("选择图片")
            }

            OutlinedButton(
                onClick = onSelectPdf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("选择 PDF")
            }
        }
    }
}

@Composable
private fun PreviewPhaseContent(
    state: ScanUiState,
    onTitleChange: (String) -> Unit,
    onGroupSectionLabelChange: (Int, String) -> Unit,
    onGroupSourceUrlChange: (Int, String) -> Unit,
    onQuestionTextChange: (Int, Int, String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Confidence warning
        if (state.confidence < 0.7f && state.confidence > 0f) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "识别置信度较低（${(state.confidence * 100).toInt()}%），请核对扫描结果",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Paper title
        item {
            OutlinedTextField(
                value = state.paperTitle,
                onValueChange = onTitleChange,
                label = { Text("试卷名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Question groups
        itemsIndexed(state.editableGroups) { groupIndex, group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Section label
                    OutlinedTextField(
                        value = group.sectionLabel,
                        onValueChange = { onGroupSectionLabelChange(groupIndex, it) },
                        label = { Text("节标签") },
                        placeholder = { Text("如: Text 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Source URL
                    OutlinedTextField(
                        value = group.sourceUrl,
                        onValueChange = { onGroupSourceUrlChange(groupIndex, it) },
                        label = { Text("来源 URL") },
                        placeholder = { Text("来源链接（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (group.sourceUrl.isBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Text(
                                "来源未识别，保存后可手动补充",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Passage preview
                    if (group.passageParagraphs.isNotEmpty()) {
                        Text("文章预览", style = MaterialTheme.typography.labelMedium)
                        Text(
                            group.passageParagraphs.joinToString("\n").take(300) + if (group.passageParagraphs.joinToString("\n").length > 300) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (group.sentenceOptions.isNotEmpty()) {
                        val optionsLabel = if (group.questionType == "COMMENT_OPINION_MATCH") "可选观点" else "可选句子"
                        Text(optionsLabel, style = MaterialTheme.typography.labelMedium)
                        Text(
                            group.sentenceOptions.joinToString("\n").take(300) +
                                if (group.sentenceOptions.joinToString("\n").length > 300) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider()

                    // Questions
                    Text("题目 (${group.questions.size})", style = MaterialTheme.typography.labelMedium)
                    group.questions.forEachIndexed { qIndex, question ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${question.questionNumber}.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = question.questionText,
                                    onValueChange = { onQuestionTextChange(groupIndex, qIndex, it) },
                                    label = { Text("题干") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 1, maxLines = 3
                                )
                                // Options display (read-only summary)
                                val options = listOfNotNull(
                                    question.optionA.takeIf { it.isNotBlank() }?.let { "[A] $it" },
                                    question.optionB.takeIf { it.isNotBlank() }?.let { "[B] $it" },
                                    question.optionC.takeIf { it.isNotBlank() }?.let { "[C] $it" },
                                    question.optionD.takeIf { it.isNotBlank() }?.let { "[D] $it" }
                                )
                                if (options.isNotEmpty()) {
                                    Text(
                                        options.joinToString("  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Validation: empty question text (skip for CLOZE where it's intentionally empty)
                                if (question.questionText.isBlank() &&
                                    group.questionType != "CLOZE" &&
                                    group.questionType != "TRANSLATION" &&
                                    group.questionType != "PARAGRAPH_ORDER" &&
                                    group.questionType != "SENTENCE_INSERTION" &&
                                    group.questionType != "COMMENT_OPINION_MATCH"
                                ) {
                                    Text(
                                        "此字段不能为空",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Save button
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text("保存")
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
