package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.CambridgeEntry

interface CambridgeDictionaryRepository {
    suspend fun searchSuggestions(query: String): List<String>
    suspend fun fetchEntry(word: String): CambridgeEntry
}
