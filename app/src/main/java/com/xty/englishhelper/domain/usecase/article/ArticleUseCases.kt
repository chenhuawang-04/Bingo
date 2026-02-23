package com.xty.englishhelper.domain.usecase.article

import com.xty.englishhelper.domain.article.ArticleTokenizer
import com.xty.englishhelper.domain.article.DictionaryMatcher
import com.xty.englishhelper.domain.article.SentenceSplitter
import com.xty.englishhelper.domain.article.WordRef
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ArticleWordStat
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.WordExample
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject

class CreateArticleUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(title: String, content: String, sourceType: ArticleSourceType = ArticleSourceType.MANUAL, domain: String = "", difficultyAi: Float = 0f): Long {
        val article = Article(
            title = title,
            content = content,
            sourceType = sourceType,
            domain = domain,
            difficultyAi = difficultyAi,
            parseStatus = ArticleParseStatus.PENDING
        )
        return repository.upsertArticle(article)
    }
}

class UpdateArticleUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(article: Article): Long {
        return repository.upsertArticle(article.copy(updatedAt = System.currentTimeMillis()))
    }
}

class DeleteArticleUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(articleId: Long) {
        repository.deleteArticle(articleId)
    }
}

class GetArticleListUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    operator fun invoke(): Flow<List<Article>> {
        return repository.getAllArticles()
    }
}

class GetArticleDetailUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    operator fun invoke(articleId: Long): Flow<Article?> {
        return repository.getArticleById(articleId)
    }
}

class GetArticleStatisticsUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(articleId: Long): ArticleStatistics {
        val sentences = repository.getSentences(articleId)
        val wordStats = repository.getWordStats(articleId)
        val article = repository.getArticleByIdOnce(articleId)

        val totalWords = wordStats.sumOf { it.frequency }
        val topFreq = wordStats.take(10).map { it.displayToken to it.frequency }

