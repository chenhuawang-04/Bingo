package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleAiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi
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

        return parseJsonPayload(responseText, ArticleOcrResult::class.java)
            ?: ArticleOcrResult(confidence = 0f)
    }

    override suspend fun analyzeSentence(
        sentence: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): SentenceAnalysisResult {
        val systemPrompt =
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

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            messages = listOf(
                ChatMessage(role = "user", content = "Analyze this sentence: $sentence")
            ),
            maxTokens = 1024
        )

        return parseJsonPayload(responseText, SentenceAnalysisResult::class.java)
            ?: SentenceAnalysisResult()
    }

    private fun <T> parseJsonPayload(responseText: String, clazz: Class<T>): T? {
        val cleaned = stripCodeFence(responseText)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(clazz).lenient()
        return runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()
    }

    private fun stripCodeFence(text: String): String {
        return text
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
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

    private fun removeTrailingCommas(json: String): String {
        return json.replace(Regex(",\\s*([}\\]])"), "$1")
    }
}
