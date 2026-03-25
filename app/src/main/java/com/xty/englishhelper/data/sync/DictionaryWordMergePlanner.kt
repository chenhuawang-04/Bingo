package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.WordJsonModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryWordMergePlanner @Inject constructor() {

    data class CloudWordUpdate(
        val cloudWord: WordJsonModel,
        val localWordUid: String?,
        val localNormalizedSpelling: String
    )

    data class Plan(
        val cloudOnlyWords: List<WordJsonModel>,
        val cloudUpdates: List<CloudWordUpdate>
    ) {
        val cloudNewerWords: List<WordJsonModel>
            get() = cloudUpdates.map { it.cloudWord }

        val hasWordChanges: Boolean
            get() = cloudOnlyWords.isNotEmpty() || cloudUpdates.isNotEmpty()
    }

    fun plan(
        localWords: List<WordJsonModel>,
        cloudWords: List<WordJsonModel>
    ): Plan {
        val localByUid = localWords
            .filter { it.wordUid.isNotBlank() }
            .associateBy { it.wordUid }
        val localByNormalized = localWords.associateBy(::normalizedSpelling)

        val cloudOnly = mutableListOf<WordJsonModel>()
        val cloudUpdates = mutableListOf<CloudWordUpdate>()

        cloudWords.forEach { cloudWord ->
            val localWord = cloudWord.wordUid
                .takeIf { it.isNotBlank() }
                ?.let(localByUid::get)
                ?: localByNormalized[normalizedSpelling(cloudWord)]

            when {
                localWord == null -> cloudOnly += cloudWord
                shouldPreferCloud(localWord, cloudWord) -> {
                    cloudUpdates += CloudWordUpdate(
                        cloudWord = cloudWord,
                        localWordUid = localWord.wordUid.takeIf { it.isNotBlank() },
                        localNormalizedSpelling = normalizedSpelling(localWord)
                    )
                }
            }
        }

        return Plan(
            cloudOnlyWords = cloudOnly,
            cloudUpdates = cloudUpdates
        )
    }

    private fun shouldPreferCloud(
        local: WordJsonModel,
        cloud: WordJsonModel
    ): Boolean {
        if (local == cloud) return false

        val localUpdatedAt = local.updatedAt.takeIf { it > 0 }
        val cloudUpdatedAt = cloud.updatedAt.takeIf { it > 0 }
        if (localUpdatedAt != null && cloudUpdatedAt != null) {
            return cloudUpdatedAt > localUpdatedAt
        }
        if (localUpdatedAt == null && cloudUpdatedAt != null) {
            return true
        }
        if (localUpdatedAt != null && cloudUpdatedAt == null) {
            return false
        }

        return completenessScore(cloud) > completenessScore(local)
    }

    private fun completenessScore(word: WordJsonModel): Int {
        var score = 0
        if (word.phonetic.isNotBlank()) score += 2
        score += word.meanings.count { it.definition.isNotBlank() }
        if (word.rootExplanation.isNotBlank()) score += 2
        score += word.decomposition.count { it.segment.isNotBlank() || it.meaning.isNotBlank() }
        score += word.synonyms.count { it.word.isNotBlank() }
        score += word.similarWords.count { it.word.isNotBlank() }
        score += word.cognates.count { it.word.isNotBlank() }
        score += word.inflections.count { it.form.isNotBlank() }
        return score
    }

    private fun normalizedSpelling(word: WordJsonModel): String {
        return word.spelling.trim().lowercase()
    }
}
