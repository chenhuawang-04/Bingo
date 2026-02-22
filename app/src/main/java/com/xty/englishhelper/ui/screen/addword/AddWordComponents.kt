package com.xty.englishhelper.ui.screen.addword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.PartOfSpeech
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo

@Composable
internal fun SectionHeader(title: String, onAdd: () -> Unit) {
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
internal fun MeaningRow(
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

internal val MORPHEME_ROLE_OPTIONS = listOf(
    MorphemeRole.PREFIX to "前缀",
    MorphemeRole.ROOT to "词根",
    MorphemeRole.SUFFIX to "后缀",
    MorphemeRole.STEM to "词干",
    MorphemeRole.LINKING to "连接",
    MorphemeRole.OTHER to "其他"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DecompositionPartRow(
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
internal fun SynonymRow(
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
internal fun SimilarWordRow(
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
internal fun CognateRow(
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

internal val INFLECTION_TYPE_OPTIONS = listOf(
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
internal fun InflectionRow(
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
