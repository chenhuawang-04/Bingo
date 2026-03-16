package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.data.remote.dto.AiWordAnalysis
import com.xty.englishhelper.domain.model.AiOrganizeResult
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.repository.AiRepository
import com.xty.englishhelper.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi
) : AiRepository {

    override suspend fun organizeWord(word: String, apiKey: String, model: String, baseUrl: String, provider: AiProvider): AiOrganizeResult {
        val client = clientProvider.getClient(provider)
        val text = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = buildString {
                        appendLine(Constants.AI_SYSTEM_PROMPT)
                        appendLine()
                        append(Constants.AI_USER_PROMPT_TEMPLATE.format(word))
                    }
                )
            ),
            maxTokens = 2048
        )
        return parseAiResponse(text)
    }

    override suspend fun testConnection(apiKey: String, model: String, baseUrl: String, provider: AiProvider): Boolean {
        val client = clientProvider.getClient(provider)
        val text = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
            maxTokens = 32
        )
        return text.isNotBlank()
    }

    private fun parseAiResponse(text: String): AiOrganizeResult {
        val cleaned = stripCodeFence(text)
        val jsonText = extractFirstJsonObject(cleaned) ?: cleaned.trim()

        val adapter = moshi.adapter(AiWordAnalysis::class.java).lenient()
        val analysis = adapter.fromJson(jsonText)
            ?: throw IllegalStateException("Failed to parse AI response")

        return AiOrganizeResult(
            phonetic = analysis.phonetic,
            meanings = analysis.meanings.map { Meaning(it.pos, it.definition) },
            rootExplanation = analysis.rootExplanation,
            synonyms = analysis.synonyms.map { SynonymInfo(word = it.word, explanation = it.explanation) },
            similarWords = analysis.similarWords.map {
                SimilarWordInfo(word = it.word, meaning = it.meaning, explanation = it.explanation)
            },
            cognates = analysis.cognates.map {
                CognateInfo(word = it.word, meaning = it.meaning, sharedRoot = it.sharedRoot)
            },
            decomposition = analysis.decomposition.map {
                DecompositionPart(
                    segment = it.segment,
                    role = runCatching { MorphemeRole.valueOf(it.role) }.getOrDefault(MorphemeRole.OTHER),
                    meaning = it.meaning
                )
            },
            inflections = analysis.inflections.map {
                Inflection(form = it.form, formType = it.formType)
            }
        )
    }

    private fun stripCodeFence(text: String): String {
        return text
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .replace("'''json", "", ignoreCase = true)
            .replace("'''", "")
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
}
