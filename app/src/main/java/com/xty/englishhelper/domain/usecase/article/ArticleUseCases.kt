package com.xty.englishhelper.domain.usecase.article

import com.xty.englishhelper.domain.article.ArticleTokenizer
import com.xty.englishhelper.domain.article.DictionaryMatcher
import com.xty.englishhelper.domain.article.SentenceSplitter
import com.xty.englishhelper.domain.article.WordRef
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ArticleWordStat
import com.xty.englishhelper.domain.model.GrammarPoint
import com.xty.englishhelper.domain.model.KeyWord
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.model.SentenceBreakdown
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.ParagraphAnalysisCacheData
import com.xty.englishhelper.domain.repository.WordExample
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class CreateArticleUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(
        title: String,
        content: String,
        sourceType: ArticleSourceType = ArticleSourceType.MANUAL,
        domain: String = "",
        difficultyAi: Float = 0f,
        summary: String = "",
        author: String = "",
        source: String = "",
        paragraphs: List<ArticleParagraph> = emptyList(),
        coverImageUri: String? = null
    ): Long {
        val article = Article(
            title = title,
            content = content,
            articleUid = UUID.randomUUID().toString(),
            sourceType = sourceType,
            domain = domain,
            difficultyAi = difficultyAi,
            parseStatus = ArticleParseStatus.PENDING,
            summary = summary,
            author = author,
            source = source,
            coverImageUri = coverImageUri
        )
        val articleId = repository.upsertArticle(article)

        // Store paragraphs
        if (paragraphs.isNotEmpty()) {
            val withArticleId = paragraphs.mapIndexed { index, p ->
                p.copy(articleId = articleId, paragraphIndex = index)
            }
            repository.insertParagraphs(withArticleId)
        }

        return articleId
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

            // Only run full parse for saved articles
            if (!article.isSaved) {
                repository.updateParseStatus(articleId, ArticleParseStatus.DONE.ordinal)
                return
            }

            // Step 1: Read paragraphs (already structured in DB)
            val paragraphs = repository.getParagraphs(articleId)

            // Step 2: Clean old data
            repository.deleteSentencesByArticle(articleId)
            repository.deleteWordStatsByArticle(articleId)
            repository.deleteExamplesByArticle(articleId)

            // Step 3: Split sentences per paragraph (reused in Step 5)
            var globalSentenceIndex = 0
            var globalCharOffset = 0
            val allSentences = mutableListOf<ArticleSentence>()
            val allSpans = mutableListOf<com.xty.englishhelper.domain.article.SentenceSpan>()

            for (paragraph in paragraphs) {
                if (paragraph.text.isBlank()) {
                    globalCharOffset += 1 // paragraph separator
                    continue
                }

                val spans = SentenceSplitter.split(paragraph.text)
                allSpans.addAll(spans)
                for (span in spans) {
                    allSentences.add(
                        ArticleSentence(
                            articleId = articleId,
                            sentenceIndex = globalSentenceIndex++,
                            text = span.text,
                            charStart = globalCharOffset + span.charStart,
                            charEnd = globalCharOffset + span.charEnd,
                            paragraphId = paragraph.id
                        )
                    )
                }
                globalCharOffset += paragraph.text.length + 1 // +1 for paragraph separator
            }

            // Step 4: Insert sentences
            allSentences.chunked(500).forEach { chunk ->
                repository.insertSentences(articleId, chunk)
            }

            // Step 5: Tokenize using sentence spans (reusing allSpans from Step 3)
            val tokens = ArticleTokenizer.tokenize(allSpans)

            // Step 6: Aggregate frequencies
            val freqMap = ArticleTokenizer.aggregate(tokens)
            val wordStats = freqMap.map { (normalized, entry) ->
                ArticleWordStat(
                    articleId = articleId,
                    normalizedToken = normalized,
                    displayToken = entry.displayToken,
                    frequency = entry.frequency
                )
            }
            repository.upsertWordStats(articleId, wordStats)

            // Step 7: Match against dictionary
            val allWords = repository.getAllWordsForMatching()
            val wordRefs = allWords.map { WordRef(it.wordId, it.dictionaryId, it.normalizedSpelling) }
            val inflectionMap = allWords.associate { it.wordId to it.inflections }
            val matcher = DictionaryMatcher(wordRefs, inflectionMap)
            val matches = matcher.match(tokens)

            // Step 8: Resolve sentence IDs and insert links
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

            // Step 9: Generate word examples (only for saved articles)
            val sentenceById = storedSentences.associateBy { it.id }
            val examples = wordLinks.map { link ->
                val sentence = sentenceById[link.sentenceId]
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

            // Step 10: Update word count
            val wordCount = freqMap.values.sumOf { it.frequency }
            repository.updateWordCount(articleId, wordCount)

            // Step 11: Update content field for compatibility (paragraphs joined)
            val contentJoined = paragraphs.joinToString("\n\n") { it.text }
            if (contentJoined != article.content) {
                repository.upsertArticle(article.copy(content = contentJoined, wordCount = wordCount))
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
        val hash = computeHash(sentenceText)
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

    private fun computeHash(text: String): String {
        val input = "$text|$CACHE_VERSION"
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

    private fun grammarPointsToJson(points: List<GrammarPoint>): String {
        return json.encodeToString(points)
    }

    private fun keyWordsToJson(keywords: List<KeyWord>): String {
        return json.encodeToString(keywords)
    }

    private fun parseGrammarPoints(jsonStr: String): List<GrammarPoint> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<GrammarPoint>>(jsonStr) }.getOrDefault(emptyList())
    }

    private fun parseKeyWords(jsonStr: String): List<KeyWord> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<KeyWord>>(jsonStr) }.getOrDefault(emptyList())
    }

    companion object {
        private const val CACHE_VERSION = "v1"
    }
}

