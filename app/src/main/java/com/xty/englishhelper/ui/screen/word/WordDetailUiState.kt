package com.xty.englishhelper.ui.screen.word

import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.model.TtsState
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseWithTags
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.repository.WordExample
import com.xty.englishhelper.domain.model.WordCluster
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview

data class WordDetailUiState(
    val word: WordDetails? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val error: String? = null,
    val ttsState: TtsState = TtsState(),
    val linkedWordIds: Map<String, Long> = emptyMap(),
    val associatedWords: List<AssociatedWordInfo> = emptyList(),
    val examples: List<WordExample> = emptyList(),
    val pools: List<WordPool> = emptyList(),
    val clusters: List<WordCluster> = emptyList(),
    val clusterReviews: List<WordClusterReview> = emptyList(),
    val edgePreviews: List<WordEdgeNeighborPreview> = emptyList(),
    val phrases: List<WordPhraseWithTags> = emptyList(),
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
    val cloudExampleSource: CloudExampleSource = CloudExampleSource.CAMBRIDGE,
    val cloudExamples: List<CloudWordExample> = emptyList(),
    val cloudExamplesLoading: Boolean = false,
    val cloudExamplesError: String? = null
)
