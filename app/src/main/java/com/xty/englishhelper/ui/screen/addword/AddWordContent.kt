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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddWordContent(
    state: AddWordUiState,
    isWide: Boolean,
    onSpellingChange: (String) -> Unit,
    onPhoneticChange: (String) -> Unit,
    onRootExplanationChange: (String) -> Unit,
    onToggleUnit: (Long) -> Unit,
    onOrganizeWithAi: () -> Unit,
    onMeaningChange: (Int, com.xty.englishhelper.domain.model.Meaning) -> Unit,
    onAddMeaning: () -> Unit,
    onRemoveMeaning: (Int) -> Unit,
    onDecompositionPartChange: (Int, com.xty.englishhelper.domain.model.DecompositionPart) -> Unit,
    onAddDecompositionPart: () -> Unit,
    onRemoveDecompositionPart: (Int) -> Unit,
    onInflectionChange: (Int, com.xty.englishhelper.domain.model.Inflection) -> Unit,
    onAddInflection: () -> Unit,
    onRemoveInflection: (Int) -> Unit,
    onSynonymChange: (Int, com.xty.englishhelper.domain.model.SynonymInfo) -> Unit,
    onAddSynonym: () -> Unit,
    onRemoveSynonym: (Int) -> Unit,
    onSimilarWordChange: (Int, com.xty.englishhelper.domain.model.SimilarWordInfo) -> Unit,
    onAddSimilarWord: () -> Unit,
    onRemoveSimilarWord: (Int) -> Unit,
    onCognateChange: (Int, com.xty.englishhelper.domain.model.CognateInfo) -> Unit,
    onAddCognate: () -> Unit,
    onRemoveCognate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isWide) {
        WideContent(
            state = state,
            onSpellingChange = onSpellingChange,
            onPhoneticChange = onPhoneticChange,
            onRootExplanationChange = onRootExplanationChange,
            onToggleUnit = onToggleUnit,
            onOrganizeWithAi = onOrganizeWithAi,
            onMeaningChange = onMeaningChange,
            onAddMeaning = onAddMeaning,
            onRemoveMeaning = onRemoveMeaning,
            onDecompositionPartChange = onDecompositionPartChange,
            onAddDecompositionPart = onAddDecompositionPart,
            onRemoveDecompositionPart = onRemoveDecompositionPart,
            onInflectionChange = onInflectionChange,
            onAddInflection = onAddInflection,
            onRemoveInflection = onRemoveInflection,
            onSynonymChange = onSynonymChange,
            onAddSynonym = onAddSynonym,
            onRemoveSynonym = onRemoveSynonym,
            onSimilarWordChange = onSimilarWordChange,
            onAddSimilarWord = onAddSimilarWord,
            onRemoveSimilarWord = onRemoveSimilarWord,
            onCognateChange = onCognateChange,
            onAddCognate = onAddCognate,
            onRemoveCognate = onRemoveCognate,
            modifier = modifier
        )
    } else {
        CompactContent(
            state = state,
            onSpellingChange = onSpellingChange,
            onPhoneticChange = onPhoneticChange,
            onRootExplanationChange = onRootExplanationChange,
            onToggleUnit = onToggleUnit,
            onOrganizeWithAi = onOrganizeWithAi,
            onMeaningChange = onMeaningChange,
            onAddMeaning = onAddMeaning,
            onRemoveMeaning = onRemoveMeaning,
            onDecompositionPartChange = onDecompositionPartChange,
            onAddDecompositionPart = onAddDecompositionPart,
            onRemoveDecompositionPart = onRemoveDecompositionPart,
            onInflectionChange = onInflectionChange,
            onAddInflection = onAddInflection,
            onRemoveInflection = onRemoveInflection,
            onSynonymChange = onSynonymChange,
            onAddSynonym = onAddSynonym,
            onRemoveSynonym = onRemoveSynonym,
            onSimilarWordChange = onSimilarWordChange,
            onAddSimilarWord = onAddSimilarWord,
            onRemoveSimilarWord = onRemoveSimilarWord,
            onCognateChange = onCognateChange,
            onAddCognate = onAddCognate,
            onRemoveCognate = onRemoveCognate,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WideContent(
    state: AddWordUiState,
    onSpellingChange: (String) -> Unit,
    onPhoneticChange: (String) -> Unit,
    onRootExplanationChange: (String) -> Unit,
    onToggleUnit: (Long) -> Unit,
    onOrganizeWithAi: () -> Unit,
    onMeaningChange: (Int, com.xty.englishhelper.domain.model.Meaning) -> Unit,
    onAddMeaning: () -> Unit,
    onRemoveMeaning: (Int) -> Unit,
    onDecompositionPartChange: (Int, com.xty.englishhelper.domain.model.DecompositionPart) -> Unit,
    onAddDecompositionPart: () -> Unit,
    onRemoveDecompositionPart: (Int) -> Unit,
    onInflectionChange: (Int, com.xty.englishhelper.domain.model.Inflection) -> Unit,
    onAddInflection: () -> Unit,
    onRemoveInflection: (Int) -> Unit,
    onSynonymChange: (Int, com.xty.englishhelper.domain.model.SynonymInfo) -> Unit,
    onAddSynonym: () -> Unit,
    onRemoveSynonym: (Int) -> Unit,
    onSimilarWordChange: (Int, com.xty.englishhelper.domain.model.SimilarWordInfo) -> Unit,
    onAddSimilarWord: () -> Unit,
    onRemoveSimilarWord: (Int) -> Unit,
    onCognateChange: (Int, com.xty.englishhelper.domain.model.CognateInfo) -> Unit,
    onAddCognate: () -> Unit,
    onRemoveCognate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    EhMaxWidthContainer(
        modifier = modifier.fillMaxSize(),
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
                BasicFields(
                    state = state,
                    onSpellingChange = onSpellingChange,
                    onPhoneticChange = onPhoneticChange,
                    onRootExplanationChange = onRootExplanationChange,
                    onToggleUnit = onToggleUnit,
                    onOrganizeWithAi = onOrganizeWithAi,
                    onMeaningChange = onMeaningChange,
                    onAddMeaning = onAddMeaning,
                    onRemoveMeaning = onRemoveMeaning
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
                RelationshipFields(
                    state = state,
                    onDecompositionPartChange = onDecompositionPartChange,
                    onAddDecompositionPart = onAddDecompositionPart,
                    onRemoveDecompositionPart = onRemoveDecompositionPart,
                    onInflectionChange = onInflectionChange,
                    onAddInflection = onAddInflection,
                    onRemoveInflection = onRemoveInflection,
                    onSynonymChange = onSynonymChange,
                    onAddSynonym = onAddSynonym,
                    onRemoveSynonym = onRemoveSynonym,
                    onSimilarWordChange = onSimilarWordChange,
                    onAddSimilarWord = onAddSimilarWord,
                    onRemoveSimilarWord = onRemoveSimilarWord,
                    onCognateChange = onCognateChange,
                    onAddCognate = onAddCognate,
                    onRemoveCognate = onRemoveCognate
                )
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CompactContent(
    state: AddWordUiState,
    onSpellingChange: (String) -> Unit,
    onPhoneticChange: (String) -> Unit,
    onRootExplanationChange: (String) -> Unit,
    onToggleUnit: (Long) -> Unit,
    onOrganizeWithAi: () -> Unit,
    onMeaningChange: (Int, com.xty.englishhelper.domain.model.Meaning) -> Unit,
    onAddMeaning: () -> Unit,
    onRemoveMeaning: (Int) -> Unit,
    onDecompositionPartChange: (Int, com.xty.englishhelper.domain.model.DecompositionPart) -> Unit,
    onAddDecompositionPart: () -> Unit,
    onRemoveDecompositionPart: (Int) -> Unit,
    onInflectionChange: (Int, com.xty.englishhelper.domain.model.Inflection) -> Unit,
    onAddInflection: () -> Unit,
    onRemoveInflection: (Int) -> Unit,
    onSynonymChange: (Int, com.xty.englishhelper.domain.model.SynonymInfo) -> Unit,
    onAddSynonym: () -> Unit,
    onRemoveSynonym: (Int) -> Unit,
    onSimilarWordChange: (Int, com.xty.englishhelper.domain.model.SimilarWordInfo) -> Unit,
    onAddSimilarWord: () -> Unit,
    onRemoveSimilarWord: (Int) -> Unit,
    onCognateChange: (Int, com.xty.englishhelper.domain.model.CognateInfo) -> Unit,
    onAddCognate: () -> Unit,
    onRemoveCognate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.spelling,
                onValueChange = onSpellingChange,
                label = { Text("单词拼写") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.availableUnits.isNotEmpty()) {
            item {
                UnitSelector(state = state, onToggleUnit = onToggleUnit)
            }
        }

        item {
            AiOrganizeButton(state = state, onClick = onOrganizeWithAi)
        }

        item {
            OutlinedTextField(
                value = state.phonetic,
                onValueChange = onPhoneticChange,
                label = { Text("音标") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            SectionHeader(title = "词性与词义", onAdd = onAddMeaning)
        }
        itemsIndexed(state.meanings) { index, meaning ->
            MeaningRow(
                meaning = meaning,
                onPosChange = { onMeaningChange(index, meaning.copy(pos = it)) },
                onDefChange = { onMeaningChange(index, meaning.copy(definition = it)) },
                onRemove = { onRemoveMeaning(index) },
                showRemove = state.meanings.size > 1
            )
        }

        item {
            OutlinedTextField(
                value = state.rootExplanation,
                onValueChange = onRootExplanationChange,
                label = { Text("词根解释") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            SectionHeader(title = "词根拆解", onAdd = onAddDecompositionPart)
        }
        itemsIndexed(state.decomposition) { index, part ->
            DecompositionPartRow(
                part = part,
                onSegmentChange = { onDecompositionPartChange(index, part.copy(segment = it)) },
                onRoleChange = { onDecompositionPartChange(index, part.copy(role = it)) },
                onMeaningChange = { onDecompositionPartChange(index, part.copy(meaning = it)) },
                onRemove = { onRemoveDecompositionPart(index) }
            )
        }

        item {
            SectionHeader(title = "词形变化", onAdd = onAddInflection)
        }
        itemsIndexed(state.inflections) { index, inflection ->
            InflectionRow(
                inflection = inflection,
                onFormChange = { onInflectionChange(index, inflection.copy(form = it)) },
                onFormTypeChange = { onInflectionChange(index, inflection.copy(formType = it)) },
                onRemove = { onRemoveInflection(index) }
            )
        }

        item {
            SectionHeader(title = "近义词", onAdd = onAddSynonym)
        }
        itemsIndexed(state.synonyms) { index, syn ->
            SynonymRow(
                synonym = syn,
                onWordChange = { onSynonymChange(index, syn.copy(word = it)) },
                onExplanationChange = { onSynonymChange(index, syn.copy(explanation = it)) },
                onRemove = { onRemoveSynonym(index) }
            )
        }

        item {
            SectionHeader(title = "形近词", onAdd = onAddSimilarWord)
        }
        itemsIndexed(state.similarWords) { index, sim ->
            SimilarWordRow(
                similarWord = sim,
                onWordChange = { onSimilarWordChange(index, sim.copy(word = it)) },
                onMeaningChange = { onSimilarWordChange(index, sim.copy(meaning = it)) },
                onExplanationChange = { onSimilarWordChange(index, sim.copy(explanation = it)) },
                onRemove = { onRemoveSimilarWord(index) }
            )
        }

        item {
            SectionHeader(title = "同根词", onAdd = onAddCognate)
        }
        itemsIndexed(state.cognates) { index, cog ->
            CognateRow(
                cognate = cog,
                onWordChange = { onCognateChange(index, cog.copy(word = it)) },
                onMeaningChange = { onCognateChange(index, cog.copy(meaning = it)) },
                onSharedRootChange = { onCognateChange(index, cog.copy(sharedRoot = it)) },
                onRemove = { onRemoveCognate(index) }
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicFields(
    state: AddWordUiState,
    onSpellingChange: (String) -> Unit,
    onPhoneticChange: (String) -> Unit,
    onRootExplanationChange: (String) -> Unit,
    onToggleUnit: (Long) -> Unit,
    onOrganizeWithAi: () -> Unit,
    onMeaningChange: (Int, com.xty.englishhelper.domain.model.Meaning) -> Unit,
    onAddMeaning: () -> Unit,
    onRemoveMeaning: (Int) -> Unit
) {
    OutlinedTextField(
        value = state.spelling,
        onValueChange = onSpellingChange,
        label = { Text("单词拼写") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    if (state.availableUnits.isNotEmpty()) {
        UnitSelector(state = state, onToggleUnit = onToggleUnit)
    }

    AiOrganizeButton(state = state, onClick = onOrganizeWithAi)

    OutlinedTextField(
        value = state.phonetic,
        onValueChange = onPhoneticChange,
        label = { Text("音标") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    SectionHeader(title = "词性与词义", onAdd = onAddMeaning)
    state.meanings.forEachIndexed { index, meaning ->
        MeaningRow(
            meaning = meaning,
            onPosChange = { onMeaningChange(index, meaning.copy(pos = it)) },
            onDefChange = { onMeaningChange(index, meaning.copy(definition = it)) },
            onRemove = { onRemoveMeaning(index) },
            showRemove = state.meanings.size > 1
        )
    }

    OutlinedTextField(
        value = state.rootExplanation,
        onValueChange = onRootExplanationChange,
        label = { Text("词根解释") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RelationshipFields(
    state: AddWordUiState,
    onDecompositionPartChange: (Int, com.xty.englishhelper.domain.model.DecompositionPart) -> Unit,
    onAddDecompositionPart: () -> Unit,
    onRemoveDecompositionPart: (Int) -> Unit,
    onInflectionChange: (Int, com.xty.englishhelper.domain.model.Inflection) -> Unit,
    onAddInflection: () -> Unit,
    onRemoveInflection: (Int) -> Unit,
    onSynonymChange: (Int, com.xty.englishhelper.domain.model.SynonymInfo) -> Unit,
    onAddSynonym: () -> Unit,
    onRemoveSynonym: (Int) -> Unit,
    onSimilarWordChange: (Int, com.xty.englishhelper.domain.model.SimilarWordInfo) -> Unit,
    onAddSimilarWord: () -> Unit,
    onRemoveSimilarWord: (Int) -> Unit,
    onCognateChange: (Int, com.xty.englishhelper.domain.model.CognateInfo) -> Unit,
    onAddCognate: () -> Unit,
    onRemoveCognate: (Int) -> Unit
) {
    SectionHeader(title = "词根拆解", onAdd = onAddDecompositionPart)
    state.decomposition.forEachIndexed { index, part ->
        DecompositionPartRow(
            part = part,
            onSegmentChange = { onDecompositionPartChange(index, part.copy(segment = it)) },
            onRoleChange = { onDecompositionPartChange(index, part.copy(role = it)) },
            onMeaningChange = { onDecompositionPartChange(index, part.copy(meaning = it)) },
            onRemove = { onRemoveDecompositionPart(index) }
        )
    }

    SectionHeader(title = "词形变化", onAdd = onAddInflection)
    state.inflections.forEachIndexed { index, inflection ->
        InflectionRow(
            inflection = inflection,
            onFormChange = { onInflectionChange(index, inflection.copy(form = it)) },
            onFormTypeChange = { onInflectionChange(index, inflection.copy(formType = it)) },
            onRemove = { onRemoveInflection(index) }
        )
    }

    SectionHeader(title = "近义词", onAdd = onAddSynonym)
    state.synonyms.forEachIndexed { index, syn ->
        SynonymRow(
            synonym = syn,
            onWordChange = { onSynonymChange(index, syn.copy(word = it)) },
            onExplanationChange = { onSynonymChange(index, syn.copy(explanation = it)) },
            onRemove = { onRemoveSynonym(index) }
        )
    }

    SectionHeader(title = "形近词", onAdd = onAddSimilarWord)
    state.similarWords.forEachIndexed { index, sim ->
        SimilarWordRow(
            similarWord = sim,
            onWordChange = { onSimilarWordChange(index, sim.copy(word = it)) },
            onMeaningChange = { onSimilarWordChange(index, sim.copy(meaning = it)) },
            onExplanationChange = { onSimilarWordChange(index, sim.copy(explanation = it)) },
            onRemove = { onRemoveSimilarWord(index) }
        )
    }

    SectionHeader(title = "同根词", onAdd = onAddCognate)
    state.cognates.forEachIndexed { index, cog ->
        CognateRow(
            cognate = cog,
            onWordChange = { onCognateChange(index, cog.copy(word = it)) },
            onMeaningChange = { onCognateChange(index, cog.copy(meaning = it)) },
            onSharedRootChange = { onCognateChange(index, cog.copy(sharedRoot = it)) },
            onRemove = { onRemoveCognate(index) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitSelector(state: AddWordUiState, onToggleUnit: (Long) -> Unit) {
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
                    onClick = { onToggleUnit(unit.id) },
                    label = { Text(unit.name) }
                )
            }
        }
    }
}

@Composable
private fun AiOrganizeButton(state: AddWordUiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