class AnalyzeParagraphUseCase @Inject constructor(
    private val repository: ArticleRepository,
    private val aiRepository: ArticleAiRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(
        articleId: Long,
        paragraphId: Long,
        paragraphText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider = AiProvider.ANTHROPIC,
        isSaved: Boolean = true
    ): ParagraphAnalysisResult {
        val hash = computeHash(paragraphText)
        val normalizedUrl = baseUrl.trimEnd('/')
        val modelKey = "${provider.name}|$normalizedUrl|$model|$CACHE_VERSION"
        val memoryKey = "P|$articleId|$paragraphId|$hash|$modelKey"

        // Check cache
        if (isSaved) {
            val cached = repository.getParagraphAnalysisCache(articleId, paragraphId, hash, modelKey)
            if (cached != null) {
                return parseCachedResult(cached)
            }
        } else {
            val cached = repository.getMemoryParagraphAnalysisCache(memoryKey)
            if (cached != null) {
                return parseCachedResult(cached)
            }
        }

        // Call AI
        val result = aiRepository.analyzeParagraph(paragraphText, apiKey, model, baseUrl, provider)

        val cacheData = ParagraphAnalysisCacheData(
            meaningZh = result.meaningZh,
            grammarJson = json.encodeToString(result.grammarPoints),
            keywordsJson = json.encodeToString(result.keyVocabulary),
            breakdownsJson = json.encodeToString(result.sentenceBreakdowns)
        )

        // Store in cache
        if (isSaved) {
            repository.insertParagraphAnalysisCache(articleId, paragraphId, hash, modelKey, cacheData)
        } else {
            repository.putMemoryParagraphAnalysisCache(memoryKey, cacheData)
        }

        return result
    }

    private fun computeHash(text: String): String {
        val input = "$text|$CACHE_VERSION"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun parseCachedResult(cache: ParagraphAnalysisCacheData): ParagraphAnalysisResult {
        return ParagraphAnalysisResult(
            meaningZh = cache.meaningZh,
            grammarPoints = runCatching { json.decodeFromString<List<GrammarPoint>>(cache.grammarJson) }.getOrDefault(emptyList()),
            keyVocabulary = runCatching { json.decodeFromString<List<KeyWord>>(cache.keywordsJson) }.getOrDefault(emptyList()),
            sentenceBreakdowns = runCatching { json.decodeFromString<List<SentenceBreakdown>>(cache.breakdownsJson) }.getOrDefault(emptyList())
        )
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

class ScanWordLinksUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(paragraphs: List<ArticleParagraph>): List<ArticleWordLink> {
        val allWords = repository.getAllWordsForMatching()
        if (allWords.isEmpty()) return emptyList()

        val wordRefs = allWords.map { WordRef(it.wordId, it.dictionaryId, it.normalizedSpelling) }
        val inflectionMap = allWords.associate { it.wordId to it.inflections }
        val matcher = DictionaryMatcher(wordRefs, inflectionMap)

        val allSpans = paragraphs.flatMap { SentenceSplitter.split(it.text) }
        val tokens = ArticleTokenizer.tokenize(allSpans)
        val matches = matcher.match(tokens)

        return matches.map { match ->
            ArticleWordLink(
                articleId = 0,
                sentenceId = 0,
                wordId = match.wordId,
                dictionaryId = match.dictionaryId,
                matchedToken = match.matchedToken
            )
        }
    }
}

class TranslateParagraphUseCase @Inject constructor(
    private val repository: ArticleRepository,
    private val aiRepository: ArticleAiRepository
) {
    suspend operator fun invoke(
        articleId: Long,
        paragraphId: Long,
        paragraphText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider,
        isSaved: Boolean
    ): String {
        val hash = computeHash(paragraphText)
        val normalizedUrl = baseUrl.trimEnd('/')
        val modelKey = "TRANSLATE|${provider.name}|$normalizedUrl|$model|$CACHE_VERSION"
        val memoryKey = "T|$articleId|$paragraphId|$hash|$modelKey"

        if (isSaved) {
            val cached = repository.getParagraphAnalysisCache(articleId, paragraphId, hash, modelKey)
            if (cached != null) return cached.meaningZh
        } else {
            val cached = repository.getMemoryParagraphAnalysisCache(memoryKey)
            if (cached != null) return cached.meaningZh
        }

        val translation = aiRepository.translateParagraph(paragraphText, apiKey, model, baseUrl, provider)

        val cacheData = ParagraphAnalysisCacheData(
            meaningZh = translation,
            grammarJson = "",
            keywordsJson = "",
            breakdownsJson = ""
        )

        if (isSaved) {
            repository.insertParagraphAnalysisCache(articleId, paragraphId, hash, modelKey, cacheData)
        } else {
            repository.putMemoryParagraphAnalysisCache(memoryKey, cacheData)
        }

        return translation
    }

    private fun computeHash(text: String): String {
        val input = "$text|$CACHE_VERSION"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_VERSION = "v1"
    }
}

class QuickAnalyzeWordUseCase @Inject constructor(
    private val aiRepository: ArticleAiRepository,
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(
        word: String,
        contextSentence: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): QuickWordAnalysis {
        val normalizedUrl = baseUrl.trimEnd('/')
        val modelKey = "${provider.name}|$normalizedUrl|$model|$CACHE_VERSION"
        val hash = computeHash("$word|${contextSentence.orEmpty()}")
        val memoryKey = "Q|$hash|$modelKey"

        val cached = repository.getMemoryQuickWordAnalysisCache(memoryKey)
        if (cached != null) return cached

        val result = aiRepository.quickAnalyzeWord(word, contextSentence, apiKey, model, baseUrl, provider)
        repository.putMemoryQuickWordAnalysisCache(memoryKey, result)
        return result
    }

    private fun computeHash(text: String): String {
        val input = "$text|$CACHE_VERSION"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_VERSION = "v1"
    }
}
