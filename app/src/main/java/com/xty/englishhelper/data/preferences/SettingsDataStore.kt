package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        // Legacy global keys (kept for backward compatibility/migration fallback)
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val PROVIDER = stringPreferencesKey("provider")
        val ANTHROPIC_MODEL = stringPreferencesKey("anthropic_model")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val ANTHROPIC_BASE_URL = stringPreferencesKey("anthropic_base_url")
        val OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private fun lastSelectedUnitIdsKey(dictionaryId: Long) =
            stringPreferencesKey("last_selected_unit_ids_$dictionaryId")
    }

    val apiKey: Flow<String> = dataStore.data.map { prefs ->
        val provider = providerFromPrefs(prefs)
        encryptedApiKeyStore.getApiKey(provider)
    }
    val model: Flow<String> = dataStore.data.map { prefs ->
        val provider = providerFromPrefs(prefs)
        readModel(prefs, provider)
    }
    val baseUrl: Flow<String> = dataStore.data.map { prefs ->
        val provider = providerFromPrefs(prefs)
        readBaseUrl(prefs, provider)
    }
    val provider: Flow<AiProvider> = dataStore.data.map { prefs ->
        providerFromPrefs(prefs)
    }

    suspend fun setApiKey(key: String) {
        val provider = provider.first()
        encryptedApiKeyStore.setApiKey(provider, key)
    }

    suspend fun setApiKey(provider: AiProvider, key: String) {
        encryptedApiKeyStore.setApiKey(provider, key)
    }

    suspend fun setModel(model: String) {
        dataStore.edit { prefs ->
            val provider = providerFromPrefs(prefs)
            prefs[modelKey(provider)] = model
        }
    }

    suspend fun setModel(provider: AiProvider, model: String) {
        dataStore.edit { prefs ->
            prefs[modelKey(provider)] = model
        }
    }

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { prefs ->
            val provider = providerFromPrefs(prefs)
            prefs[baseUrlKey(provider)] = url
        }
    }

    suspend fun setBaseUrl(provider: AiProvider, url: String) {
        dataStore.edit { prefs ->
            prefs[baseUrlKey(provider)] = url
        }
    }

    suspend fun setProvider(provider: AiProvider) {
        dataStore.edit { prefs ->
            prefs[PROVIDER] = provider.name
            if (prefs[modelKey(provider)] == null) {
                prefs[modelKey(provider)] = defaultModel(provider)
            }
            if (prefs[baseUrlKey(provider)] == null) {
                prefs[baseUrlKey(provider)] = defaultBaseUrl(provider)
            }
        }
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

    private fun providerFromPrefs(prefs: Preferences): AiProvider {
        return when (prefs[PROVIDER]) {
            AiProvider.OPENAI_COMPATIBLE.name -> AiProvider.OPENAI_COMPATIBLE
            else -> AiProvider.ANTHROPIC
        }
    }

    private fun readModel(prefs: Preferences, provider: AiProvider): String {
        return prefs[modelKey(provider)] ?: prefs[MODEL] ?: defaultModel(provider)
    }

    private fun readBaseUrl(prefs: Preferences, provider: AiProvider): String {
        return prefs[baseUrlKey(provider)] ?: prefs[BASE_URL] ?: defaultBaseUrl(provider)
    }

    private fun modelKey(provider: AiProvider): Preferences.Key<String> {
        return when (provider) {
            AiProvider.ANTHROPIC -> ANTHROPIC_MODEL
            AiProvider.OPENAI_COMPATIBLE -> OPENAI_MODEL
        }
    }

    private fun baseUrlKey(provider: AiProvider): Preferences.Key<String> {
        return when (provider) {
            AiProvider.ANTHROPIC -> ANTHROPIC_BASE_URL
            AiProvider.OPENAI_COMPATIBLE -> OPENAI_BASE_URL
        }
    }

    private fun defaultModel(provider: AiProvider): String {
        return when (provider) {
            AiProvider.ANTHROPIC -> Constants.DEFAULT_MODEL
            AiProvider.OPENAI_COMPATIBLE -> Constants.DEFAULT_OPENAI_MODEL
        }
    }

    private fun defaultBaseUrl(provider: AiProvider): String {
        return when (provider) {
            AiProvider.ANTHROPIC -> Constants.ANTHROPIC_BASE_URL
            AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_BASE_URL
        }
    }
}