        return ArticleStatistics(
            wordCount = totalWords,
            sentenceCount = sentences.size,
            charCount = article?.content?.length ?: 0,
            uniqueWordCount = wordStats.size,
            topFrequencies = topFreq,
            difficultyFinal = article?.difficultyFinal ?: 0f
        )
    }
}
class ParseArticleUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(articleId: Long) {
        try {
            repository.updateParseStatus(articleId, ArticleParseStatus.PROCESSING.ordinal)

            val article = repository.getArticleByIdOnce(articleId)
                ?: throw IllegalStateException("Article not found")

            // Step 1: Split sentences
            val spans = SentenceSplitter.split(article.content)
            val sentences = spans.mapIndexed { index, span ->
                ArticleSentence(
                    articleId = articleId,
                    sentenceIndex = index,
                    text = span.text,
                    charStart = span.charStart,
                    charEnd = span.charEnd
                )
            }

            // Step 2: Insert sentences in chunks
            repository.deleteSentencesByArticle(articleId)
            sentences.chunked(500).forEach { chunk ->
                repository.insertSentences(articleId, chunk)
            }

            // Step 3: Tokenize
            val tokens = ArticleTokenizer.tokenize(spans)

            // Step 4: Aggregate frequencies
            val freqMap = ArticleTokenizer.aggregate(tokens)
            val wordStats = freqMap.map { (normalized, entry) ->
                ArticleWordStat(
                    articleId = articleId,
                    normalizedToken = normalized,
                    displayToken = entry.displayToken,
                    frequency = entry.frequency
                )
            }
            // Delete old word stats before upserting new ones to prevent stale data
            repository.deleteWordStatsByArticle(articleId)
            repository.upsertWordStats(articleId, wordStats)

            // Step 5: Match against dictionary
            val allWords = repository.getAllWordsForMatching()
            val wordRefs = allWords.map { WordRef(it.wordId, it.dictionaryId, it.normalizedSpelling) }
            val inflectionMap = allWords.associate { it.wordId to it.inflections }
            val matcher = DictionaryMatcher(wordRefs, inflectionMap)
            val matches = matcher.match(tokens)

            // Step 6: Resolve sentence IDs and insert links
            val storedSentences = repository.getSentences(articleId)
            val sentenceIdByIndex = storedSentences.associate { it.sentenceIndex to it.id }

            val wordLinks = matches.mapNotNull { match ->
                val sentenceId = sentenceIdByIndex[match.sentenceIndex] ?: return@mapNotNull null
                ArticleWordLink(
                    articleId = articleId,
                    sentenceId = sentenceId,
                    wordId = match.wordId,
                    dictionaryId = match.dictionaryId,
                    matchedToken = match.matchedToken
                )
            }
            wordLinks.chunked(500).forEach { chunk ->
                repository.upsertWordLinks(chunk)
            }

            // Step 7: Generate word examples - 先清理旧例句，再生成新的
            repository.deleteExamplesByArticle(articleId)
            val examples = wordLinks.map { link ->
                val sentence = storedSentences.find { it.id == link.sentenceId }
                val articleTitle = article.title
                WordExample(
                    wordId = link.wordId,
                    sentence = sentence?.text ?: "",
                    sourceType = WordExampleSourceType.ARTICLE,
                    sourceArticleId = articleId,
                    sourceSentenceId = link.sentenceId,
                    sourceLabel = "「$articleTitle」例句"
                )
            }.filter { it.sentence.isNotBlank() }
            examples.chunked(300).forEach { chunk ->
                repository.insertExamples(chunk)
            }

            repository.updateParseStatus(articleId, ArticleParseStatus.DONE.ordinal)
        } catch (e: Exception) {
            repository.updateParseStatus(articleId, ArticleParseStatus.FAILED.ordinal)
            throw e
        }
    }
}
class AnalyzeSentenceUseCase @Inject constructor(
    private val repository: ArticleRepository,
    private val aiRepository: ArticleAiRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(
        articleId: Long,
        sentenceId: Long,
        sentenceText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider = AiProvider.ANTHROPIC
    ): SentenceAnalysisResult {
        val hash = computeSentenceHash(sentenceText)
        val normalizedUrl = baseUrl.trimEnd('/')
        val modelKey = "${provider.name}|$normalizedUrl|$model|$CACHE_VERSION"

        // Check cache first
        val cached = repository.getAnalysisCache(articleId, sentenceId, hash, modelKey)
        if (cached != null) {
            return parseCachedResult(cached)
        }

        // Call AI
        val result = aiRepository.analyzeSentence(sentenceText, apiKey, model, baseUrl, provider)

        // Store in cache
        repository.insertAnalysisCache(
            articleId, sentenceId, hash, modelKey,
            com.xty.englishhelper.domain.repository.SentenceAnalysisCache(
                meaningZh = result.meaningZh,
                grammarJson = grammarPointsToJson(result.grammarPoints),
                keywordsJson = keyWordsToJson(result.keyVocabulary)
            )
        )

        return result
    }

    private fun computeSentenceHash(sentenceText: String): String {
        val input = "$sentenceText|$CACHE_VERSION"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun parseCachedResult(cache: com.xty.englishhelper.domain.repository.SentenceAnalysisCache): SentenceAnalysisResult {
        return SentenceAnalysisResult(
            meaningZh = cache.meaningZh,
            grammarPoints = parseGrammarPoints(cache.grammarJson),
            keyVocabulary = parseKeyWords(cache.keywordsJson)
        )
    }

    private fun grammarPointsToJson(points: List<com.xty.englishhelper.domain.model.GrammarPoint>): String {
        return json.encodeToString(points)
    }

    private fun keyWordsToJson(keywords: List<com.xty.englishhelper.domain.model.KeyWord>): String {
        return json.encodeToString(keywords)
    }

    private fun parseGrammarPoints(jsonStr: String): List<com.xty.englishhelper.domain.model.GrammarPoint> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<com.xty.englishhelper.domain.model.GrammarPoint>>(jsonStr) }.getOrDefault(emptyList())
    }

    private fun parseKeyWords(jsonStr: String): List<com.xty.englishhelper.domain.model.KeyWord> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<com.xty.englishhelper.domain.model.KeyWord>>(jsonStr) }.getOrDefault(emptyList())
    }

    companion object {
        private const val CACHE_VERSION = "v1"
    }
}
class ExtractArticleFromImagesUseCase @Inject constructor(
    private val aiRepository: ArticleAiRepository
) {
    suspend operator fun invoke(
        imageBytes: List<ByteArray>,
        hint: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider = AiProvider.ANTHROPIC
    ): ArticleOcrResult {
        return aiRepository.extractArticleFromImages(imageBytes, hint, apiKey, model, baseUrl, provider)
    }
}

class GetWordExamplesUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(wordId: Long): List<WordExample> {
        return repository.getExamplesForWord(wordId)
    }
}

class GetArticleCountUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    operator fun invoke(): Flow<Int> {
        return repository.getArticleCount()
    }
}
