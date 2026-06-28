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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect

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

    AppTopBarEffect(
        title = { Text(stringResource(R.string.question_scan_paper)) },
        navigationIcon = {
            AppTopBarBackButton(
                onClick = {
                    if (state.phase == ScanPhase.PREVIEW) viewModel.resetToSelect()
                    else onBack()
                }
            )
        }
    )

    Scaffold(
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
                                stringResource(R.string.scan_compressing),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.scan_compress_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text(stringResource(R.string.scan_recognizing), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            ScanPhase.PREVIEW -> {
                PreviewPhaseContent(
                    state = state,
                    onTitleChange = viewModel::updatePaperTitle,
                    onGroupQuestionTypeChange = viewModel::updateGroupQuestionType,
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
            Text(stringResource(R.string.scan_select_source), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSelectImages,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, contentDescription = stringResource(R.string.scan_image), modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.question_select_image))
            }

            OutlinedButton(
                onClick = onSelectPdf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.scan_pdf), modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.question_select_pdf))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewPhaseContent(
    state: ScanUiState,
    onTitleChange: (String) -> Unit,
    onGroupQuestionTypeChange: (Int, String) -> Unit,
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
                        stringResource(R.string.scan_confidence_warning, (state.confidence * 100).toInt()),
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
                label = { Text(stringResource(R.string.scan_paper_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Question groups
        itemsIndexed(state.editableGroups) { groupIndex, group ->
            var questionTypeExpanded by remember(group.uid) { mutableStateOf(false) }
            val selectedQuestionType = QuestionType.entries.firstOrNull { it.name == group.questionType }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = questionTypeExpanded,
                        onExpandedChange = { questionTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                                value = selectedQuestionType?.displayName
                                ?: if (group.rawQuestionType.isNotBlank()) stringResource(R.string.scan_unrecognized_type, group.rawQuestionType) else stringResource(R.string.scan_select_type),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.question_scan)) },
                            supportingText = {
                                if (selectedQuestionType == null && group.rawQuestionType.isNotBlank()) {
                                    Text(stringResource(R.string.scan_ocr_raw, group.rawQuestionType))
                                }
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = questionTypeExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = questionTypeExpanded,
                            onDismissRequest = { questionTypeExpanded = false }
                        ) {
                            QuestionType.entries
                                .filterNot { it == QuestionType.NEW_TYPE }
                                .forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName) },
                                        onClick = {
                                            onGroupQuestionTypeChange(groupIndex, type.name)
                                            questionTypeExpanded = false
                                        }
                                    )
                                }
                        }
                    }

                    // Section label
                    OutlinedTextField(
                        value = group.sectionLabel,
                        onValueChange = { onGroupSectionLabelChange(groupIndex, it) },
                        label = { Text(stringResource(R.string.scan_section_label)) },
                        placeholder = { Text(stringResource(R.string.scan_section_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Source URL
                    OutlinedTextField(
                        value = group.sourceUrl,
                        onValueChange = { onGroupSourceUrlChange(groupIndex, it) },
                        label = { Text(stringResource(R.string.scan_source_url)) },
                        placeholder = { Text(stringResource(R.string.scan_source_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (group.sourceUrl.isBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Text(
                                stringResource(R.string.scan_source_not_identified),
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Passage preview
                    if (group.passageParagraphs.isNotEmpty()) {
                        Text(stringResource(R.string.scan_passage_preview), style = MaterialTheme.typography.labelMedium)
                        Text(
                            group.passageParagraphs.joinToString("\n").take(300) + if (group.passageParagraphs.joinToString("\n").length > 300) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (group.sentenceOptions.isNotEmpty()) {
                        val optionsLabel = when (group.questionType) {
                            "COMMENT_OPINION_MATCH" -> stringResource(R.string.scan_optional_opinion)
                            "SUBHEADING_MATCH" -> stringResource(R.string.scan_optional_subheading)
                            "INFORMATION_MATCH" -> stringResource(R.string.scan_optional_info)
                            else -> stringResource(R.string.scan_optional_sentence)
                        }
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
                    Text(stringResource(R.string.scan_question_count, group.questions.size), style = MaterialTheme.typography.labelMedium)
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
                                    label = { Text(stringResource(R.string.scan_question_stem)) },
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
                                    group.questionType != "COMMENT_OPINION_MATCH" &&
                                    group.questionType != "SUBHEADING_MATCH" &&
                                    group.questionType != "INFORMATION_MATCH"
                                ) {
                                    Text(
                                        stringResource(R.string.scan_field_required),
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
                Text(stringResource(R.string.common_save))
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
