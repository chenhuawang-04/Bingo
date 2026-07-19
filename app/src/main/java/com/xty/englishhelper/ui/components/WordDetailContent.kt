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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.model.WordPhraseWithTags
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.model.WordCluster
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.repository.WordExample
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview
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
    examples: List<WordExample> = emptyList(),
    onArticleClick: ((articleId: Long, sentenceId: Long) -> Unit)? = null,
    pools: List<WordPool> = emptyList(),
    clusters: List<WordCluster> = emptyList(),
    clusterReviews: List<WordClusterReview> = emptyList(),
    edgePreviews: List<WordEdgeNeighborPreview> = emptyList(),
    phrases: List<WordPhraseWithTags> = emptyList(),
    cloudExampleSource: CloudExampleSource = CloudExampleSource.CAMBRIDGE,
    cloudExamples: List<CloudWordExample> = emptyList(),
    cloudExamplesLoading: Boolean = false,
    cloudExamplesError: String? = null,
    onCloudExampleSourceSelected: (CloudExampleSource) -> Unit = {},
    detailsLoading: Boolean = false,
    detailsError: String? = null,
    onRetryDetails: () -> Unit = {},
    onClusterReview: ((Long) -> Unit)? = null,
    showCloudExamples: Boolean = true,
    showSpellingHeader: Boolean = false
) {
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()

    if (isWide) {
        Row(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showSpellingHeader) {
                    item { Text(word.spelling, style = MaterialTheme.typography.headlineMedium) }
                }
                basicWordItems(word)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presentationStatusItems(detailsLoading, detailsError, onRetryDetails)
                relatedWordItems(
                    word = word,
                    linkedWordIds = linkedWordIds,
                    associatedWords = associatedWords,
                    pools = pools,
                    clusters = clusters,
                    clusterReviews = clusterReviews,
                    edgePreviews = edgePreviews,
                    phrases = phrases,
                    examples = examples,
                    onWordClick = onWordClick,
                    onArticleClick = onArticleClick,
                    cloudExampleSource = cloudExampleSource,
                    cloudExamples = cloudExamples,
                    cloudExamplesLoading = cloudExamplesLoading,
                    cloudExamplesError = cloudExamplesError,
                    onCloudExampleSourceSelected = onCloudExampleSourceSelected,
                    onClusterReview = onClusterReview,
                    showCloudExamples = showCloudExamples
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            unifiedWordDetailItems(
                word = word,
                showSpellingHeader = showSpellingHeader,
                linkedWordIds = linkedWordIds,
                associatedWords = associatedWords,
                pools = pools,
                clusters = clusters,
                clusterReviews = clusterReviews,
                edgePreviews = edgePreviews,
                phrases = phrases,
                examples = examples,
                onWordClick = onWordClick,
                onArticleClick = onArticleClick,
                cloudExampleSource = cloudExampleSource,
                cloudExamples = cloudExamples,
                cloudExamplesLoading = cloudExamplesLoading,
                cloudExamplesError = cloudExamplesError,
                onCloudExampleSourceSelected = onCloudExampleSourceSelected,
                detailsLoading = detailsLoading,
                detailsError = detailsError,
                onRetryDetails = onRetryDetails,
                onClusterReview = onClusterReview,
                showCloudExamples = showCloudExamples
            )
        }
    }
}

fun LazyListScope.unifiedWordDetailItems(
    word: WordDetails,
    linkedWordIds: Map<String, Long> = emptyMap(),
    associatedWords: List<AssociatedWordInfo> = emptyList(),
    pools: List<WordPool> = emptyList(),
    clusters: List<WordCluster> = emptyList(),
    clusterReviews: List<WordClusterReview> = emptyList(),
    edgePreviews: List<WordEdgeNeighborPreview> = emptyList(),
    phrases: List<WordPhraseWithTags> = emptyList(),
    examples: List<WordExample> = emptyList(),
    onWordClick: (Long, Long) -> Unit = { _, _ -> },
    onArticleClick: ((Long, Long) -> Unit)? = null,
    cloudExampleSource: CloudExampleSource = CloudExampleSource.CAMBRIDGE,
    cloudExamples: List<CloudWordExample> = emptyList(),
    cloudExamplesLoading: Boolean = false,
    cloudExamplesError: String? = null,
    onCloudExampleSourceSelected: (CloudExampleSource) -> Unit = {},
    detailsLoading: Boolean = false,
    detailsError: String? = null,
    onRetryDetails: () -> Unit = {},
    onClusterReview: ((Long) -> Unit)? = null,
    showCloudExamples: Boolean = true,
    showSpellingHeader: Boolean = true
) {
    if (showSpellingHeader) {
        item {
            Text(word.spelling, style = MaterialTheme.typography.headlineMedium)
        }
    }
    basicWordItems(word)
    presentationStatusItems(detailsLoading, detailsError, onRetryDetails)
    relatedWordItems(
        word = word,
        linkedWordIds = linkedWordIds,
        associatedWords = associatedWords,
        pools = pools,
        clusters = clusters,
        clusterReviews = clusterReviews,
        edgePreviews = edgePreviews,
        phrases = phrases,
        examples = examples,
        onWordClick = onWordClick,
        onArticleClick = onArticleClick,
        cloudExampleSource = cloudExampleSource,
        cloudExamples = cloudExamples,
        cloudExamplesLoading = cloudExamplesLoading,
        cloudExamplesError = cloudExamplesError,
        onCloudExampleSourceSelected = onCloudExampleSourceSelected,
        onClusterReview = onClusterReview,
        showCloudExamples = showCloudExamples
    )
}

