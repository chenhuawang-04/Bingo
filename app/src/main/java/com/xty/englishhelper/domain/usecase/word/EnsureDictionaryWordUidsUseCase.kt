package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.WordRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class EnsureDictionaryWordUidsUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val transactionRunner: TransactionRunner,
    private val wordUidNormalizer: DictionaryWordUidNormalizer
) {
    suspend operator fun invoke(dictionaryId: Long, dictionaryName: String): List<WordDetails> {
        val words = wordRepository.getWordsByDictionary(dictionaryId).first()
        val wordsMissingUid = words.filter { it.wordUid.isBlank() }
        if (wordsMissingUid.isEmpty()) return words

        val existingNonBlankUids = words.mapNotNull { it.wordUid.takeIf(String::isNotBlank) }.toSet()
        val generated = mutableMapOf<Long, String>()
        val duplicateGenerated = mutableSetOf<String>()
        wordsMissingUid.forEach { word ->
            val normalizedSpelling = word.normalizedSpelling.ifBlank { word.spelling.trim().lowercase() }
            val generatedUid = wordUidNormalizer.generateUid(
                dictionaryName = dictionaryName,
                spelling = normalizedSpelling
            )
            if (generatedUid in existingNonBlankUids) {
                throw IllegalStateException(
                    "辞书 $dictionaryName 中生成的 legacy wordUid 与已有词条冲突：${word.spelling}"
                )
            }
            if (generated.containsValue(generatedUid)) {
                duplicateGenerated += generatedUid
            }
            generated[word.id] = generatedUid
        }

        if (duplicateGenerated.isNotEmpty()) {
            throw IllegalStateException("辞书 $dictionaryName 中存在无法安全修复的空 wordUid 词条。")
        }

        transactionRunner.runInTransaction {
            wordsMissingUid.forEach { word ->
                val repairedUid = generated[word.id]
                    ?: throw IllegalStateException("修复辞书 $dictionaryName 的 wordUid 失败。")
                wordRepository.updateWord(
                    word.copy(wordUid = repairedUid)
                )
            }
        }

        return wordRepository.getWordsByDictionary(dictionaryId).first()
    }
}
