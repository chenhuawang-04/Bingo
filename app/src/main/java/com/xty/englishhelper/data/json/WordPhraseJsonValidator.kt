package com.xty.englishhelper.data.json

import com.xty.englishhelper.domain.model.WordPhraseSource

object WordPhraseJsonValidator {
    private const val MAX_TAGS = 2_000
    private const val MAX_PHRASES_PER_WORD = 50
    private const val MAX_TAGS_PER_PHRASE = 12
    private const val MAX_UID_LENGTH = 128
    private const val MAX_TAG_NAME_LENGTH = 32
    private const val MAX_TAG_DESCRIPTION_LENGTH = 160
    private const val MAX_PHRASE_LENGTH = 120
    private const val MAX_MEANING_LENGTH = 240
    private const val MAX_EXAMPLE_LENGTH = 320
    private const val MAX_USAGE_NOTE_LENGTH = 320
    private const val MAX_SHORT_FIELD_LENGTH = 48
    private const val MAX_MODEL_LENGTH = 96

    fun validate(model: DictionaryJsonModel) {
        if (model.phraseTags.size > MAX_TAGS) {
            throw IllegalArgumentException("词组/短语标签数量过多：${model.phraseTags.size}")
        }

        val validWordUids = model.words.map { it.wordUid }.filter { it.isNotBlank() }.toSet()
        val maxPhrases = model.words.size.toLong() * MAX_PHRASES_PER_WORD.toLong()
        if (model.wordPhrases.size.toLong() > maxPhrases) {
            throw IllegalArgumentException(
                "词组/短语数量过多：${model.wordPhrases.size}（最多 ${maxPhrases}）"
            )
        }

        validateTags(model.phraseTags)
        validatePhrases(
            phrases = model.wordPhrases,
            validWordUids = validWordUids,
            validTagUids = model.phraseTags.map { it.tagUid }.toSet()
        )
    }

    private fun validateTags(tags: List<WordPhraseTagJsonModel>) {
        val tagUids = mutableSetOf<String>()
        val normalizedNames = mutableSetOf<String>()
        tags.forEachIndexed { index, tag ->
            val ordinal = index + 1
            requireBounded(tag.tagUid, "第 $ordinal 个词组标签 uid", 1, MAX_UID_LENGTH)
            requireBounded(tag.name, "第 $ordinal 个词组标签名称", 1, MAX_TAG_NAME_LENGTH)
            requireBounded(tag.description, "第 $ordinal 个词组标签说明", 0, MAX_TAG_DESCRIPTION_LENGTH)
            requireSource(tag.source, "第 $ordinal 个词组标签来源")
            requireNonNegativeTime(tag.createdAt, "第 $ordinal 个词组标签创建时间")
            requireNonNegativeTime(tag.updatedAt, "第 $ordinal 个词组标签更新时间")

            if (!tagUids.add(tag.tagUid)) {
                throw IllegalArgumentException("文件中存在重复词组标签 uid：${tag.tagUid}")
            }
            val normalized = normalizeName(tag.normalizedName.ifBlank { tag.name })
            if (!normalizedNames.add(normalized)) {
                throw IllegalArgumentException("文件中存在重复词组标签名称：${tag.name}")
            }
        }
    }

