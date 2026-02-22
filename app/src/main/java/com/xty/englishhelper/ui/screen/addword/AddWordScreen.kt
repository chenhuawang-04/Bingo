package com.xty.englishhelper.ui.screen.addword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.PartOfSpeech
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        if (isWide) {
            // Dual-column layout for medium/expanded
            EhMaxWidthContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                maxWidth = 900.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left column: basic fields
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = state.spelling,
                            onValueChange = viewModel::onSpellingChange,
                            label = { Text("单词拼写") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (state.availableUnits.isNotEmpty()) {
                            Column {
                                Text("所属单元", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    state.availableUnits.forEach { unit ->
                                        FilterChip(
                                            selected = unit.id in state.selectedUnitIds,
                                            onClick = { viewModel.toggleUnitSelection(unit.id) },
                                            label = { Text(unit.name) }
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = viewModel::organizeWithAi,
                            enabled = !state.isAiLoading && state.spelling.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isAiLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("  正在整理…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Text("  AI 自动整理")
                            }
                        }

                        OutlinedTextField(
                            value = state.phonetic,
                            onValueChange = viewModel::onPhoneticChange,
                            label = { Text("音标") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        SectionHeader(title = "词性与词义", onAdd = viewModel::addMeaning)
                        state.meanings.forEachIndexed { index, meaning ->
                            MeaningRow(
                                meaning = meaning,
                                onPosChange = { viewModel.onMeaningChange(index, meaning.copy(pos = it)) },
                                onDefChange = { viewModel.onMeaningChange(index, meaning.copy(definition = it)) },
                                onRemove = { viewModel.removeMeaning(index) },
                                showRemove = state.meanings.size > 1
                            )
                        }

                        OutlinedTextField(
                            value = state.rootExplanation,
                            onValueChange = viewModel::onRootExplanationChange,
                            label = { Text("词根解释") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(80.dp))
                    }

                    // Right column: word relationships
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SectionHeader(title = "词根拆解", onAdd = viewModel::addDecompositionPart)
                        state.decomposition.forEachIndexed { index, part ->
                            DecompositionPartRow(
                                part = part,
                                onSegmentChange = { viewModel.onDecompositionPartChange(index, part.copy(segment = it)) },
                                onRoleChange = { viewModel.onDecompositionPartChange(index, part.copy(role = it)) },
                                onMeaningChange = { viewModel.onDecompositionPartChange(index, part.copy(meaning = it)) },
                                onRemove = { viewModel.removeDecompositionPart(index) }
                            )
                        }

                        SectionHeader(title = "词形变化", onAdd = viewModel::addInflection)
                        state.inflections.forEachIndexed { index, inflection ->
                            InflectionRow(
                                inflection = inflection,
                                onFormChange = { viewModel.onInflectionChange(index, inflection.copy(form = it)) },
                                onFormTypeChange = { viewModel.onInflectionChange(index, inflection.copy(formType = it)) },
                                onRemove = { viewModel.removeInflection(index) }
                            )
                        }

                        SectionHeader(title = "近义词", onAdd = viewModel::addSynonym)
                        state.synonyms.forEachIndexed { index, syn ->
                            SynonymRow(
                                synonym = syn,
                                onWordChange = { viewModel.onSynonymChange(index, syn.copy(word = it)) },
                                onExplanationChange = { viewModel.onSynonymChange(index, syn.copy(explanation = it)) },
                                onRemove = { viewModel.removeSynonym(index) }
                            )
                        }

                        SectionHeader(title = "形近词", onAdd = viewModel::addSimilarWord)
                        state.similarWords.forEachIndexed { index, sim ->
                            SimilarWordRow(
                                similarWord = sim,
                                onWordChange = { viewModel.onSimilarWordChange(index, sim.copy(word = it)) },
                                onMeaningChange = { viewModel.onSimilarWordChange(index, sim.copy(meaning = it)) },
                                onExplanationChange = { viewModel.onSimilarWordChange(index, sim.copy(explanation = it)) },
                                onRemove = { viewModel.removeSimilarWord(index) }
                            )
                        }

                        SectionHeader(title = "同根词", onAdd = viewModel::addCognate)
                        state.cognates.forEachIndexed { index, cog ->
                            CognateRow(
                                cognate = cog,
                                onWordChange = { viewModel.onCognateChange(index, cog.copy(word = it)) },
                                onMeaningChange = { viewModel.onCognateChange(index, cog.copy(meaning = it)) },
                                onSharedRootChange = { viewModel.onCognateChange(index, cog.copy(sharedRoot = it)) },
                                onRemove = { viewModel.removeCognate(index) }
                            )
                        }

                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        } else {
            // Compact: original single-column LazyColumn
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.spelling,
                        onValueChange = viewModel::onSpellingChange,
                        label = { Text("单词拼写") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.availableUnits.isNotEmpty()) {
                    item {
                        Column {
                            Text("所属单元", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                state.availableUnits.forEach { unit ->
                                    FilterChip(
                                        selected = unit.id in state.selectedUnitIds,
                                        onClick = { viewModel.toggleUnitSelection(unit.id) },
                                        label = { Text(unit.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = viewModel::organizeWithAi,
                        enabled = !state.isAiLoading && state.spelling.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isAiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("  正在整理…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Text("  AI 自动整理")
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.phonetic,
                        onValueChange = viewModel::onPhoneticChange,
                        label = { Text("音标") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    SectionHeader(title = "词性与词义", onAdd = viewModel::addMeaning)
                }
                itemsIndexed(state.meanings) { index, meaning ->
                    MeaningRow(
                        meaning = meaning,
                        onPosChange = { viewModel.onMeaningChange(index, meaning.copy(pos = it)) },
                        onDefChange = { viewModel.onMeaningChange(index, meaning.copy(definition = it)) },
                        onRemove = { viewModel.removeMeaning(index) },
                        showRemove = state.meanings.size > 1
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.rootExplanation,
                        onValueChange = viewModel::onRootExplanationChange,
                        label = { Text("词根解释") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    SectionHeader(title = "词根拆解", onAdd = viewModel::addDecompositionPart)
                }
                itemsIndexed(state.decomposition) { index, part ->
                    DecompositionPartRow(
                        part = part,
                        onSegmentChange = { viewModel.onDecompositionPartChange(index, part.copy(segment = it)) },
                        onRoleChange = { viewModel.onDecompositionPartChange(index, part.copy(role = it)) },
                        onMeaningChange = { viewModel.onDecompositionPartChange(index, part.copy(meaning = it)) },
                        onRemove = { viewModel.removeDecompositionPart(index) }
                    )
                }

                item {
                    SectionHeader(title = "词形变化", onAdd = viewModel::addInflection)
                }
                itemsIndexed(state.inflections) { index, inflection ->
                    InflectionRow(
                        inflection = inflection,
                        onFormChange = { viewModel.onInflectionChange(index, inflection.copy(form = it)) },
                        onFormTypeChange = { viewModel.onInflectionChange(index, inflection.copy(formType = it)) },
                        onRemove = { viewModel.removeInflection(index) }
                    )
                }

                item {
                    SectionHeader(title = "近义词", onAdd = viewModel::addSynonym)
                }
                itemsIndexed(state.synonyms) { index, syn ->
                    SynonymRow(
                        synonym = syn,
                        onWordChange = { viewModel.onSynonymChange(index, syn.copy(word = it)) },
                        onExplanationChange = { viewModel.onSynonymChange(index, syn.copy(explanation = it)) },
                        onRemove = { viewModel.removeSynonym(index) }
                    )
                }

                item {
                    SectionHeader(title = "形近词", onAdd = viewModel::addSimilarWord)
                }
                itemsIndexed(state.similarWords) { index, sim ->
                    SimilarWordRow(
                        similarWord = sim,
                        onWordChange = { viewModel.onSimilarWordChange(index, sim.copy(word = it)) },
                        onMeaningChange = { viewModel.onSimilarWordChange(index, sim.copy(meaning = it)) },
                        onExplanationChange = { viewModel.onSimilarWordChange(index, sim.copy(explanation = it)) },
                        onRemove = { viewModel.removeSimilarWord(index) }
                    )
                }

                item {
                    SectionHeader(title = "同根词", onAdd = viewModel::addCognate)
                }
                itemsIndexed(state.cognates) { index, cog ->
                    CognateRow(
                        cognate = cog,
                        onWordChange = { viewModel.onCognateChange(index, cog.copy(word = it)) },
                        onMeaningChange = { viewModel.onCognateChange(index, cog.copy(meaning = it)) },
                        onSharedRootChange = { viewModel.onCognateChange(index, cog.copy(sharedRoot = it)) },
                        onRemove = { viewModel.removeCognate(index) }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" 添加")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeaningRow(
    meaning: Meaning,
    onPosChange: (String) -> Unit,
    onDefChange: (String) -> Unit,
    onRemove: () -> Unit,
    showRemove: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(0.3f)
        ) {
            OutlinedTextField(
                value = meaning.pos,
                onValueChange = {
                    onPosChange(it)
                    expanded = true
                },
                label = { Text("词性") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
            )
            val filtered = if (meaning.pos.isBlank()) {
                PartOfSpeech.ALL
            } else {
                PartOfSpeech.ALL.filter { it.contains(meaning.pos, ignoreCase = true) }
            }
            if (filtered.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    filtered.forEach { pos ->
                        DropdownMenuItem(
                            text = { Text(pos) },
                            onClick = {
                                onPosChange(pos)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = meaning.definition,
            onValueChange = onDefChange,
            label = { Text("释义") },
            singleLine = true,
            modifier = Modifier.weight(0.6f)
        )
        if (showRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
    }
}

private val MORPHEME_ROLE_OPTIONS = listOf(
    MorphemeRole.PREFIX to "前缀",
    MorphemeRole.ROOT to "词根",
    MorphemeRole.SUFFIX to "后缀",
    MorphemeRole.STEM to "词干",
    MorphemeRole.LINKING to "连接",
    MorphemeRole.OTHER to "其他"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecompositionPartRow(
    part: DecompositionPart,
    onSegmentChange: (String) -> Unit,
    onRoleChange: (MorphemeRole) -> Unit,
    onMeaningChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val roleLabel = MORPHEME_ROLE_OPTIONS.firstOrNull { it.first == part.role }?.second ?: "其他"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = part.segment,
            onValueChange = onSegmentChange,
            label = { Text("成分") },
            singleLine = true,
            modifier = Modifier.weight(0.25f)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(0.25f)
        ) {
            OutlinedTextField(
                value = roleLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("类型") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MORPHEME_ROLE_OPTIONS.forEach { (role, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onRoleChange(role)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        OutlinedTextField(
            value = part.meaning,
            onValueChange = onMeaningChange,
            label = { Text("含义") },
            singleLine = true,
            modifier = Modifier.weight(0.35f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Close, contentDescription = "删除")
        }
    }
}

@Composable
private fun SynonymRow(
    synonym: SynonymInfo,
    onWordChange: (String) -> Unit,
    onExplanationChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = synonym.word,
            onValueChange = onWordChange,
            label = { Text("近义词") },
            singleLine = true,
            modifier = Modifier.weight(0.35f)
        )
        OutlinedTextField(
            value = synonym.explanation,
            onValueChange = onExplanationChange,
            label = { Text("区分说明") },
            singleLine = true,
            modifier = Modifier.weight(0.55f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Close, contentDescription = "删除")
        }
    }
}

@Composable
private fun SimilarWordRow(
    similarWord: SimilarWordInfo,
    onWordChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onExplanationChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = similarWord.word,
                onValueChange = onWordChange,
                label = { Text("形近词") },
                singleLine = true,
                modifier = Modifier.weight(0.35f)
            )
            OutlinedTextField(
                value = similarWord.meaning,
                onValueChange = onMeaningChange,
                label = { Text("含义") },
                singleLine = true,
                modifier = Modifier.weight(0.55f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
        OutlinedTextField(
            value = similarWord.explanation,
            onValueChange = onExplanationChange,
            label = { Text("区分方法") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CognateRow(
    cognate: CognateInfo,
    onWordChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onSharedRootChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = cognate.word,
                onValueChange = onWordChange,
                label = { Text("同根词") },
                singleLine = true,
                modifier = Modifier.weight(0.35f)
            )
            OutlinedTextField(
                value = cognate.meaning,
                onValueChange = onMeaningChange,
                label = { Text("含义") },
                singleLine = true,
                modifier = Modifier.weight(0.55f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
        OutlinedTextField(
            value = cognate.sharedRoot,
            onValueChange = onSharedRootChange,
            label = { Text("共同词根") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val INFLECTION_TYPE_OPTIONS = listOf(
    "plural" to "复数",
    "past_tense" to "过去式",
    "past_participle" to "过去分词",
    "present_participle" to "现在分词",
    "third_person" to "第三人称",
    "comparative" to "比较级",
    "superlative" to "最高级"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InflectionRow(
    inflection: Inflection,
    onFormChange: (String) -> Unit,
    onFormTypeChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val typeLabel = INFLECTION_TYPE_OPTIONS.firstOrNull { it.first == inflection.formType }?.second ?: inflection.formType.ifBlank { "类型" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = inflection.form,
            onValueChange = onFormChange,
            label = { Text("变形") },
            singleLine = true,
            modifier = Modifier.weight(0.4f)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(0.45f)
        ) {
            OutlinedTextField(
                value = typeLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("类型") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                INFLECTION_TYPE_OPTIONS.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onFormTypeChange(type)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Close, contentDescription = "删除")
        }
    }
}
