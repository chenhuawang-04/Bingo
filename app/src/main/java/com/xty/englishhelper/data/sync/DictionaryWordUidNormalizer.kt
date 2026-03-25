package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.UnitJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryWordUidNormalizer @Inject constructor() {

    fun normalize(model: DictionaryJsonModel): DictionaryJsonModel {
        val resolvedWords = resolveWords(model)
        val validWordUids = resolvedWords.map { it.wordUid }.toSet()
        val blankWordUidReplacement = blankWordUidReplacement(model, resolvedWords)

        return model.copy(
            words = resolvedWords,
            units = model.units.map { unit ->
                unit.copy(
                    wordUids = unit.wordUids.map { uid ->
                        resolveRequiredReference(
                            dictionaryName = model.name,
                            referenceType = "单元",
                            referenceValue = uid,
                            validWordUids = validWordUids,
                            blankWordUidReplacement = blankWordUidReplacement
                        )
                    }
                )
            },
            studyStates = normalizeStudyStates(
                dictionaryName = model.name,
                studyStates = model.studyStates,
                validWordUids = validWordUids,
                blankWordUidReplacement = blankWordUidReplacement
            ),
            wordPools = model.wordPools.map { pool ->
                pool.copy(
                    focusWordUid = pool.focusWordUid?.takeIf { it.isNotBlank() }?.let { focusUid ->
                        resolveRequiredReference(
                            dictionaryName = model.name,
                            referenceType = "词池焦点词",
                            referenceValue = focusUid,
                            validWordUids = validWordUids,
                            blankWordUidReplacement = blankWordUidReplacement
                        )
                    },
                    memberWordUids = pool.memberWordUids.map { uid ->
                        resolveRequiredReference(
                            dictionaryName = model.name,
                            referenceType = "词池成员",
                            referenceValue = uid,
                            validWordUids = validWordUids,
                            blankWordUidReplacement = blankWordUidReplacement
                        )
                    }
                )
            }
        )
    }

    fun generateUid(dictionaryName: String, spelling: String): String {
        val normalizedSpelling = spelling.trim().lowercase()
        require(normalizedSpelling.isNotBlank()) { "Cannot generate wordUid for blank spelling" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$dictionaryName\u0000$normalizedSpelling".toByteArray(Charsets.UTF_8))
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return "legacy-$hex"
    }

    private fun resolveWords(model: DictionaryJsonModel): List<WordJsonModel> {
        val resolved = model.words.map { word ->
            val resolvedUid = word.wordUid.ifBlank { generateUid(model.name, word.spelling) }
            word.copy(wordUid = resolvedUid)
        }

        val duplicateUids = resolved
            .groupBy { it.wordUid }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateUids.isNotEmpty()) {
            throw IllegalArgumentException("文件中存在重复 wordUid：${duplicateUids.sorted().joinToString("、")}")
        }

        return resolved
    }

    private fun blankWordUidReplacement(
        originalModel: DictionaryJsonModel,
        normalizedWords: List<WordJsonModel>
    ): String? {
        val blankWords = originalModel.words.withIndex().filter { it.value.wordUid.isBlank() }
        return when (blankWords.size) {
            0 -> null
            1 -> normalizedWords[blankWords.single().index].wordUid
            else -> null
        }
    }

    private fun normalizeStudyStates(
        dictionaryName: String,
        studyStates: List<StudyStateJsonModel>,
        validWordUids: Set<String>,
        blankWordUidReplacement: String?
    ): List<StudyStateJsonModel> {
        val normalized = studyStates.map { state ->
            state.copy(
                wordUid = resolveRequiredReference(
                    dictionaryName = dictionaryName,
                    referenceType = "学习状态",
                    referenceValue = state.wordUid,
                    validWordUids = validWordUids,
                    blankWordUidReplacement = blankWordUidReplacement
                )
            )
        }

        val duplicateStudyStates = normalized
            .groupBy { it.wordUid }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateStudyStates.isNotEmpty()) {
            throw IllegalArgumentException(
                "文件中同一单词存在多条学习状态记录：${duplicateStudyStates.sorted().joinToString("、")}"
            )
        }

        return normalized
    }

    private fun resolveRequiredReference(
        dictionaryName: String,
        referenceType: String,
        referenceValue: String,
        validWordUids: Set<String>,
        blankWordUidReplacement: String?
    ): String {
        if (referenceValue.isBlank()) {
            return blankWordUidReplacement
                ?: throw IllegalArgumentException(
                    "辞书 $dictionaryName 的 $referenceType 引用了空 wordUid，但词条中没有且仅有一个可修复的空 wordUid。"
                )
        }
        if (referenceValue !in validWordUids) {
            throw IllegalArgumentException(
                "辞书 $dictionaryName 的 $referenceType 引用了不存在的 wordUid：$referenceValue"
            )
        }
        return referenceValue
    }
}
