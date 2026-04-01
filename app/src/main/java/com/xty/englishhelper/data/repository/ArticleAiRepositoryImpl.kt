package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.model.WordOcrCandidate
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleSuitabilityResult
import com.xty.englishhelper.util.AiResponseUnwrapper
import com.xty.englishhelper.util.AiJsonRepairer
import com.xty.englishhelper.util.Constants
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ArticleAiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore
) : ArticleAiRepository {

    override suspend fun extractArticleFromImages(
        imageBytes: List<ByteArray>,
        hint: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ArticleOcrResult {
        val prompt = buildString {
            append("Use OCR to transcribe all visible English text from these images faithfully. ")
            append("Do not rewrite. Return strict JSON only.")
            append(
                """
                {
                  "title": "article title or empty string",
                  "content": "full transcribed text",
                  "domain": "science|technology|news|finance|literature|other",
                  "difficulty": 5.0,
                  "confidence": 0.95
                }
                """.trimIndent()
            )
            append("\n")
            append(Constants.JSON_STRICT_RULES)
            if (!hint.isNullOrBlank()) {
                append("\nHint: ")
                append(hint)
            }
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            imageBytes = imageBytes,
            prompt = prompt,
            maxTokens = 2048
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseJsonPayload(responseText, ArticleOcrResult::class.java, unwrapEnabled, repairEnabled)
            ?: ArticleOcrResult(confidence = 0f)
    }

    override suspend fun analyzeSentence(
        sentence: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): SentenceAnalysisResult {
        val userMessage =
            """
            You are an English sentence analysis assistant.
            Return strict JSON only:
            {
              "meaningZh": "Chinese translation",
              "grammarPoints": [
                {"title": "point", "explanation": "details"}
              ],
              "keyVocabulary": [
                {"word": "word", "meaning": "Chinese meaning"}
              ]
            }
            """.trimIndent()
                .plus("\n")
                .plus(Constants.JSON_STRICT_RULES)
                .plus("\n\nAnalyze this sentence: $sentence")

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 1024
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseJsonPayload(responseText, SentenceAnalysisResult::class.java, unwrapEnabled, repairEnabled)
            ?: SentenceAnalysisResult()
    }

    override suspend fun extractWordsFromImages(
        imageBytes: List<ByteArray>,
        conditions: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): List<String> {
        val conditionClause = if (conditions.isBlank()) "" else "${conditions}的"
        val prompt = buildString {
            append("请扫描图片，提取出所有${conditionClause}英语单词，以JSON数组格式返回，如 [\"word1\", \"word2\"]。只返回JSON数组，不要其他文字。")
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            imageBytes = imageBytes,
            prompt = prompt,
            maxTokens = 2048
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseStringArray(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun extractWordsWithContextFromImages(
        imageBytes: List<ByteArray>,
        conditions: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): List<WordOcrCandidate> {
        val prompt = buildString {
            appendLine("请执行“完全扫描”模式。")
            appendLine("目标：识别图片中符合筛选条件的英语单词，并提取每个单词附近可见的关键信息，作为后续整理参考。")
            appendLine("注意：你现在只做 OCR 结构化提取，不要做词义分析，不要做词条整理。")
            appendLine()
            appendLine("筛选条件：${conditions.ifBlank { "无（提取所有可见英文单词）" }}")
            appendLine()
            appendLine("只允许返回如下 JSON 数组，每项格式：")
            appendLine(
                """
                [
                  {
                    "word": "target",
                    "references": [
                      "图片中与该词直接相关的原文片段1",
                      "图片中与该词直接相关的原文片段2"
                    ]
                  }
                ]
                """.trimIndent()
            )
            appendLine()
            appendLine("约束：")
            appendLine("1) word 必须是英文词形，去掉标点与多余符号。")
            appendLine("2) references 仅保留图片中与该词直接相关的短片段，禁止编造，禁止外推。")
            appendLine("3) 每个单词 references 最多 3 条，每条尽量不超过 80 字符。")
            appendLine("4) 若无有效片段，references 返回空数组。")
            appendLine("5) 只返回 JSON 数组，不要任何额外文本。")
            appendLine(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            imageBytes = imageBytes,
            prompt = prompt,
            maxTokens = 3072
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseWordCandidates(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun analyzeParagraph(
        paragraphText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ParagraphAnalysisResult {
        val userMessage =
            """
            You are an English paragraph analysis assistant for Chinese learners.
            Analyze the paragraph by:
            1. Translating each sentence to Chinese
            2. Identifying key grammar points
            3. Listing important vocabulary

            Return strict JSON only:
            {
              "meaningZh": "Overall paragraph meaning in Chinese",
              "grammarPoints": [
                {"title": "grammar point", "explanation": "Chinese explanation"}
              ],
              "keyVocabulary": [
                {"word": "english word", "meaning": "Chinese meaning"}
              ],
              "sentenceBreakdowns": [
                {"sentence": "original sentence", "translation": "Chinese translation", "grammarNotes": "brief grammar note"}
              ]
            }
            """.trimIndent()
                .plus("\n")
                .plus(Constants.JSON_STRICT_RULES)
                .plus("\n\nAnalyze this paragraph:\n\n$paragraphText")

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 2048
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseJsonPayload(responseText, ParagraphAnalysisResult::class.java, unwrapEnabled, repairEnabled)
            ?: ParagraphAnalysisResult()
    }

    override suspend fun translateParagraph(
        paragraphText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): String {
        val userMessage = buildString {
            append("你是一位专业的英汉翻译。请将用户提供的英文段落翻译为流畅自然的中文。只返回中文译文，不要添加任何解释、注释或原文。\n\n")
            append(paragraphText)
        }

        val client = clientProvider.getClient(provider)
        return client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 1024
        ).trim()
    }

    override suspend fun quickAnalyzeWord(
        word: String,
        contextSentence: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): QuickWordAnalysis {
        val contextPart = if (!contextSentence.isNullOrBlank()) {
            "\nContext sentence: $contextSentence"
        } else ""

        val userMessage = """
            You are an English vocabulary analysis assistant for Chinese learners preparing for the graduate school entrance exam (考研).
            Analyze the given word and return strict JSON only:
            {
              "phonetic": "IPA phonetic notation",
              "partOfSpeech": "part of speech abbreviation (n./v./adj./adv./etc.)",
              "contextMeaning": "meaning in the given context (in Chinese)",
              "commonMeanings": ["meaning1", "meaning2", "meaning3"],
              "examImportance": "核心/高频/普通/低频"
            }
            - commonMeanings: up to 3 most common Chinese meanings
            - examImportance: rate importance for 考研 English
        """.trimIndent()
            .plus("\n")
            .plus(Constants.JSON_STRICT_RULES)
            .plus("\n\nWord: $word$contextPart")

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 512
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseQuickWordAnalysis(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun evaluateArticleSuitability(
        title: String,
        excerpt: String,
        trailText: String?,
        source: String?,
        section: String?,
        wordCount: Int?,
        url: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ArticleSuitabilityResult {
        val safeExcerpt = excerpt.trim().take(2200)
        val userMessage = buildString {
            append("你是考研英语选文评估员，请判断文章是否适合用作考研英语阅读/完形/翻译材料，并给出0-100整数评分与简短理由。\n")
            append("评估标准（摘要）：\n")
            append("- 主题倾向公共议题/社科/科技/教育/经济/环境/文化现象；避免政治、宗教、军事、暴力、种族、色情等敏感或强争议话题。\n")
            append("- 体裁偏议论文/说明文；文学/诗歌/戏剧/纯新闻快讯/广告/操作指南/过度娱乐化内容不适合。\n")
            append("- 可读性与公平：语言规范、背景依赖低，避免高度专业论文或术语密集。\n")
            append("- 命题性：逻辑结构清晰，有转折/因果/对比/例证等，便于出题。\n")
            append("- 长度适中（约400-600词更合适），过短/过长可扣分。\n\n")
            append("文章信息：\n")
            append("Title: ").append(title).append('\n')
            if (!trailText.isNullOrBlank()) append("Summary: ").append(trailText.trim()).append('\n')
            if (!source.isNullOrBlank()) append("Source: ").append(source.trim()).append('\n')
            if (!section.isNullOrBlank()) append("Section: ").append(section.trim()).append('\n')
            if (wordCount != null && wordCount > 0) append("WordCount: ").append(wordCount).append('\n')
            if (!url.isNullOrBlank()) append("URL: ").append(url.trim()).append('\n')
            if (safeExcerpt.isNotBlank()) {
                append("Excerpt:\n")
                append(safeExcerpt).append('\n')
            }
            append(
                """
                请仅返回如下JSON：
                {
                  "score": 85,
                  "reason": "简短理由，不超过两句话"
                }
                """.trimIndent()
            )
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 512
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseSuitability(responseText, unwrapEnabled, repairEnabled)
    }

    private fun <T> parseJsonPayload(
        responseText: String,
        clazz: Class<T>,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): T? {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(clazz).lenient()
        return runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()
    }

    private fun stripCodeFence(text: String): String {
        return text
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .replace("'''json", "", ignoreCase = true)
            .replace("'''", "")
            .trim()
    }

    private fun normalizeResponse(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): String {
        val cleaned = stripCodeFence(text)
        val unwrapped = if (unwrapEnabled) {
            val candidate = extractFirstJsonObject(cleaned)
                ?: extractFirstJsonArray(cleaned)
                ?: cleaned.trim()
            AiResponseUnwrapper.unwrapJsonEnvelope(candidate) ?: cleaned
        } else {
            cleaned
        }
        val stripped = stripCodeFence(unwrapped)
        return if (repairEnabled) AiJsonRepairer.repair(stripped) else stripped
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun extractFirstJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '[' -> if (!inString) depth++
                ']' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun removeTrailingCommas(json: String): String {
        return json.replace(Regex(",\\s*([}\\]])"), "$1")
    }

    private fun parseStringArray(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): List<String> {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
        val arrayMatch = Regex("\\[\\s*(\"[^\"]*\"(?:\\s*,\\s*\"[^\"]*\")*)\\s*\\]").find(cleaned)
            ?: return emptyList()
        return Regex("\"([^\"]+)\"").findAll(arrayMatch.value)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun parseWordCandidates(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): List<WordOcrCandidate> {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
        val arrayText = extractFirstJsonArray(cleaned)
        if (arrayText != null) {
            return parseWordCandidatesFromArray(arrayText)
        }

        val objectText = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val objectAdapter = moshi.adapter(WordCandidateEnvelopeJson::class.java).lenient()
        val envelope = runCatching { objectAdapter.fromJson(objectText) }.getOrNull()
            ?: runCatching { objectAdapter.fromJson(removeTrailingCommas(objectText)) }.getOrNull()
        if (envelope != null) {
            val listFromEnvelope = when {
                !envelope.items.isNullOrEmpty() -> envelope.items
                !envelope.results.isNullOrEmpty() -> envelope.results
                !envelope.candidates.isNullOrEmpty() -> envelope.candidates
                envelope.word != null -> listOf(
                    WordCandidateJson(
                        word = envelope.word,
                        references = envelope.references
                    )
                )
                else -> emptyList()
            }
            return sanitizeWordCandidates(listFromEnvelope)
        }

        return emptyList()
    }

    private fun parseWordCandidatesFromArray(arrayText: String): List<WordOcrCandidate> {
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, WordCandidateJson::class.java)
        val adapter = moshi.adapter<List<WordCandidateJson>>(listType).lenient()
        val parsed = runCatching { adapter.fromJson(arrayText) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(arrayText)) }.getOrNull()
            ?: emptyList()
        return sanitizeWordCandidates(parsed)
    }

    private fun sanitizeWordCandidates(parsed: List<WordCandidateJson>): List<WordOcrCandidate> {
        return parsed.asSequence()
            .map {
                WordOcrCandidate(
                    spelling = (it.word ?: "").trim(),
                    references = (it.references ?: emptyList())
                        .map { ref -> ref.trim() }
                        .filter { ref -> ref.isNotBlank() }
                        .distinct()
                        .take(3)
                )
            }
            .filter { it.spelling.isNotBlank() }
            .distinctBy { it.spelling.lowercase() }
            .toList()
    }

    private fun parseQuickWordAnalysis(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): QuickWordAnalysis {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()

        // Parse using Moshi
        val adapter = moshi.adapter(QuickWordAnalysisJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            QuickWordAnalysis(
                phonetic = parsed.phonetic ?: "",
                partOfSpeech = parsed.partOfSpeech ?: "",
                contextMeaning = parsed.contextMeaning ?: "",
                commonMeanings = parsed.commonMeanings ?: emptyList(),
                examImportance = parsed.examImportance ?: ""
            )
        } else {
            QuickWordAnalysis()
        }
    }

    private fun parseSuitability(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): ArticleSuitabilityResult {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(SuitabilityJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        val score = parsed?.score?.roundToInt()?.coerceIn(0, 100) ?: 0
        val reason = parsed?.reason?.trim().orEmpty()
        return ArticleSuitabilityResult(score = score, reason = reason)
    }
}

// Internal JSON model for Moshi parsing
private data class QuickWordAnalysisJson(
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val contextMeaning: String? = null,
    val commonMeanings: List<String>? = null,
    val examImportance: String? = null
)

private data class SuitabilityJson(
    val score: Double? = null,
    val reason: String? = null
)

private data class WordCandidateJson(
    val word: String? = null,
    val references: List<String>? = null
)

private data class WordCandidateEnvelopeJson(
    val items: List<WordCandidateJson>? = null,
    val results: List<WordCandidateJson>? = null,
    val candidates: List<WordCandidateJson>? = null,
    val word: String? = null,
    val references: List<String>? = null
)
