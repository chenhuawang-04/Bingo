package com.xty.englishhelper.data.repository.pool

import android.util.Log
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiSettingsScope
import kotlinx.coroutines.CancellationException

/**
 * Temporary AI-powered classifier that categorizes dictionary entries into
 * entry_type (word/root/phrase).
 *
 * BUG 3 fix: count and query only words with entry_type IS NULL.
 * BUG 10 fix: includes meaningsJson and rootExplanation in the prompt.
 *
 * THIS CLASS SHOULD BE REMOVED after all dictionaries are classified.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 */
class EntryTypeClassifier @javax.inject.Inject constructor(
    private val wordDao: WordDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * Classify all unclassified words in the dictionary.
     * @return total number of words classified in this run
     */
    suspend fun classify(
        dictionaryId: Long,
        isCancelled: () -> Boolean,
        onProgress: (classified: Int, total: Int) -> Unit
    ): Int {
        // BUG 3 fix: count only words with entry_type IS NULL
        val totalWords = wordDao.countWordsWithoutEntryType(dictionaryId)
        if (totalWords == 0) return 0

        var totalClassified = 0
        var lastId = 0L
        val batchSize = 50

        while (true) {
            if (isCancelled()) break

            // BUG 3 fix: query only unclassified words (WHERE entry_type IS NULL)
            val batch = wordDao.getWordsForClassification(dictionaryId, lastId, batchSize)
            if (batch.isEmpty()) break

            val prompt = EdgePromptBuilder.buildEntryTypePrompt(batch)
            val response = try {
                callAi(prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("EntryTypeClassifier", "Entry type classification batch failed", e)
                break
            }

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
