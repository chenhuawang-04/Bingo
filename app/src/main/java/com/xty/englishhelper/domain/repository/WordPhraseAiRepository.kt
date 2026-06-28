package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseOrganizeResult
import com.xty.englishhelper.domain.model.WordPhraseTag

interface WordPhraseAiRepository {
    suspend fun organizeWordPhrases(
        word: WordDetails,
        existingTags: List<WordPhraseTag>,
        maxPhrases: Int,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): WordPhraseOrganizeResult
}
