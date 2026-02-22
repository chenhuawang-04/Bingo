package com.xty.englishhelper.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailContent(
    word: WordDetails,
    associatedWords: List<AssociatedWordInfo>,
    linkedWordIds: Map<String, Long>,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    modifier: Modifier = Modifier,
    examples: List<com.xty.englishhelper.domain.repository.WordExample> = emptyList(),
    onArticleClick: (articleId: Long, sentenceId: Long) -> Unit = { _, _ -> }
) {
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()

    if (isWide) {
        // Two-column layout on large screens
        Row(modifier = modifier.fillMaxSize()) {
            // Left column: basic info
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (word.phonetic.isNotBlank()) {
                    item {
                        Text(
                            text = word.phonetic,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (word.meanings.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "词性与词义") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                word.meanings.forEach { meaning ->
                                    DetailRow(label = meaning.pos, value = meaning.definition)
                                }
                            }
                        }
                    }
                }
                if (word.decomposition.isNotEmpty() || word.rootExplanation.isNotBlank()) {
                    item {
                        WordDetailSection(title = "词根解释") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (word.decomposition.isNotEmpty()) {
                                    DecompositionDisplay(parts = word.decomposition)
                                }
                                if (word.rootExplanation.isNotBlank()) {
                                    if (word.decomposition.isNotEmpty()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    Text(
                                        text = word.rootExplanation,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                if (word.inflections.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "词形变化") {
                            InflectionsDisplay(inflections = word.inflections)
                        }
                    }
                }
            }
            // Right column: related words
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (word.synonyms.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "近义词") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                word.synonyms.forEach { syn ->
                                    ClickableWordRow(
                                        word = syn.word,
                                        detail = syn.explanation,
                                        linkedWordIds = linkedWordIds,
                                        dictionaryId = word.dictionaryId,
                                        onWordClick = onWordClick
                                    )
                                }
                            }
                        }
                    }
                }
                if (word.similarWords.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "形近词") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                word.similarWords.forEach { sim ->
                                    Column {
                                        ClickableWordRow(
                                            word = sim.word,
                                            detail = sim.meaning,
                                            linkedWordIds = linkedWordIds,
                                            dictionaryId = word.dictionaryId,
                                            onWordClick = onWordClick
                                        )
                                        if (sim.explanation.isNotBlank()) {
                                            Text(
                                                text = "区分：${sim.explanation}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (word.cognates.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "同根词") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                word.cognates.forEach { cog ->
                                    Column {
                                        ClickableWordRow(
                                            word = cog.word,
                                            detail = cog.meaning,
                                            linkedWordIds = linkedWordIds,
                                            dictionaryId = word.dictionaryId,
                                            onWordClick = onWordClick
                                        )
                                        if (cog.sharedRoot.isNotBlank()) {
                                            Text(
                                                text = "词根：${cog.sharedRoot}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (associatedWords.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "联想词") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                associatedWords.forEach { assoc ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = assoc.spelling,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            modifier = Modifier
                                                .clickable {
                                                    onWordClick(assoc.wordId, word.dictionaryId)
                                                }
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            assoc.commonSegments.forEach { seg ->
                                                AssistChip(
                                                    onClick = {},
                                                    label = {
                                                        Text(
                                                            seg,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (examples.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "文章例句") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                examples.forEach { example ->
                                    androidx.compose.material3.Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onArticleClick(
                                                    example.sourceArticleId ?: 0L,
                                                    example.sourceSentenceId ?: 0L
                                                )
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            if (!example.sourceLabel.isNullOrBlank()) {
                                                Text(
                                                    example.sourceLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                example.sentence,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Single-column layout (compact)
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (word.phonetic.isNotBlank()) {
                item {
                    Text(
                        text = word.phonetic,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (word.meanings.isNotEmpty()) {
                item {
                    WordDetailSection(title = "词性与词义") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            word.meanings.forEach { meaning ->
                                DetailRow(label = meaning.pos, value = meaning.definition)
                            }
                        }
                    }
                }
            }
            if (word.decomposition.isNotEmpty() || word.rootExplanation.isNotBlank()) {
                item {
                    WordDetailSection(title = "词根解释") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (word.decomposition.isNotEmpty()) {
                                DecompositionDisplay(parts = word.decomposition)
                            }
                            if (word.rootExplanation.isNotBlank()) {
                                if (word.decomposition.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                Text(
                                    text = word.rootExplanation,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            if (word.inflections.isNotEmpty()) {
                item {
                    WordDetailSection(title = "词形变化") {
                        InflectionsDisplay(inflections = word.inflections)
                    }
                }
            }
            if (word.synonyms.isNotEmpty()) {
                item {
                    WordDetailSection(title = "近义词") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            word.synonyms.forEach { syn ->
                                ClickableWordRow(
                                    word = syn.word,
                                    detail = syn.explanation,
                                    linkedWordIds = linkedWordIds,
                                    dictionaryId = word.dictionaryId,
                                    onWordClick = onWordClick
                                )
                            }
                        }
                    }
                }
            }
            if (word.similarWords.isNotEmpty()) {
                item {
                    WordDetailSection(title = "形近词") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            word.similarWords.forEach { sim ->
                                Column {
                                    ClickableWordRow(
                                        word = sim.word,
                                        detail = sim.meaning,
                                        linkedWordIds = linkedWordIds,
                                        dictionaryId = word.dictionaryId,
                                        onWordClick = onWordClick
                                    )
                                    if (sim.explanation.isNotBlank()) {
                                        Text(
                                            text = "区分：${sim.explanation}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (word.cognates.isNotEmpty()) {
                item {
                    WordDetailSection(title = "同根词") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            word.cognates.forEach { cog ->
                                Column {
                                    ClickableWordRow(
                                        word = cog.word,
                                        detail = cog.meaning,
                                        linkedWordIds = linkedWordIds,
                                        dictionaryId = word.dictionaryId,
                                        onWordClick = onWordClick
                                    )
                                    if (cog.sharedRoot.isNotBlank()) {
                                        Text(
                                            text = "词根：${cog.sharedRoot}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (associatedWords.isNotEmpty()) {
                item {
                    WordDetailSection(title = "联想词") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            associatedWords.forEach { assoc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = assoc.spelling,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                onWordClick(assoc.wordId, word.dictionaryId)
                                            }
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        assoc.commonSegments.forEach { seg ->
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        seg,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (examples.isNotEmpty()) {
                item {
                    WordDetailSection(title = "文章例句") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            examples.forEach { example ->
                                androidx.compose.material3.Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onArticleClick(
                                                example.sourceArticleId ?: 0L,
                                                example.sourceSentenceId ?: 0L
                                            )
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (!example.sourceLabel.isNullOrBlank()) {
                                            Text(
                                                example.sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            example.sentence,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DecompositionDisplay(parts: List<DecompositionPart>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = roleLabel(part.role),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (part.role == MorphemeRole.ROOT) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = part.segment,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (part.role == MorphemeRole.ROOT) FontWeight.Bold else FontWeight.Normal,
                        color = if (part.role == MorphemeRole.ROOT) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                )
                if (part.meaning.isNotBlank()) {
                    Text(
                        text = part.meaning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClickableWordRow(
    word: String,
    detail: String,
    linkedWordIds: Map<String, Long>,
    dictionaryId: Long,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit
) {
    val normalized = word.trim().lowercase()
    val linkedId = linkedWordIds[normalized]

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (linkedId != null) {
            Text(
                text = word,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                ),
                modifier = Modifier
                    .clickable { onWordClick(linkedId, dictionaryId) }
                    .padding(top = 2.dp)
            )
        } else {
            Text(
                text = word,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun roleLabel(role: MorphemeRole): String = when (role) {
    MorphemeRole.PREFIX -> "前缀"
    MorphemeRole.ROOT -> "词根"
    MorphemeRole.SUFFIX -> "后缀"
    MorphemeRole.STEM -> "词干"
    MorphemeRole.LINKING -> "连接"
    MorphemeRole.OTHER -> "其他"
}

private fun inflectionTypeLabel(formType: String): String = when (formType) {
    "plural" -> "复数"
    "past_tense" -> "过去式"
    "past_participle" -> "过去分词"
    "present_participle" -> "现在分词"
    "third_person" -> "第三人称"
    "comparative" -> "比较级"
    "superlative" -> "最高级"
    else -> formType
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InflectionsDisplay(inflections: List<Inflection>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        inflections.forEach { inflection ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        "${inflectionTypeLabel(inflection.formType)}: ${inflection.form}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}
