package com.xty.englishhelper.data.repository.pool

import android.util.Log
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiSettingsScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Temporary AI-powered classifier that categorizes dictionary entries into
 * entry_type (word/root/phrase).
 *
 * BUG 3 fix: count and query only words with entry_type IS NULL.
 * BUG 10 fix: includes meaningsJson and rootExplanation in the prompt.
 * N8 fix: retry with exponential backoff on transient AI failures.
 *
 * THIS CLASS SHOULD BE REMOVED after all dictionaries are classified.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 */
class EntryTypeClassifier @javax.inject.Inject constructor(
    private val wordDao: WordDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_RETRIES = 3
    }

    /**
     * Classify all words in the dictionary.
     * First resets all entry_type to NULL, then reclassifies using AI.
     * @return total number of words classified in this run
     */
    suspend fun classify(
        dictionaryId: Long,
        isCancelled: () -> Boolean,
        onProgress: (classified: Int, total: Int) -> Unit
    ): Int {
        // Reset all entry_type to NULL for reclassification
        wordDao.resetEntryTypes(dictionaryId)

        // Count all words in dictionary
        val totalWords = wordDao.countAllWords(dictionaryId)
        if (totalWords == 0) return 0

        var totalClassified = 0
        var lastId = 0L
        val batchSize = 50

        while (true) {
            if (isCancelled()) break

            // Query all words for classification
            val batch = wordDao.getWordsForClassification(dictionaryId, lastId, batchSize)
            if (batch.isEmpty()) break

            val prompt = EdgePromptBuilder.buildEntryTypePrompt(batch)
            val response = callAiWithRetry(prompt) ?: break

            val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
            val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
            val normalized = EdgeParser.normalizeResponse(response, unwrapEnabled, repairEnabled)

            val results = EdgeParser.parseEntryTypeResponse(normalized)
            val resultMap = results.associateBy { it.first }

            batch.forEach { word ->
                val entryType = resultMap[word.id]
                if (entryType != null) {
                    wordDao.updateEntryType(word.id, entryType.second)
                    totalClassified++
                }
            }

            lastId = batch.last().id
            onProgress(totalClassified, totalWords)
        }

        return totalClassified
    }

    /**
     * N8 fix: retry AI call with exponential backoff on transient failures.
     * Returns null if all retries are exhausted or the error is non-retryable.
     */
    private suspend fun callAiWithRetry(prompt: String): String? {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                return callAi(prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                val retryable = isRetryableError(e)
                Log.w("EntryTypeClassifier", "AI call attempt ${attempt + 1}/$MAX_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) return null
                if (attempt < MAX_RETRIES - 1) {
                    val baseDelay = if (isRateLimitError(e)) 2000L else 1000L
                    delay(baseDelay * (attempt + 1))
                }
            }
        }
        Log.w("EntryTypeClassifier", "AI call failed after $MAX_RETRIES attempts", lastException)
        return null
    }

    private fun isRetryableError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") || msg.contains("invalid api key")) {
            return false
        }
        if (isRateLimitError(e)) return true
        if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) return true
        if (e is java.io.IOException && (msg.contains("timeout") || msg.contains("connect"))) return true
        if (e is kotlinx.coroutines.TimeoutCancellationException) return true
        if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) return true
        return false
    }

    private fun isRateLimitError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("429") || msg.contains("rate limit")
    }

    private suspend fun callAi(prompt: String): String {
        val config = settingsDataStore.getAiConfig(AiSettingsScope.POOL)
        val client = aiApiClientProvider.getClient(config.provider)
        return client.sendMessage(
            url = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 4096
        )
    }
}
