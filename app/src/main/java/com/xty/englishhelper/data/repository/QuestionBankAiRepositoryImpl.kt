package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.repository.AnswerResult
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.ScanResult
import com.xty.englishhelper.domain.repository.ScannedQuestion
import com.xty.englishhelper.domain.repository.ScannedQuestionGroup
import com.xty.englishhelper.domain.repository.VerifyResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionBankAiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi
) : QuestionBankAiRepository {

    override suspend fun scanQuestions(
        images: List<ByteArray>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): ScanResult {
        val prompt = buildString {
            append("You are an exam paper OCR specialist. ")
            append("Carefully extract all content from these exam paper images.\n")
            append("Return strict JSON matching this structure:\n")
            append("""
{
  "examPaperTitle": "paper title",
  "questionGroups": [
    {
      "questionType": "READING_COMPREHENSION",
      "sectionLabel": "Text 1",
      "directions": "directions text or null",
      "passageParagraphs": ["paragraph 1", "paragraph 2"],
      "sourceInfo": "source description or null",
      "sourceUrl": "URL you found via search, or null",
      "questions": [
        {
          "questionNumber": 21,
          "questionText": "question stem",
          "optionA": "[A] option text",
          "optionB": "[B] option text",
          "optionC": "[C] option text",
          "optionD": "[D] option text",
          "wordCount": 45,
          "difficultyLevel": "MEDIUM",
          "difficultyScore": 0.6
        }
      ],
      "wordCount": 350,
      "difficultyLevel": "MEDIUM",
      "difficultyScore": 0.65
    }
  ],
  "confidence": 0.9
}
""".trimIndent())
            append("\nRules:\n")
            append("- Transcribe passage text EXACTLY as printed, preserving all paragraphs.\n")
            append("- sourceUrl: Do NOT extract URLs from the exam paper image (exam papers never print source URLs). ")
            append("Instead, use web content to identify and confirm the original source article for each reading passage. ")
            append("If you find a likely match, provide its URL. If not, set to null.\n")
            append("- sourceInfo: a brief description of the source you found (e.g. \"The Guardian, 2024\"), or null.\n")
            append("- wordCount = word count of passage + all questions in the group.\n")
            append("- difficultyLevel: EASY/MEDIUM/HARD based on vocabulary and sentence complexity.\n")
            append("- confidence: your overall OCR confidence (0-1).\n")
            append("- Return JSON only, no markdown fences.")
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            imageBytes = images, prompt = prompt, maxTokens = 8192
        )

        return parseScanResult(responseText)
    }

    override suspend fun verifySource(
        passageText: String, referenceUrl: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): VerifyResult {
        val systemPrompt = "You are a source article research assistant. " +
            "Your task is to find the original published article from which an exam reading passage was taken, " +
            "using web content to confirm the source."
        val userMessage = buildString {
            append("Below is a reading passage extracted from an English exam paper. ")
            append("Find the original published article that this passage comes from.\n\n")
            append("=== PASSAGE TEXT ===\n")
            append(passageText)
            append("\n=== END PASSAGE ===\n\n")
            if (referenceUrl.isNotBlank()) {
                append("A previous search suggested this URL as a possible source (use it as a hint, but do NOT limit your search to it — it may be incorrect):\n")
                append(referenceUrl)
                append("\n\n")
            }
            append("Instructions:\n")
            append("- Use web content to find the original full article.\n")
            append("- If found, return matched=true with the complete article text split into paragraphs.\n")
            append("- If the reference URL is wrong, find and return the correct URL.\n")
            append("- If you cannot find the original article at all, return matched=false with an explanation.\n\n")
            append("Return strict JSON:\n")
            append("""
{
  "matched": true,
  "errorMessage": null,
  "articleTitle": "title of the original article",
  "articleAuthor": "author name",
  "articleContent": "full article text (all paragraphs joined)",
  "articleSummary": "brief 1-2 sentence summary",
  "articleParagraphs": ["paragraph 1", "paragraph 2", "..."],
  "sourceUrl": "the actual canonical URL of the source article"
}
""".trimIndent())
            append("\nIf not found, set matched=false and explain in errorMessage. Return JSON only, no markdown fences.")
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = systemPrompt,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 4096
        )

        return parseVerifyResult(responseText)
    }

    override suspend fun generateAnswers(
        passageText: String, questions: List<QuestionItem>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<AnswerResult> {
        val systemPrompt = "You are an expert English exam answer generator. Read the passage and answer each multiple-choice question."
        val userMessage = buildString {
            append("Passage:\n$passageText\n\nQuestions:\n")
            questions.forEach { q ->
                append("${q.questionNumber}. ${q.questionText}\n")
                q.optionA?.let { append("[A] $it\n") }
                q.optionB?.let { append("[B] $it\n") }
                q.optionC?.let { append("[C] $it\n") }
                q.optionD?.let { append("[D] $it\n") }
                append("\n")
            }
            append("Return strict JSON array:\n")
            append("""
[
  {
    "questionNumber": 21,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
""".trimIndent())
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = systemPrompt,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 4096
        )

        return parseAnswerResults(responseText)
    }

    override suspend fun scanAnswers(
        images: List<ByteArray>, questionNumbers: List<Int>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<AnswerResult> {
        val prompt = buildString {
            append("Extract answer keys from these answer sheet images.\n")
            append("Expected question numbers: ${questionNumbers.joinToString(", ")}\n")
            append("Return strict JSON array:\n")
            append("""
[
  {"questionNumber": 21, "answer": "A", "explanation": "explanation if visible"}
]
""".trimIndent())
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            imageBytes = images, prompt = prompt, maxTokens = 2048
        )

        return parseAnswerResults(responseText)
    }

    // ── JSON Parsing ──

    private fun parseScanResult(responseText: String): ScanResult {
        val cleaned = stripCodeFence(responseText)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(ScanResultJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            ScanResult(
                examPaperTitle = parsed.examPaperTitle ?: "",
                questionGroups = parsed.questionGroups?.map { it.toDomain() } ?: emptyList(),
                confidence = parsed.confidence ?: 0f
            )
        } else {
            ScanResult(confidence = 0f)
        }
    }

    private fun parseVerifyResult(responseText: String): VerifyResult {
        val cleaned = stripCodeFence(responseText)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(VerifyResultJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            VerifyResult(
                matched = parsed.matched ?: false,
                errorMessage = parsed.errorMessage,
                articleTitle = parsed.articleTitle,
                articleAuthor = parsed.articleAuthor,
                articleContent = parsed.articleContent,
                articleSummary = parsed.articleSummary,
                articleParagraphs = parsed.articleParagraphs,
                sourceUrl = parsed.sourceUrl
            )
        } else {
            VerifyResult(matched = false, errorMessage = "Failed to parse AI response")
        }
    }

    private fun parseAnswerResults(responseText: String): List<AnswerResult> {
        val cleaned = stripCodeFence(responseText)
        val json = extractFirstJsonArray(cleaned) ?: cleaned.trim()
        val type = Types.newParameterizedType(List::class.java, AnswerResultJson::class.java)
        val adapter = moshi.adapter<List<AnswerResultJson>>(type).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return parsed?.map {
            AnswerResult(
                questionNumber = it.questionNumber ?: 0,
                answer = it.answer ?: "",
                explanation = it.explanation,
                difficultyLevel = it.difficultyLevel,
                difficultyScore = it.difficultyScore
            )
        } ?: emptyList()
    }

    // ── Utility functions ──

    private fun stripCodeFence(text: String): String =
        text.replace("```json", "", ignoreCase = true).replace("```", "").trim()

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0; var inString = false; var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) { escaped = false; continue }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun extractFirstJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0; var inString = false; var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) { escaped = false; continue }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '[' -> if (!inString) depth++
                ']' -> if (!inString) { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun removeTrailingCommas(json: String): String =
        json.replace(Regex(",\\s*([}\\]])"), "$1")
}

// ── Internal JSON models ──

private data class ScanResultJson(
    val examPaperTitle: String? = null,
    val questionGroups: List<ScannedQuestionGroupJson>? = null,
    val confidence: Float? = null
)

private data class ScannedQuestionGroupJson(
    val questionType: String? = null,
    val sectionLabel: String? = null,
    val directions: String? = null,
    val passageParagraphs: List<String>? = null,
    val sourceInfo: String? = null,
    val sourceUrl: String? = null,
    val questions: List<ScannedQuestionJson>? = null,
    val wordCount: Int? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
) {
    fun toDomain() = ScannedQuestionGroup(
        questionType = questionType ?: "READING_COMPREHENSION",
        sectionLabel = sectionLabel,
        directions = directions,
        passageParagraphs = passageParagraphs ?: emptyList(),
        sourceInfo = sourceInfo,
        sourceUrl = sourceUrl,
        questions = questions?.map { it.toDomain() } ?: emptyList(),
        wordCount = wordCount ?: 0,
        difficultyLevel = difficultyLevel,
        difficultyScore = difficultyScore
    )
}

private data class ScannedQuestionJson(
    val questionNumber: Int? = null,
    val questionText: String? = null,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val wordCount: Int? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
) {
    fun toDomain() = ScannedQuestion(
        questionNumber = questionNumber ?: 0,
        questionText = questionText ?: "",
        optionA = optionA ?: "",
        optionB = optionB ?: "",
        optionC = optionC ?: "",
        optionD = optionD ?: "",
        wordCount = wordCount ?: 0,
        difficultyLevel = difficultyLevel,
        difficultyScore = difficultyScore
    )
}

private data class VerifyResultJson(
    val matched: Boolean? = null,
    val errorMessage: String? = null,
    val articleTitle: String? = null,
    val articleAuthor: String? = null,
    val articleContent: String? = null,
    val articleSummary: String? = null,
    val articleParagraphs: List<String>? = null,
    val sourceUrl: String? = null
)

private data class AnswerResultJson(
    val questionNumber: Int? = null,
    val answer: String? = null,
    val explanation: String? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)
