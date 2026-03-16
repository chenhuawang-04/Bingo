package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiProviderProfile
import com.xty.englishhelper.domain.model.AiScopeConfig
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

        val AI_PROVIDERS_JSON = stringPreferencesKey("ai_providers_v2")
        val AI_DEFAULT_PROVIDER = stringPreferencesKey("ai_default_provider_v2")
        val AI_SCOPE_CONFIGS_JSON = stringPreferencesKey("ai_scope_configs_v2")
        val AI_DEBUG_MODE = booleanPreferencesKey("ai_debug_mode")
        val AI_RESPONSE_UNWRAP_ENABLED = booleanPreferencesKey("ai_response_unwrap_enabled")
        val IMAGE_COMPRESSION_ENABLED = booleanPreferencesKey("image_compression_enabled")
        val IMAGE_COMPRESSION_TARGET_BYTES = intPreferencesKey("image_compression_target_bytes")

        val GITHUB_OWNER = stringPreferencesKey("github_owner")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val GUARDIAN_DETAIL_CONCURRENCY = intPreferencesKey("guardian_detail_concurrency")
        val ONLINE_READING_SOURCE = stringPreferencesKey("online_reading_source")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_LOCALE = stringPreferencesKey("tts_locale")
        val TTS_AUTO_STUDY = booleanPreferencesKey("tts_auto_study")
        val TTS_PREWARM_CONCURRENCY = intPreferencesKey("tts_prewarm_concurrency")
        val TTS_PREWARM_RETRY = intPreferencesKey("tts_prewarm_retry")
        private fun lastSelectedUnitIdsKey(dictionaryId: Long) =
            stringPreferencesKey("last_selected_unit_ids_$dictionaryId")
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class AiConfig(
        val providerName: String,
        val provider: AiProvider,
        val apiKey: String,
        val model: String,
        val baseUrl: String
    )

    data class AiProviderSummary(
        val profile: AiProviderProfile,
        val hasApiKey: Boolean
    )

    data class TtsConfig(
        val rate: Float,
        val pitch: Float,
        val localeTag: String,
        val prewarmConcurrency: Int,
        val prewarmRetry: Int
    )

    data class ImageCompressionConfig(
        val enabled: Boolean,
        val targetBytes: Int
    )

    private val defaultImageCompressionTargetBytes = 1_000_000
    private val minImageCompressionTargetBytes = 200 * 1024
    private val maxImageCompressionTargetBytes = 4 * 1024 * 1024

    val providers: Flow<List<AiProviderProfile>> = dataStore.data.map { prefs ->
        readProviders(prefs)
    }

    val providersWithKeys: Flow<List<AiProviderSummary>> = providers.map { list ->
        list.map { profile ->
            AiProviderSummary(profile, encryptedApiKeyStore.hasApiKey(profile.name))
        }
    }

    val defaultProviderName: Flow<String> = dataStore.data.map { prefs ->
        readDefaultProviderName(prefs)
    }

    fun scopeConfig(scope: AiSettingsScope): Flow<AiScopeConfig> = dataStore.data.map { prefs ->
        readScopeConfig(prefs, scope)
    }

    val guardianDetailConcurrency: Flow<Int> = dataStore.data.map { prefs ->
        prefs[GUARDIAN_DETAIL_CONCURRENCY] ?: 5
    }

    val onlineReadingSource: Flow<String> = dataStore.data.map { prefs ->
        prefs[ONLINE_READING_SOURCE] ?: OnlineReadingSource.GUARDIAN.key
    }

    val ttsRate: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_RATE] ?: 1.0f
    }

    val ttsPitch: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_PITCH] ?: 1.0f
    }

    val ttsLocale: Flow<String> = dataStore.data.map { prefs ->
        prefs[TTS_LOCALE] ?: "system"
    }

    val ttsAutoStudy: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TTS_AUTO_STUDY] ?: true
    }

    val aiDebugMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AI_DEBUG_MODE] ?: false
    }

    val aiResponseUnwrapEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AI_RESPONSE_UNWRAP_ENABLED] ?: false
    }

    val imageCompressionEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[IMAGE_COMPRESSION_ENABLED] ?: true
    }

    val imageCompressionTargetBytes: Flow<Int> = dataStore.data.map { prefs ->
        clampImageCompressionTarget(prefs[IMAGE_COMPRESSION_TARGET_BYTES] ?: defaultImageCompressionTargetBytes)
    }

    val ttsPrewarmConcurrency: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TTS_PREWARM_CONCURRENCY] ?: 2
    }

    val ttsPrewarmRetry: Flow<Int> = dataStore.data.map { prefs ->
        prefs[TTS_PREWARM_RETRY] ?: 2
    }

    suspend fun setGuardianDetailConcurrency(value: Int) {
        dataStore.edit { prefs ->
            prefs[GUARDIAN_DETAIL_CONCURRENCY] = value
        }
    }

    suspend fun setOnlineReadingSource(value: String) {
        dataStore.edit { prefs ->
            prefs[ONLINE_READING_SOURCE] = value
        }
    }

    suspend fun setTtsRate(value: Float) {
        dataStore.edit { prefs -> prefs[TTS_RATE] = value }
    }

    suspend fun setTtsPitch(value: Float) {
        dataStore.edit { prefs -> prefs[TTS_PITCH] = value }
    }

    suspend fun setTtsLocale(value: String) {
        dataStore.edit { prefs -> prefs[TTS_LOCALE] = value }
    }

    suspend fun setTtsAutoStudy(value: Boolean) {
        dataStore.edit { prefs -> prefs[TTS_AUTO_STUDY] = value }
    }

    suspend fun setAiDebugMode(value: Boolean) {
        dataStore.edit { prefs -> prefs[AI_DEBUG_MODE] = value }
    }

    suspend fun setAiResponseUnwrapEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[AI_RESPONSE_UNWRAP_ENABLED] = value }
    }

    suspend fun setImageCompressionEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[IMAGE_COMPRESSION_ENABLED] = value }
    }

    suspend fun setImageCompressionTargetBytes(value: Int) {
        dataStore.edit { prefs ->
            prefs[IMAGE_COMPRESSION_TARGET_BYTES] = clampImageCompressionTarget(value)
        }
    }

    suspend fun setTtsPrewarmConcurrency(value: Int) {
        dataStore.edit { prefs -> prefs[TTS_PREWARM_CONCURRENCY] = value }
    }

    suspend fun setTtsPrewarmRetry(value: Int) {
        dataStore.edit { prefs -> prefs[TTS_PREWARM_RETRY] = value }
    }

    suspend fun getProviders(): List<AiProviderProfile> {
        return readProviders(dataStore.data.first())
    }

    suspend fun getProvider(providerName: String): AiProviderProfile? {
        return getProviders().firstOrNull { it.name == providerName }
    }

    suspend fun addProvider(profile: AiProviderProfile) {
        dataStore.edit { prefs ->
            val providers = readProviders(prefs).toMutableList()
            if (providers.any { it.name.equals(profile.name, ignoreCase = true) }) {
                return@edit
            }
            val normalized = profile.copy(baseUrl = normalizeBaseUrl(profile.baseUrl, profile.provider))
            providers.add(normalized)
            prefs[AI_PROVIDERS_JSON] = json.encodeToString(providers)
            val currentDefault = prefs[AI_DEFAULT_PROVIDER]
            if (currentDefault.isNullOrBlank()) {
                prefs[AI_DEFAULT_PROVIDER] = normalized.name
            }
        }
    }

    suspend fun updateProvider(profile: AiProviderProfile) {
        dataStore.edit { prefs ->
            val providers = readProviders(prefs).toMutableList()
            val index = providers.indexOfFirst { it.name == profile.name }
            if (index < 0) return@edit
            providers[index] = profile.copy(baseUrl = normalizeBaseUrl(profile.baseUrl, profile.provider))
            prefs[AI_PROVIDERS_JSON] = json.encodeToString(providers)
        }
    }

    suspend fun deleteProvider(providerName: String): Boolean {
        var removed = false
        dataStore.edit { prefs ->
            val providers = readProviders(prefs).toMutableList()
            val index = providers.indexOfFirst { it.name == providerName }
            if (index < 0) return@edit
            if (providers.size <= 1) return@edit
            providers.removeAt(index)
            removed = true
            prefs[AI_PROVIDERS_JSON] = json.encodeToString(providers)
            val defaultName = prefs[AI_DEFAULT_PROVIDER]
            val newDefault = if (defaultName == providerName) providers.first().name else defaultName
            if (!newDefault.isNullOrBlank()) {
                prefs[AI_DEFAULT_PROVIDER] = newDefault
            }
            val scopeConfigs = readScopeConfigMap(prefs).toMutableMap()
            val fallbackProvider = providers.firstOrNull { it.name == newDefault } ?: providers.first()
            val fallbackModel = defaultModel(fallbackProvider.provider)
            scopeConfigs.keys.toList().forEach { scopeKey ->
                val config = scopeConfigs[scopeKey] ?: return@forEach
                if (config.providerName == providerName) {
                    scopeConfigs[scopeKey] = config.copy(
                        providerName = fallbackProvider.name,
                        model = fallbackModel
                    )
                }
            }
            prefs[AI_SCOPE_CONFIGS_JSON] = json.encodeToString(scopeConfigs)
        }
        if (removed) {
            encryptedApiKeyStore.removeApiKey(providerName)
        }
        return removed
    }

    suspend fun setDefaultProvider(providerName: String) {
        dataStore.edit { prefs ->
            val providers = readProviders(prefs)
            if (providers.none { it.name == providerName }) return@edit
            prefs[AI_DEFAULT_PROVIDER] = providerName
        }
    }

    fun getProviderApiKey(providerName: String): String = encryptedApiKeyStore.getApiKey(providerName)

    fun setProviderApiKey(providerName: String, key: String) {
        encryptedApiKeyStore.setApiKey(providerName, key)
    }

    suspend fun setScopeConfig(scope: AiSettingsScope, providerName: String, model: String) {
        dataStore.edit { prefs ->
            val scopeConfigs = readScopeConfigMap(prefs).toMutableMap()
            scopeConfigs[scope.name] = AiScopeConfig(providerName = providerName, model = model)
            prefs[AI_SCOPE_CONFIGS_JSON] = json.encodeToString(scopeConfigs)
        }
    }

    suspend fun getScopesUsingProvider(providerName: String): List<AiSettingsScope> {
        val prefs = dataStore.data.first()
        val configs = readScopeConfigMap(prefs)
        return configs.filterValues { it.providerName == providerName }
            .keys
            .mapNotNull { name -> runCatching { AiSettingsScope.valueOf(name) }.getOrNull() }
    }

    suspend fun getLastSelectedUnitIds(dictionaryId: Long): Set<Long> {
        val raw = dataStore.data.first()[lastSelectedUnitIdsKey(dictionaryId)] ?: return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setLastSelectedUnitIds(dictionaryId: Long, ids: Set<Long>) {
        dataStore.edit { it[lastSelectedUnitIdsKey(dictionaryId)] = ids.joinToString(",") }
    }

    suspend fun migrateApiKeyIfNeeded() {
        val prefs = dataStore.data.first()
        val oldKey = prefs[API_KEY]
        if (!oldKey.isNullOrBlank()) {
            encryptedApiKeyStore.setApiKey(oldKey)
            dataStore.edit { it.remove(API_KEY) }
        }
    }

    suspend fun migrateAiSettingsIfNeeded() {
        val prefs = dataStore.data.first()
        val hasProviders = !prefs[AI_PROVIDERS_JSON].isNullOrBlank()
        val hasScopes = !prefs[AI_SCOPE_CONFIGS_JSON].isNullOrBlank()

        val existingProviders = if (hasProviders) readProviders(prefs) else emptyList()
        val fallbackAnthropic = AiProviderProfile(
            name = "Anthropic",
            provider = AiProvider.ANTHROPIC,
            baseUrl = readBaseUrl(prefs, AiProvider.ANTHROPIC)
        )
        val fallbackOpenAi = AiProviderProfile(
            name = "OpenAI Compatible",
            provider = AiProvider.OPENAI_COMPATIBLE,
            baseUrl = readBaseUrl(prefs, AiProvider.OPENAI_COMPATIBLE)
        )
        val providers = if (existingProviders.isEmpty()) {
            listOf(fallbackAnthropic, fallbackOpenAi)
        } else {
            existingProviders
        }
        val anthropicName = providers.firstOrNull { it.provider == AiProvider.ANTHROPIC }?.name
        val openAiName = providers.firstOrNull { it.provider == AiProvider.OPENAI_COMPATIBLE }?.name

        val legacyDefault = providerFromPrefs(prefs)
        val fallbackDefaultName = if (legacyDefault == AiProvider.OPENAI_COMPATIBLE) {
            openAiName ?: providers.firstOrNull()?.name ?: fallbackOpenAi.name
        } else {
            anthropicName ?: providers.firstOrNull()?.name ?: fallbackAnthropic.name
        }
        val defaultProviderName = readDefaultProviderName(prefs).ifBlank {
            providers.firstOrNull()?.name ?: fallbackDefaultName
        }

        val legacyScopes = listOf(
            AiSettingsScope.POOL,
            AiSettingsScope.OCR,
            AiSettingsScope.ARTICLE,
            AiSettingsScope.SEARCH
        )
        encryptedApiKeyStore.migrateProviderKeysIfNeeded(
            anthropicProviderName = anthropicName,
            openAiProviderName = openAiName,
            scopes = legacyScopes
        )

        if (hasProviders && hasScopes) return

        val scopeConfigs = if (hasScopes) {
            readScopeConfigMap(prefs).toMutableMap()
        } else {
            mutableMapOf()
        }

        if (scopeConfigs.isEmpty()) {
            val mainModel = readModel(prefs, legacyDefault)
            val fastModel = readFastModel(prefs, legacyDefault)
            scopeConfigs[AiSettingsScope.MAIN.name] = AiScopeConfig(defaultProviderName, mainModel)
            scopeConfigs[AiSettingsScope.FAST.name] = AiScopeConfig(defaultProviderName, fastModel)

            legacyScopes.forEach { scope ->
                val providerName = prefs[stringPreferencesKey("${scope.prefix}provider")]
                    ?.let { raw ->
                        when (raw) {
                            AiProvider.OPENAI_COMPATIBLE.name ->
                                openAiName ?: providers.firstOrNull()?.name ?: fallbackOpenAi.name
                            else ->
                                anthropicName ?: providers.firstOrNull()?.name ?: fallbackAnthropic.name
                        }
                    } ?: defaultProviderName
                val providerFormat = if (providerName == openAiName) {
                    AiProvider.OPENAI_COMPATIBLE
                } else {
                    AiProvider.ANTHROPIC
                }
                val modelKey = stringPreferencesKey("${scope.prefix}model_${providerFormat.name.lowercase()}")
                val scopedModel = prefs[modelKey] ?: defaultModel(providerFormat)
                scopeConfigs[scope.name] = AiScopeConfig(providerName, scopedModel)
            }
        }

        dataStore.edit { mutablePrefs ->
            if (!hasProviders || existingProviders.isEmpty()) {
                mutablePrefs[AI_PROVIDERS_JSON] = json.encodeToString(providers)
            }
            if (mutablePrefs[AI_DEFAULT_PROVIDER].isNullOrBlank()) {
                mutablePrefs[AI_DEFAULT_PROVIDER] = defaultProviderName
            }
            if (!hasScopes) {
                mutablePrefs[AI_SCOPE_CONFIGS_JSON] = json.encodeToString(scopeConfigs)
            }
        }
    }

    suspend fun getFastAiConfig(): AiConfig {
        return getAiConfig(AiSettingsScope.FAST)
    }

    suspend fun getAiConfig(scope: AiSettingsScope): AiConfig {
        val prefs = dataStore.data.first()
        val providers = readProviders(prefs)
        val defaultProvider = resolveDefaultProvider(prefs, providers)
        val scopeConfig = readScopeConfig(prefs, scope)
        val chosen = providers.firstOrNull { it.name == scopeConfig.providerName } ?: defaultProvider
        val baseUrl = normalizeBaseUrl(chosen.baseUrl, chosen.provider)
        val model = scopeConfig.model.ifBlank { defaultModel(chosen.provider) }
        val apiKey = encryptedApiKeyStore.getApiKey(chosen.name)

        if (apiKey.isBlank() && chosen.name != defaultProvider.name) {
            val fallbackKey = encryptedApiKeyStore.getApiKey(defaultProvider.name)
            if (fallbackKey.isNotBlank()) {
                return AiConfig(
                    providerName = defaultProvider.name,
                    provider = defaultProvider.provider,
                    apiKey = fallbackKey,
                    model = defaultModel(defaultProvider.provider),
                    baseUrl = normalizeBaseUrl(defaultProvider.baseUrl, defaultProvider.provider)
                )
            }
        }

        return AiConfig(
            providerName = chosen.name,
            provider = chosen.provider,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl
        )
    }

    suspend fun getTtsConfig(): TtsConfig {
        val prefs = dataStore.data.first()
        return TtsConfig(
            rate = prefs[TTS_RATE] ?: 1.0f,
            pitch = prefs[TTS_PITCH] ?: 1.0f,
            localeTag = prefs[TTS_LOCALE] ?: "system",
            prewarmConcurrency = prefs[TTS_PREWARM_CONCURRENCY] ?: 2,
            prewarmRetry = prefs[TTS_PREWARM_RETRY] ?: 2
        )
    }

    suspend fun getImageCompressionConfig(): ImageCompressionConfig {
        val prefs = dataStore.data.first()
        return ImageCompressionConfig(
            enabled = prefs[IMAGE_COMPRESSION_ENABLED] ?: true,
            targetBytes = clampImageCompressionTarget(
                prefs[IMAGE_COMPRESSION_TARGET_BYTES] ?: defaultImageCompressionTargetBytes
            )
        )
    }

    suspend fun getAiResponseUnwrapEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[AI_RESPONSE_UNWRAP_ENABLED] ?: false
    }

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

    private fun readProviders(prefs: Preferences): List<AiProviderProfile> {
        val raw = prefs[AI_PROVIDERS_JSON]
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<AiProviderProfile>>(raw) }.getOrDefault(emptyList())
    }

    private fun readDefaultProviderName(prefs: Preferences): String {
        return prefs[AI_DEFAULT_PROVIDER] ?: ""
    }

    private fun readScopeConfig(prefs: Preferences, scope: AiSettingsScope): AiScopeConfig {
        val configs = readScopeConfigMap(prefs)
        val existing = configs[scope.name]
        if (existing != null) return existing
        val providers = readProviders(prefs)
        val defaultProvider = resolveDefaultProvider(prefs, providers)
        return AiScopeConfig(defaultProvider.name, defaultModel(defaultProvider.provider))
    }

    private fun readScopeConfigMap(prefs: Preferences): Map<String, AiScopeConfig> {
        val raw = prefs[AI_SCOPE_CONFIGS_JSON]
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, AiScopeConfig>>(raw) }.getOrDefault(emptyMap())
    }

    private fun resolveDefaultProvider(
        prefs: Preferences,
        providers: List<AiProviderProfile>
    ): AiProviderProfile {
        val defaultName = prefs[AI_DEFAULT_PROVIDER]
        val fromName = providers.firstOrNull { it.name == defaultName }
        if (fromName != null) return fromName
        return providers.firstOrNull() ?: AiProviderProfile(
            name = "Anthropic",
            provider = AiProvider.ANTHROPIC,
            baseUrl = Constants.ANTHROPIC_BASE_URL
        )
    }

    private fun normalizeBaseUrl(baseUrl: String, provider: AiProvider): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            return defaultBaseUrl(provider)
        }
        return trimmed
    }

    private fun clampImageCompressionTarget(value: Int): Int {
        return value.coerceIn(minImageCompressionTargetBytes, maxImageCompressionTargetBytes)
    }
}