private fun LazyListScope.presentationStatusItems(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    if (isLoading) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在加载词簇、词池和本地例句…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (error != null) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.basicWordItems(word: WordDetails) {
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
            WordDetailSection(title = stringResource(R.string.word_meanings)) {
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
            WordDetailSection(title = stringResource(R.string.word_root_explanation)) {
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
            WordDetailSection(title = stringResource(R.string.word_inflections)) {
                InflectionsDisplay(inflections = word.inflections)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.relatedWordItems(
    word: WordDetails,
    linkedWordIds: Map<String, Long>,
    associatedWords: List<AssociatedWordInfo>,
    pools: List<WordPool>,
    clusters: List<WordCluster>,
    clusterReviews: List<WordClusterReview>,
    edgePreviews: List<WordEdgeNeighborPreview>,
    phrases: List<WordPhraseWithTags>,
    examples: List<WordExample>,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onArticleClick: ((articleId: Long, sentenceId: Long) -> Unit)?,
    cloudExampleSource: CloudExampleSource,
    cloudExamples: List<CloudWordExample>,
    cloudExamplesLoading: Boolean,
    cloudExamplesError: String?,
    onCloudExampleSourceSelected: (CloudExampleSource) -> Unit,
    onClusterReview: ((Long) -> Unit)?,
    showCloudExamples: Boolean
) {
    if (clusters.isNotEmpty()) {
        item {
            WordDetailSection(title = "词簇") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    clusters.forEach { cluster ->
                        val review = clusterReviews.firstOrNull { it.cluster.id == cluster.id }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${cluster.name} · ${cluster.memberCount} 词",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (onClusterReview != null && cluster.memberCount > 1) {
                                    TextButton(onClick = { onClusterReview(cluster.id) }) {
                                        Text("关联背诵")
                                    }
                                }
                            }
                            if (review != null && review.words.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    review.words.forEach { member ->
                                        AssistChip(
                                            onClick = { onWordClick(member.id, member.dictionaryId) },
                                            label = { Text(member.spelling) }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "暂无其他成员",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (edgePreviews.isNotEmpty()) {
        item {
            WordDetailSection(title = "关系网络") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    edgePreviews.forEach { edge ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWordClick(edge.neighborId, word.dictionaryId) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                edge.spelling,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                edge.edgeTypes.joinToString(" · ") { it.label },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (word.synonyms.isNotEmpty()) {
        item {
            WordDetailSection(title = stringResource(R.string.word_synonyms)) {
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
            WordDetailSection(title = stringResource(R.string.word_similar_words)) {
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
                                    text = stringResource(R.string.word_distinction_format, sim.explanation),
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
            WordDetailSection(title = stringResource(R.string.word_cognates)) {
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
                                    text = stringResource(R.string.word_root_prefix, cog.sharedRoot),
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
            WordDetailSection(title = stringResource(R.string.word_associated_words)) {
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
                                    .clickable { onWordClick(assoc.wordId, word.dictionaryId) }
                                    .padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                assoc.commonSegments.forEach { seg ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            seg,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

    if (phrases.isNotEmpty()) {
        item {
            WordDetailSection(title = stringResource(R.string.word_phrases)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    phrases.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = item.phrase.phrase,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (item.phrase.meaning.isNotBlank()) {
                                Text(
                                    text = item.phrase.meaning,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (item.phrase.example.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.word_phrase_example, item.phrase.example),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.phrase.usageNote.isNotBlank()) {
                                Text(
                                    text = item.phrase.usageNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    item.tags.forEach { tag ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                tag.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

    pools.forEach { pool ->
        item {
            val label = if (pool.strategy == "QUALITY_FIRST") stringResource(R.string.word_quality_pool) else stringResource(R.string.word_related_pool)
            WordDetailSection(title = label) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pool.members.filterNot { it.id == word.id }.forEach { member ->
                        AssistChip(
                            onClick = { onWordClick(member.id, member.dictionaryId) },
                            label = {
                                Text(
                                    member.spelling,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (examples.isNotEmpty()) {
        item {
            WordDetailSection(title = stringResource(R.string.word_article_examples)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEach { example ->
                        val articleId = example.sourceArticleId
                        val sentenceId = example.sourceSentenceId
                        val cardModifier = if (onArticleClick != null && articleId != null && sentenceId != null) {
                            Modifier.fillMaxWidth().clickable { onArticleClick(articleId, sentenceId) }
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        Card(modifier = cardModifier) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        example.sourceLabel?.takeIf { it.isNotBlank() }
                                            ?: if (example.sourceType == WordExampleSourceType.ARTICLE) "文章提取" else "手动例句",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (onArticleClick != null && articleId != null && sentenceId != null) {
                                        Text(
                                            "查看原文",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
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

    if (showCloudExamples) {
        item {
            CloudExamplesSection(
                selectedSource = cloudExampleSource,
                examples = cloudExamples,
                isLoading = cloudExamplesLoading,
                error = cloudExamplesError,
                onSourceSelected = onCloudExampleSourceSelected
            )
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
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "${inflectionTypeLabel(inflection.formType)}: ${inflection.form}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
