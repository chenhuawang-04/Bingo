package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.AnthropicApiService
import com.xty.englishhelper.data.remote.dto.AiWordAnalysis
import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import com.xty.englishhelper.data.remote.dto.MessageDto
import com.xty.englishhelper.domain.model.AiOrganizeResult
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
    private val apiService: AnthropicApiService,
    private val moshi: Moshi
) : AiRepository {

    override suspend fun organizeWord(word: String, apiKey: String, model: String, baseUrl: String): AiOrganizeResult {
        val request = AnthropicRequest(
            model = model,
            system = Constants.AI_SYSTEM_PROMPT,
            messages = listOf(
                MessageDto(
                    role = "user",
                    content = Constants.AI_USER_PROMPT_TEMPLATE.format(word)
                )
            )
        )

        val url = buildUrl(baseUrl)
        val response = apiService.createMessage(url, apiKey, request)
        val text = response.content.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from AI")

        return parseAiResponse(text)
    }

    override suspend fun testConnection(apiKey: String, model: String, baseUrl: String): Boolean {
        val request = AnthropicRequest(
            model = model,
            maxTokens = 32,
            messages = listOf(
                MessageDto(role = "user", content = "Hi")
            )
        )
        val url = buildUrl(baseUrl)
        val response = apiService.createMessage(url, apiKey, request)
        return response.content.isNotEmpty()
    }

    private fun buildUrl(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return when {
            base.endsWith("/v1/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
    }

    private fun parseAiResponse(text: String): AiOrganizeResult {
        // Strip markdown code blocks if present
        val jsonText = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val adapter = moshi.adapter(AiWordAnalysis::class.java)
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
}
