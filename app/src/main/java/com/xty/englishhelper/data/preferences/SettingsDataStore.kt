package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiSettingsScope
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
        val FAST_ANTHROPIC_MODEL = stringPreferencesKey("fast_anthropic_model")
        val FAST_OPENAI_MODEL = stringPreferencesKey("fast_openai_model")
        val GITHUB_OWNER = stringPreferencesKey("github_owner")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val GUARDIAN_DETAIL_CONCURRENCY = intPreferencesKey("guardian_detail_concurrency")
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

    val fastModel: Flow<String> = dataStore.data.map { prefs ->
        val provider = providerFromPrefs(prefs)
        readFastModel(prefs, provider)
    }

    val guardianDetailConcurrency: Flow<Int> = dataStore.data.map { prefs ->
        prefs[GUARDIAN_DETAIL_CONCURRENCY] ?: 5
    }

    suspend fun setGuardianDetailConcurrency(value: Int) {
        dataStore.edit { prefs ->
            prefs[GUARDIAN_DETAIL_CONCURRENCY] = value
        }
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

    suspend fun setFastModel(model: String) {
        dataStore.edit { prefs ->
            val provider = providerFromPrefs(prefs)
            prefs[fastModelKey(provider)] = model
        }
    }

    suspend fun setFastModel(provider: AiProvider, model: String) {
        dataStore.edit { prefs ->
            prefs[fastModelKey(provider)] = model
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

    // ── Scoped AI config ──

    data class AiConfig(
        val provider: AiProvider,
        val apiKey: String,
        val model: String,
        val baseUrl: String
    )

    suspend fun getFastAiConfig(): AiConfig {
        val prefs = dataStore.data.first()
        val p = providerFromPrefs(prefs)
        return AiConfig(
            provider = p,
            apiKey = encryptedApiKeyStore.getApiKey(p),
            model = readFastModel(prefs, p),
            baseUrl = readBaseUrl(prefs, p)
        )
    }

    suspend fun getAiConfig(scope: AiSettingsScope): AiConfig {
        if (scope == AiSettingsScope.MAIN) {
            val prefs = dataStore.data.first()
            val p = providerFromPrefs(prefs)
            return AiConfig(
                provider = p,
                apiKey = encryptedApiKeyStore.getApiKey(p),
                model = readModel(prefs, p),
                baseUrl = readBaseUrl(prefs, p)
            )
        }
        val prefs = dataStore.data.first()
        val scopedProviderStr = prefs[stringPreferencesKey("${scope.prefix}provider")]
            ?: return getAiConfig(AiSettingsScope.MAIN) // not configured, fallback
        val scopedProvider = try { AiProvider.valueOf(scopedProviderStr) } catch (_: Exception) {
            return getAiConfig(AiSettingsScope.MAIN)
        }
        val scopedApiKey = encryptedApiKeyStore.getApiKey(scope, scopedProvider)
        if (scopedApiKey.isBlank()) return getAiConfig(AiSettingsScope.MAIN) // no key, fallback
        val scopedModel = prefs[stringPreferencesKey("${scope.prefix}model_${scopedProvider.name.lowercase()}")] ?: defaultModel(scopedProvider)
        val scopedBaseUrl = prefs[stringPreferencesKey("${scope.prefix}base_url_${scopedProvider.name.lowercase()}")] ?: defaultBaseUrl(scopedProvider)
        return AiConfig(
            provider = scopedProvider,
            apiKey = scopedApiKey,
            model = scopedModel,
            baseUrl = scopedBaseUrl
        )
    }

    fun scopedProvider(scope: AiSettingsScope): Flow<AiProvider?> = dataStore.data.map { prefs ->
        val raw = prefs[stringPreferencesKey("${scope.prefix}provider")]
        raw?.let { try { AiProvider.valueOf(it) } catch (_: Exception) { null } }
    }

    fun scopedApiKey(scope: AiSettingsScope): Flow<String> = dataStore.data.map { prefs ->
        val providerStr = prefs[stringPreferencesKey("${scope.prefix}provider")] ?: return@map ""
        val p = try { AiProvider.valueOf(providerStr) } catch (_: Exception) { return@map "" }
        encryptedApiKeyStore.getApiKey(scope, p)
    }

    fun scopedModel(scope: AiSettingsScope): Flow<String> = dataStore.data.map { prefs ->
        val providerStr = prefs[stringPreferencesKey("${scope.prefix}provider")] ?: return@map ""
        val p = try { AiProvider.valueOf(providerStr) } catch (_: Exception) { return@map "" }
        prefs[stringPreferencesKey("${scope.prefix}model_${p.name.lowercase()}")] ?: defaultModel(p)
    }

    fun scopedBaseUrl(scope: AiSettingsScope): Flow<String> = dataStore.data.map { prefs ->
        val providerStr = prefs[stringPreferencesKey("${scope.prefix}provider")] ?: return@map ""
        val p = try { AiProvider.valueOf(providerStr) } catch (_: Exception) { return@map "" }
        prefs[stringPreferencesKey("${scope.prefix}base_url_${p.name.lowercase()}")] ?: defaultBaseUrl(p)
    }

    suspend fun setScopedProvider(scope: AiSettingsScope, provider: AiProvider) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("${scope.prefix}provider")] = provider.name
        }
    }

    suspend fun setScopedApiKey(scope: AiSettingsScope, provider: AiProvider, key: String) {
        encryptedApiKeyStore.setApiKey(scope, provider, key)
    }

    suspend fun setScopedModel(scope: AiSettingsScope, provider: AiProvider, model: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("${scope.prefix}model_${provider.name.lowercase()}")] = model
        }
    }

    suspend fun setScopedBaseUrl(scope: AiSettingsScope, provider: AiProvider, url: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("${scope.prefix}base_url_${provider.name.lowercase()}")] = url
        }
    }

    suspend fun clearScopedSettings(scope: AiSettingsScope) {
        if (scope == AiSettingsScope.MAIN) return
        encryptedApiKeyStore.clearScopedApiKeys(scope)
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("${scope.prefix}provider"))
            AiProvider.entries.forEach { p ->
                prefs.remove(stringPreferencesKey("${scope.prefix}model_${p.name.lowercase()}"))
                prefs.remove(stringPreferencesKey("${scope.prefix}base_url_${p.name.lowercase()}"))
            }
        }
    }

    // ── GitHub Sync config ──

    val githubOwner: Flow<String> = dataStore.data.map { it[GITHUB_OWNER] ?: "" }
    val githubRepo: Flow<String> = dataStore.data.map { it[GITHUB_REPO] ?: "" }
    val lastSyncAt: Flow<Long> = dataStore.data.map { it[LAST_SYNC_AT] ?: 0L }

    suspend fun setGitHubOwner(owner: String) {
        dataStore.edit { it[GITHUB_OWNER] = owner }
    }

    suspend fun setGitHubRepo(repo: String) {
        dataStore.edit { it[GITHUB_REPO] = repo }
    }

    suspend fun setLastSyncAt(timestamp: Long) {
        dataStore.edit { it[LAST_SYNC_AT] = timestamp }
    }

    fun getGitHubPat(): String = encryptedApiKeyStore.getGitHubPat()

    fun setGitHubPat(token: String) = encryptedApiKeyStore.setGitHubPat(token)

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

    private fun readFastModel(prefs: Preferences, provider: AiProvider): String {
        return prefs[fastModelKey(provider)] ?: readModel(prefs, provider)
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

    private fun fastModelKey(provider: AiProvider): Preferences.Key<String> {
        return when (provider) {
            AiProvider.ANTHROPIC -> FAST_ANTHROPIC_MODEL
            AiProvider.OPENAI_COMPATIBLE -> FAST_OPENAI_MODEL
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
