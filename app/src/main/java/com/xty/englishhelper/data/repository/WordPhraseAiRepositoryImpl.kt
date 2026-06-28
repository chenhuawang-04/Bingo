package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.data.remote.dto.WordPhraseOrganizationResponseDto
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseCandidate
import com.xty.englishhelper.domain.model.WordPhraseOrganizeResult
import com.xty.englishhelper.domain.model.WordPhraseTag
import com.xty.englishhelper.domain.model.WordPhraseTagCandidate
import com.xty.englishhelper.domain.repository.WordPhraseAiRepository
import com.xty.englishhelper.util.AiJsonRepairer
import com.xty.englishhelper.util.AiResponseUnwrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordPhraseAiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore
) : WordPhraseAiRepository {

    override suspend fun organizeWordPhrases(
        word: WordDetails,
        existingTags: List<WordPhraseTag>,
        maxPhrases: Int,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): WordPhraseOrganizeResult {
        val client = clientProvider.getClient(provider)
        val raw = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = buildPrompt(word, existingTags, maxPhrases.coerceIn(1, 12))
                )
            ),
            maxTokens = 1536
        )
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parse(raw, unwrapEnabled, repairEnabled)
    }

    private fun buildPrompt(
        word: WordDetails,
        existingTags: List<WordPhraseTag>,
        maxPhrases: Int
    ): String = buildString {
        appendLine("你是英语学习应用中的单词短语/词组整理器。")
        appendLine("请为目标单词整理真实、常用、可用于阅读理解或写作的词组/短语。")
        appendLine("要求：")
        appendLine("1. 只输出严格 JSON 对象，不要 Markdown，不要解释。")
        appendLine("2. 每个 phrase 必须是真实常用表达，不要生成生硬拼接。")
        appendLine("3. 每个 phrase 必须带至少一个 tag，可以带多个 tag。")
        appendLine("4. 优先复用已有 tag；只有已有 tag 无法覆盖时才创建新 tag。")
        appendLine("5. tag 名称要短、稳定、可复用，不要为单个短语创建过细 tag。")
        appendLine("6. 最多返回 $maxPhrases 个 phrase；没有值得保存的短语时返回 {\"phrases\":[]}.")
        appendLine()
        appendLine("输出 JSON schema：")
        appendLine("{\"phrases\":[{\"phrase\":\"take advantage of\",\"meaning\":\"利用\",\"example\":\"Students should take advantage of online resources.\",\"usageNote\":\"常用于建议类写作。\",\"register\":\"neutral\",\"difficulty\":\"B2\",\"confidence\":0.9,\"tags\":[{\"name\":\"建议措施\",\"description\":\"用于提出建议或解决方案\"}]}]}")
        appendLine()
        appendLine("目标单词：${word.spelling}")
        if (word.phonetic.isNotBlank()) appendLine("音标：${word.phonetic}")
        if (word.meanings.isNotEmpty()) {
            appendLine("释义：")
            word.meanings.take(8).forEach { meaning ->
                appendLine("- ${meaning.pos} ${meaning.definition}")
            }
        }
        appendLine()
        appendLine("已有 tag（请优先复用）：")
        if (existingTags.isEmpty()) {
            appendLine("- 暂无")
        } else {
            existingTags.take(80).forEach { tag ->
                val desc = tag.description.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                appendLine("- ${tag.name}$desc")
            }
        }
    }

    private fun parse(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): WordPhraseOrganizeResult {
        val cleaned = normalizeResponse(text, unwrapEnabled, repairEnabled)
        val jsonText = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(WordPhraseOrganizationResponseDto::class.java).lenient()
        val dto = adapter.fromJson(jsonText)
            ?: throw IllegalStateException("Failed to parse word phrase response")
        return WordPhraseOrganizeResult(
            phrases = dto.phrases.map { phrase ->
                WordPhraseCandidate(
                    phrase = phrase.phrase,
                    meaning = phrase.meaning,
                    example = phrase.example,
                    usageNote = phrase.usageNote,
                    register = phrase.register,
                    difficulty = phrase.difficulty,
                    confidence = phrase.confidence,
                    tags = phrase.tags.map { tag ->
                        WordPhraseTagCandidate(
                            name = tag.name,
                            description = tag.description
                        )
                    }
                )
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
