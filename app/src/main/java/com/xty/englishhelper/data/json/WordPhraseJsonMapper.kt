package com.xty.englishhelper.data.json

import com.xty.englishhelper.domain.model.WordPhrase
import com.xty.englishhelper.domain.model.WordPhraseSource
import com.xty.englishhelper.domain.model.WordPhraseSyncItem
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordPhraseTag

fun WordPhraseSyncSnapshot.toPhraseTagJsonModels(): List<WordPhraseTagJsonModel> =
    tags.map { tag ->
        WordPhraseTagJsonModel(
            tagUid = tag.tagUid,
            name = tag.name,
            normalizedName = tag.normalizedName,
            description = tag.description,
            source = tag.source,
            createdAt = tag.createdAt,
            updatedAt = tag.updatedAt
        )
    }

fun WordPhraseSyncSnapshot.toWordPhraseJsonModels(): List<WordPhraseJsonModel> =
    phrases.map { item ->
        val phrase = item.phrase
        WordPhraseJsonModel(
            phraseUid = phrase.phraseUid,
            wordUid = item.wordUid,
            phrase = phrase.phrase,
            normalizedPhrase = phrase.normalizedPhrase,
            meaning = phrase.meaning,
            example = phrase.example,
            usageNote = phrase.usageNote,
            register = phrase.register,
            difficulty = phrase.difficulty,
            confidence = phrase.confidence,
            source = phrase.source,
            model = phrase.model,
            practiceCount = phrase.practiceCount,
            createdAt = phrase.createdAt,
            updatedAt = phrase.updatedAt,
            organizedAt = phrase.organizedAt,
            tagUids = item.tagUids
        )
    }

fun wordPhraseSyncSnapshotFromJson(
    phraseTags: List<WordPhraseTagJsonModel>,
    wordPhrases: List<WordPhraseJsonModel>
): WordPhraseSyncSnapshot {
    return WordPhraseSyncSnapshot(
        tags = phraseTags
            .filter { it.name.isNotBlank() }
            .map { tag ->
                WordPhraseTag(
                    id = 0,
                    tagUid = tag.tagUid,
                    dictionaryId = 0,
                    name = tag.name,
                    normalizedName = tag.normalizedName,
                    description = tag.description,
                    source = tag.source.ifBlank { WordPhraseSource.AI.name },
                    createdAt = tag.createdAt,
                    updatedAt = tag.updatedAt
                )
            },
        phrases = wordPhrases
            .filter { it.wordUid.isNotBlank() && it.phrase.isNotBlank() }
            .map { item ->
                WordPhraseSyncItem(
                    wordUid = item.wordUid,
                    tagUids = item.tagUids.filter { it.isNotBlank() }.distinct(),
                    phrase = WordPhrase(
                        id = 0,
                        phraseUid = item.phraseUid,
                        wordId = 0,
                        dictionaryId = 0,
                        phrase = item.phrase,
                        normalizedPhrase = item.normalizedPhrase,
                        meaning = item.meaning,
                        example = item.example,
                        usageNote = item.usageNote,
                        register = item.register,
                        difficulty = item.difficulty,
                        confidence = item.confidence,
                        source = item.source.ifBlank { WordPhraseSource.AI.name },
                        model = item.model,
                        practiceCount = item.practiceCount,
                        createdAt = item.createdAt,
                        updatedAt = item.updatedAt,
                        organizedAt = item.organizedAt
                    )
                )
            }
    )
}
