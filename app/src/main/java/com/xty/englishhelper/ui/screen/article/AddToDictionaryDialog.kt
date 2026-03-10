package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit

@Composable
fun AddToDictionaryDialog(
    word: String,
    dictionaries: List<Dictionary>,
    onLoadUnits: (suspend (dictionaryId: Long) -> List<StudyUnit>)? = null,
    onConfirm: (dictionaryId: Long, unitId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDictId by remember { mutableLongStateOf(dictionaries.firstOrNull()?.id ?: 0L) }
    var selectedUnitId by remember { mutableStateOf<Long?>(null) }
    var units by remember { mutableStateOf<List<StudyUnit>>(emptyList()) }
    var isLoadingUnits by remember { mutableStateOf(false) }
    var unitLoadError by remember { mutableStateOf<String?>(null) }

    // Auto-select first dictionary when list arrives asynchronously
    LaunchedEffect(dictionaries) {
        if (selectedDictId == 0L && dictionaries.isNotEmpty()) {
            selectedDictId = dictionaries.first().id
        }
    }

    // Load units when dictionary selection changes
    LaunchedEffect(selectedDictId) {
        if (selectedDictId != 0L && onLoadUnits != null) {
            isLoadingUnits = true
            unitLoadError = null
            try {
                units = onLoadUnits(selectedDictId)
            } catch (_: Exception) {
                units = emptyList()
                unitLoadError = "加载单元失败"
            } finally {
                isLoadingUnits = false
            }
        } else {
            units = emptyList()
            unitLoadError = null
        }
        selectedUnitId = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("将「$word」加入词典")
        },
        text = {
            Column {
                if (dictionaries.isEmpty()) {
                    Text(
                        "暂无辞书，请先创建一本辞书",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "选择辞书",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))

                    LazyColumn(modifier = Modifier.height((dictionaries.size.coerceAtMost(4) * 48).dp)) {
                        items(dictionaries) { dict ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDictId = dict.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDictId == dict.id,
                                    onClick = { selectedDictId = dict.id }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(dict.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (selectedDictId != 0L && onLoadUnits != null) {
                        if (isLoadingUnits) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("加载单元中…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (unitLoadError != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                unitLoadError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else if (units.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                "选择单元（可选）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))

                            LazyColumn(modifier = Modifier.height(((units.size + 1).coerceAtMost(4) * 48).dp)) {
                                // "No unit" option
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedUnitId = null }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedUnitId == null,
                                            onClick = { selectedUnitId = null }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("不指定", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                items(units) { unit ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedUnitId = unit.id }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedUnitId == unit.id,
                                            onClick = { selectedUnitId = unit.id }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(unit.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDictId, selectedUnitId) },
                enabled = selectedDictId != 0L && dictionaries.isNotEmpty()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
