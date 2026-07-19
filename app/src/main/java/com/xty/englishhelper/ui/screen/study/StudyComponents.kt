package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.WordPhraseWithTags
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.model.WordCluster
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.repository.WordExample
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview
import com.xty.englishhelper.ui.components.unifiedWordDetailItems

@Composable
internal fun StatRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor
        )
    }
}

internal fun LazyListScope.wordDetailItems(
    word: WordDetails,
    linkedWordIds: Map<String, Long>,
    associatedWords: List<AssociatedWordInfo>,
    pools: List<WordPool>,
    clusters: List<WordCluster>,
    clusterReviews: List<WordClusterReview>,
    edgePreviews: List<WordEdgeNeighborPreview>,
    phrases: List<WordPhraseWithTags>,
    examples: List<WordExample>,
    onWordClick: (Long, Long) -> Unit,
    onArticleClick: ((Long, Long) -> Unit)?,
    cloudExampleSource: CloudExampleSource,
    cloudExamples: List<CloudWordExample>,
    cloudExamplesLoading: Boolean,
    cloudExamplesError: String?,
    onCloudExampleSourceSelected: (CloudExampleSource) -> Unit,
    detailsLoading: Boolean,
    detailsError: String?,
    onRetryDetails: () -> Unit,
    onClusterReview: ((Long) -> Unit)?
) {
    unifiedWordDetailItems(
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
        detailsLoading = detailsLoading,
        detailsError = detailsError,
        onRetryDetails = onRetryDetails,
        onClusterReview = onClusterReview,
        showSpellingHeader = true
    )
}
