package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.AnthropicApiService
import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import com.xty.englishhelper.data.remote.dto.MessageDto
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleAiRepositoryImpl @Inject constructor(
    private val apiService: AnthropicApiService,
    private val moshi: Moshi
) : ArticleAiRepository {

    override suspend fun extractArticleFromImages(
        imageBytes: List<ByteArray>,
        hint: String?,
        apiKey: String,
        model: String,
        baseUrl: String
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

        val requestBody = buildMultimodalRequest(
            model = model,
            imageBytes = imageBytes,
            prompt = prompt
        )

        val url = buildMessagesUrl(baseUrl)
        val response = apiService.createMultimodalMessage(url, apiKey, requestBody)
        val responseText = response.content.firstOrNull()?.text.orEmpty()

        return parseJsonPayload(responseText, ArticleOcrResult::class.java)
            ?: ArticleOcrResult(confidence = 0f)
    }

    override suspend fun analyzeSentence(
        sentence: String,
        apiKey: String,
        model: String,
        baseUrl: String
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

        val request = AnthropicRequest(
            model = model,
            maxTokens = 1024,
            system = systemPrompt,
            messages = listOf(
                MessageDto(role = "user", content = "Analyze this sentence: $sentence")
            )
        )

        val url = buildMessagesUrl(baseUrl)
        val response = apiService.createMessage(url, apiKey, request)
        val responseText = response.content.firstOrNull()?.text.orEmpty()

        return parseJsonPayload(responseText, SentenceAnalysisResult::class.java)
            ?: SentenceAnalysisResult()
    }

    private fun buildMultimodalRequest(
        model: String,
        imageBytes: List<ByteArray>,
        prompt: String
    ): RequestBody {
        val sb = StringBuilder()
        sb.append("""{"model":"$model","max_tokens":2048,"messages":[{"role":"user","content":[""")

        sb.append("""{"type":"text","text":""")
        val escapedPrompt = escapeJson(prompt)
        sb.append("\"$escapedPrompt\"")
        sb.append("}")

        imageBytes.forEach { bytes ->
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            sb.append(""",{"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":""")
            sb.append("\"$base64\"")
            sb.append("}}")
        }

        sb.append("""]}]}""")
        return sb.toString().toRequestBody("application/json".toMediaType())
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

    private fun escapeJson(raw: String): String {
        val out = StringBuilder(raw.length + 32)
        raw.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun buildMessagesUrl(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return when {
            base.endsWith("/v1/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
    }
}
