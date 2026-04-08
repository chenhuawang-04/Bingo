package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.QuickDictionaryEntry

interface OedDictionaryRepository {
    suspend fun lookupWord(word: String): List<QuickDictionaryEntry>
}
