package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseWithTags
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview
import com.xty.englishhelper.domain.repository.WordExample
import com.xty.englishhelper.domain.repository.WordClusterRepository
import com.xty.englishhelper.domain.repository.WordPhraseRepository
import com.xty.englishhelper.domain.usecase.article.GetWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.pool.GetWordPoolsUseCase
import com.xty.englishhelper.domain.usecase.study.GetStudyWordEdgePreviewsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

enum class WordPresentationSection {
    LINKED_WORDS,
    ASSOCIATED_WORDS,
    ARTICLE_EXAMPLES,
    WORD_POOLS,
    WORD_CLUSTERS,
    RELATION_NETWORK,
    PHRASES
}

data class WordPresentationDetails(
    val linkedWordIds: Map<String, Long> = emptyMap(),
    val associatedWords: List<AssociatedWordInfo> = emptyList(),
    val examples: List<WordExample> = emptyList(),
    val pools: List<WordPool> = emptyList(),
    val clusterReviews: List<WordClusterReview> = emptyList(),
    val edgePreviews: List<WordEdgeNeighborPreview> = emptyList(),
    val phrases: List<WordPhraseWithTags> = emptyList(),
    val failedSections: Set<WordPresentationSection> = emptySet()
)

/**
 * Loads every local information block used by a word presentation. Independent data sources are
 * queried concurrently, and a failure in one source does not hide the successfully loaded blocks.
 */
class GetWordPresentationUseCase @Inject constructor(
    private val resolveLinkedWords: ResolveLinkedWordsUseCase,
    private val getAssociatedWords: GetAssociatedWordsUseCase,
    private val getWordExamples: GetWordExamplesUseCase,
    private val getWordPools: GetWordPoolsUseCase,
    private val wordClusterRepository: WordClusterRepository,
    private val getWordEdgePreviews: GetStudyWordEdgePreviewsUseCase,
    private val wordPhraseRepository: WordPhraseRepository
) {
    suspend operator fun invoke(word: WordDetails): WordPresentationDetails = supervisorScope {
        val spellings = word.synonyms.map { it.word } +
            word.similarWords.map { it.word } +
            word.cognates.map { it.word }

        val linked = async {
            loadSection<Map<String, Long>>(WordPresentationSection.LINKED_WORDS, emptyMap()) {
                if (spellings.isEmpty()) emptyMap() else resolveLinkedWords(word.dictionaryId, spellings)
            }
        }
        val associated = async {
            loadSection<List<AssociatedWordInfo>>(WordPresentationSection.ASSOCIATED_WORDS, emptyList()) {
                getAssociatedWords(word.id)
            }
        }
        val examples = async {
            loadSection<List<WordExample>>(WordPresentationSection.ARTICLE_EXAMPLES, emptyList()) {
                getWordExamples(word.id)
            }
        }
        val pools = async {
            loadSection<List<WordPool>>(WordPresentationSection.WORD_POOLS, emptyList()) {
                getWordPools(word.id)
            }
        }
        val clusters = async {
            loadSection<List<WordClusterReview>>(WordPresentationSection.WORD_CLUSTERS, emptyList()) {
                wordClusterRepository.getClusterReviewsForWord(word.id)
            }
        }
        val edges = async {
            loadSection<List<WordEdgeNeighborPreview>>(WordPresentationSection.RELATION_NETWORK, emptyList()) {
                getWordEdgePreviews(word.dictionaryId, word.id, minConfidence = 0.0)
            }
        }
        val phrases = async {
            loadSection<List<WordPhraseWithTags>>(WordPresentationSection.PHRASES, emptyList()) {
                wordPhraseRepository.getPhrasesForWord(word.id)
            }
        }

        val linkedResult = linked.await()
        val associatedResult = associated.await()
        val exampleResult = examples.await()
        val poolResult = pools.await()
        val clusterResult = clusters.await()
        val edgeResult = edges.await()
        val phraseResult = phrases.await()
        WordPresentationDetails(
            linkedWordIds = linkedResult.value,
            associatedWords = associatedResult.value,
            examples = exampleResult.value,
            pools = poolResult.value,
            clusterReviews = clusterResult.value,
            edgePreviews = edgeResult.value,
            phrases = phraseResult.value,
            failedSections = listOfNotNull(
                linkedResult.failedSection,
                associatedResult.failedSection,
                exampleResult.failedSection,
                poolResult.failedSection,
                clusterResult.failedSection,
                edgeResult.failedSection,
                phraseResult.failedSection
            ).toSet()
        )
    }

    private suspend fun <T> loadSection(
        section: WordPresentationSection,
        fallback: T,
        block: suspend () -> T
    ): SectionResult<T> = try {
        SectionResult(value = block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        SectionResult(value = fallback, failedSection = section)
    }

    private data class SectionResult<T>(
        val value: T,
        val failedSection: WordPresentationSection? = null
    )
}