    private fun validatePhrases(
        phrases: List<WordPhraseJsonModel>,
        validWordUids: Set<String>,
        validTagUids: Set<String>
    ) {
        val phraseUids = mutableSetOf<String>()
        val normalizedByWord = mutableSetOf<Pair<String, String>>()
        val countByWord = mutableMapOf<String, Int>()

        phrases.forEachIndexed { index, phrase ->
            val ordinal = index + 1
            requireBounded(phrase.wordUid, "第 $ordinal 个词组/短语 wordUid", 1, MAX_UID_LENGTH)
            if (phrase.wordUid !in validWordUids) {
                throw IllegalArgumentException("第 $ordinal 个词组/短语引用了不存在的 wordUid：${phrase.wordUid}")
            }
            if (phrase.phraseUid.isNotBlank()) {
                requireBounded(phrase.phraseUid, "第 $ordinal 个词组/短语 uid", 1, MAX_UID_LENGTH)
                if (!phraseUids.add(phrase.phraseUid)) {
                    throw IllegalArgumentException("文件中存在重复词组/短语 uid：${phrase.phraseUid}")
                }
            }
            requireBounded(phrase.phrase, "第 $ordinal 个词组/短语内容", 1, MAX_PHRASE_LENGTH)
            requireBounded(phrase.meaning, "第 $ordinal 个词组/短语释义", 0, MAX_MEANING_LENGTH)
            requireBounded(phrase.example, "第 $ordinal 个词组/短语例句", 0, MAX_EXAMPLE_LENGTH)
            requireBounded(phrase.usageNote, "第 $ordinal 个词组/短语用法说明", 0, MAX_USAGE_NOTE_LENGTH)
            requireNullableBounded(phrase.register, "第 $ordinal 个词组/短语 register", MAX_SHORT_FIELD_LENGTH)
            requireNullableBounded(phrase.difficulty, "第 $ordinal 个词组/短语 difficulty", MAX_SHORT_FIELD_LENGTH)
            requireNullableBounded(phrase.model, "第 $ordinal 个词组/短语模型", MAX_MODEL_LENGTH)
            requireSource(phrase.source, "第 $ordinal 个词组/短语来源")
            require(phrase.confidence in 0f..1f) {
                "第 $ordinal 个词组/短语 confidence 无效：${phrase.confidence}"
            }
            requireNonNegativeTime(phrase.createdAt, "第 $ordinal 个词组/短语创建时间")
            requireNonNegativeTime(phrase.updatedAt, "第 $ordinal 个词组/短语更新时间")
            requireNonNegativeTime(phrase.organizedAt, "第 $ordinal 个词组/短语整理时间")
            if (phrase.tagUids.size > MAX_TAGS_PER_PHRASE) {
                throw IllegalArgumentException("第 $ordinal 个词组/短语标签过多：${phrase.tagUids.size}")
            }
            phrase.tagUids.forEach { tagUid ->
                requireBounded(tagUid, "第 $ordinal 个词组/短语 tagUid", 1, MAX_UID_LENGTH)
                if (tagUid !in validTagUids) {
                    throw IllegalArgumentException("词组/短语 ${phrase.phrase} 引用了不存在的 tagUid：$tagUid")
                }
            }

            val count = (countByWord[phrase.wordUid] ?: 0) + 1
            if (count > MAX_PHRASES_PER_WORD) {
                throw IllegalArgumentException("单词 ${phrase.wordUid} 的词组/短语数量过多：$count")
            }
            countByWord[phrase.wordUid] = count

            val normalized = normalizePhrase(phrase.normalizedPhrase.ifBlank { phrase.phrase })
            if (!normalizedByWord.add(phrase.wordUid to normalized)) {
                throw IllegalArgumentException("单词 ${phrase.wordUid} 存在重复词组/短语：${phrase.phrase}")
            }
        }
    }

    private fun requireBounded(value: String, label: String, minLength: Int, maxLength: Int) {
        val length = value.trim().length
        require(length >= minLength) { "$label 为空" }
        require(length <= maxLength) { "$label 过长：$length（最多 $maxLength）" }
    }

    private fun requireNullableBounded(value: String?, label: String, maxLength: Int) {
        val length = value?.trim()?.length ?: return
        require(length <= maxLength) { "$label 过长：$length（最多 $maxLength）" }
    }

    private fun requireSource(value: String, label: String) {
        val normalized = value.trim().ifBlank { WordPhraseSource.AI.name }
        require(WordPhraseSource.entries.any { it.name == normalized }) {
            "$label 无效：$value"
        }
    }

    private fun requireNonNegativeTime(value: Long, label: String) {
        require(value >= 0L) { "$label 无效：$value" }
    }

    private fun normalizeName(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().lowercase()

    private fun normalizePhrase(raw: String): String =
        raw.replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ',', ';', ':')
            .lowercase()
}
