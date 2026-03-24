package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.data.remote.dto.AiWordAnalysis
import com.xty.englishhelper.data.remote.dto.WordResearchResponseDto
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiOrganizeResult
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordResearchItem
import com.xty.englishhelper.domain.model.WordResearchReference
import com.xty.englishhelper.domain.repository.AiRepository
import com.xty.englishhelper.util.Constants
import com.xty.englishhelper.util.AiResponseUnwrapper
import com.xty.englishhelper.util.AiJsonRepairer
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Collections
import java.util.LinkedHashMap

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore
) : AiRepository {

    private val wordResearchCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, WordResearchReference>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordResearchReference>?): Boolean {
                return size > 256
            }
        }
    )

    override suspend fun organizeWord(
        word: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider,
        reference: WordResearchReference?
    ): AiOrganizeResult {
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
                        appendLine(Constants.AI_WORD_PROMPT_RULES)
                        appendLine()
                        if (reference != null && reference.hasUsefulReference) {
                            appendLine("【辅助参考资料 / 仅供审慎参考】")
                            appendLine("以下内容来自网络整理摘要，可能包含学习笔记、词典差异说明、考研易混词总结等。")
                            appendLine("你可以合理参考，但不得机械照抄；若与可靠词源、常用法或标准英语习惯冲突，以可靠解释为准。")
                            appendLine("请优先吸收那些对考研更常考、更易混、更稳定的区别点。")
                            appendLine(formatReferenceContext(reference))
                            appendLine()
                        }
                        append("请严格按要求分析单词：")
                        append(word)
                    }
                )
            ),
            maxTokens = 2048
        )
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseAiResponse(text, unwrapEnabled, repairEnabled)
    }

    override suspend fun researchWord(
        word: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): WordResearchReference {
        val cacheKey = buildResearchCacheKey(word, model, baseUrl, provider)
        wordResearchCache[cacheKey]?.let { return it }

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
                        appendLine(Constants.AI_WORD_RESEARCH_PROMPT_RULES)
                        appendLine()
                        append("目标单词：")
                        append(word)
                    }
                )
            ),
            maxTokens = 1536
        )
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        val result = parseWordResearchResponse(text, unwrapEnabled, repairEnabled)
        wordResearchCache[cacheKey] = result
        return result
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

    private fun parseAiResponse(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): AiOrganizeResult {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
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

    private fun parseWordResearchResponse(
        text: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): WordResearchReference {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
        val jsonText = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(WordResearchResponseDto::class.java).lenient()
        val dto = adapter.fromJson(jsonText)
            ?: throw IllegalStateException("Failed to parse word research response")

        return WordResearchReference(
            hasUsefulReference = dto.hasUsefulReference,
            examFocusSummary = normalizeText(dto.examFocusSummary, maxChars = 280),
            confusionWords = dto.confusionWords.map { it.toDomain() }.sanitizeResearchItems(),
            synonymWords = dto.synonymWords.map { it.toDomain() }.sanitizeResearchItems(),
            similarWords = dto.similarWords.map { it.toDomain() }.sanitizeResearchItems(),
            cognateWords = dto.cognateWords.map { it.toDomain() }.sanitizeResearchItems(),
            webFindings = dto.webFindings
                .map { normalizeText(it, maxChars = 160) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .take(5),
            confidence = dto.confidence
        )
    }

    private fun com.xty.englishhelper.data.remote.dto.WordResearchItemDto.toDomain(): WordResearchItem {
        return WordResearchItem(
            word = word,
            note = note,
            examImportance = examImportance
        )
    }

    private fun formatReferenceContext(reference: WordResearchReference): String {
        fun appendGroup(
            builder: StringBuilder,
            title: String,
            items: List<WordResearchItem>
        ) {
            if (items.isEmpty()) return
            builder.appendLine(title)
            items.take(4).forEach { item ->
                builder.append("- ")
                builder.append(item.word.ifBlank { "未标注词项" })
                if (item.examImportance.isNotBlank()) {
                    builder.append("（考研重要性：")
                    builder.append(item.examImportance)
                    builder.append("）")
                }
                if (item.note.isNotBlank()) {
                    builder.append("：")
                    builder.append(item.note)
                }
                builder.appendLine()
            }
        }

        return buildString {
            if (reference.examFocusSummary.isNotBlank()) {
                append("考研重点摘要：")
                appendLine(reference.examFocusSummary)
            }
            if (reference.confidence > 0f) {
                append("参考置信度：")
                appendLine(String.format("%.2f", reference.confidence.coerceIn(0f, 1f)))
            }
            appendGroup(this, "易混词参考：", reference.confusionWords)
            appendGroup(this, "同义词差异参考：", reference.synonymWords)
            appendGroup(this, "形近词参考：", reference.similarWords)
            appendGroup(this, "同根词参考：", reference.cognateWords)
            if (reference.webFindings.isNotEmpty()) {
                appendLine("网络整理摘要：")
                reference.webFindings.take(5).forEach { finding ->
                    append("- ")
                    appendLine(finding)
                }
            }
        }.trim()
    }

    private fun buildResearchCacheKey(
        word: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): String {
        return "${word.trim().lowercase()}|${provider.name}|${baseUrl.trimEnd('/')}|$model|v1"
    }

    private fun List<WordResearchItem>.sanitizeResearchItems(): List<WordResearchItem> {
        return this.map {
            it.copy(
                word = normalizeText(it.word, maxChars = 48),
                note = normalizeText(it.note, maxChars = 160),
                examImportance = normalizeText(it.examImportance, maxChars = 12)
            )
        }
            .filter { it.word.isNotBlank() || it.note.isNotBlank() }
            .distinctBy { "${it.word.lowercase()}|${it.note.lowercase()}" }
            .take(4)
    }

    private fun normalizeText(text: String, maxChars: Int): String {
        if (text.isBlank()) return ""
        val compact = text.replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxChars) compact else compact.take(maxChars).trimEnd() + "…"
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
            val candidate = extractFirstJsonObject(cleaned) ?: cleaned.trim()
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
}
