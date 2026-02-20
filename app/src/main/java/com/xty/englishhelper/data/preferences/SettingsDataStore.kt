package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xty.englishhelper.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptedApiKeyStore: EncryptedApiKeyStore
) {
    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        private fun lastSelectedUnitIdsKey(dictionaryId: Long) =
            stringPreferencesKey("last_selected_unit_ids_$dictionaryId")
    }

    val apiKey: Flow<String> = flow { emit(encryptedApiKeyStore.getApiKey()) }
    val model: Flow<String> = dataStore.data.map { it[MODEL] ?: Constants.DEFAULT_MODEL }
    val baseUrl: Flow<String> = dataStore.data.map { it[BASE_URL] ?: Constants.ANTHROPIC_BASE_URL }

    suspend fun setApiKey(key: String) {
        encryptedApiKeyStore.setApiKey(key)
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[MODEL] = model }
    }

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { it[BASE_URL] = url }
    }

    suspend fun getLastSelectedUnitIds(dictionaryId: Long): Set<Long> {
        val raw = dataStore.data.first()[lastSelectedUnitIdsKey(dictionaryId)] ?: return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setLastSelectedUnitIds(dictionaryId: Long, ids: Set<Long>) {
        dataStore.edit { it[lastSelectedUnitIdsKey(dictionaryId)] = ids.joinToString(",") }
    }

    /**
     * Migrates plaintext API key from DataStore to EncryptedSharedPreferences.
     * Should be called once during app startup.
     */
    suspend fun migrateApiKeyIfNeeded() {
        val prefs = dataStore.data.first()
        val oldKey = prefs[API_KEY]
        if (!oldKey.isNullOrBlank()) {
            encryptedApiKeyStore.setApiKey(oldKey)
            dataStore.edit { it.remove(API_KEY) }
        }
    }
}
