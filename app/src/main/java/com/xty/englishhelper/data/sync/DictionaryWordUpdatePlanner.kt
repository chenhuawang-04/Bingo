package com.xty.englishhelper.data.sync

import com.xty.englishhelper.domain.model.WordDetails
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryWordUpdatePlanner @Inject constructor() {

    data class Candidate(
        val existingWord: WordDetails,
        val finalWord: WordDetails
    )

    data class Plan(
        val temporaryRenameIds: Set<Long>,
        val finalUpdates: List<WordDetails>
    )

    fun plan(
        localWords: List<WordDetails>,
        candidates: List<Candidate>
    ): Plan {
        if (candidates.isEmpty()) {
            return Plan(
                temporaryRenameIds = emptySet(),
                finalUpdates = emptyList()
            )
        }

        val candidateIds = candidates.map { it.existingWord.id }
        val duplicateExistingIds = candidateIds
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateExistingIds.isNotEmpty()) {
            throw IllegalStateException(
                "同一本辞书中有多个云端更新映射到了同一条本地单词记录，无法安全同步。"
            )
        }

        val duplicateTargets = candidates
            .groupBy { it.finalWord.normalizedSpelling }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateTargets.isNotEmpty()) {
            throw IllegalStateException(
                "云端存在多个单词试图写入同一个拼写：${duplicateTargets.sorted().joinToString("、")}。"
            )
        }

        val affectedIds = candidateIds.toSet()
        val unaffectedByNormalized = localWords
            .filter { it.id !in affectedIds }
            .associateBy { it.normalizedSpelling }

        candidates.forEach { candidate ->
            val targetNormalized = candidate.finalWord.normalizedSpelling
            val conflicting = unaffectedByNormalized[targetNormalized]
            if (conflicting != null) {
                throw IllegalStateException(
                    "单词 ${candidate.finalWord.spelling} 与本地现有词条冲突，无法安全同步，请先处理同名词条。"
                )
            }
        }

        val temporaryRenameIds = candidates
            .filter { it.existingWord.normalizedSpelling != it.finalWord.normalizedSpelling }
            .map { it.existingWord.id }
            .toSet()

        return Plan(
            temporaryRenameIds = temporaryRenameIds,
            finalUpdates = candidates.map { it.finalWord }
        )
    }
}
