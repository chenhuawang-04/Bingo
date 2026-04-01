package com.xty.englishhelper.data.remote.cambridge

interface CambridgeService {
    suspend fun fetchAutocompleteJson(query: String): String
    suspend fun fetchEntryHtml(word: String): String
}
