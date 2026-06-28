package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xty.englishhelper.data.json.SettingsSyncJsonModel
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiProviderProfile
import com.xty.englishhelper.domain.model.AiScopeConfig
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.domain.model.PoolRetryMode
import com.xty.englishhelper.domain.model.WordReferenceSource
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
    private enum class SyncValueType {
        STRING,
        INT,
        LONG,
        FLOAT,
        BOOLEAN
    }

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
        val AI_JSON_REPAIR_ENABLED = booleanPreferencesKey("ai_json_repair_enabled")
        val WORD_ORGANIZE_HIGH_QUALITY_ENABLED = booleanPreferencesKey("word_organize_high_quality_enabled")
        val WORD_ORGANIZE_REFERENCE_SOURCE = stringPreferencesKey("word_organize_reference_source")
        val IMAGE_COMPRESSION_ENABLED = booleanPreferencesKey("image_compression_enabled")
        val IMAGE_COMPRESSION_TARGET_BYTES = intPreferencesKey("image_compression_target_bytes")
        val BACKGROUND_TASK_CONCURRENCY = intPreferencesKey("background_task_concurrency")

        val GITHUB_OWNER = stringPreferencesKey("github_owner")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val GITHUB_CONFIG_SYNC_ENABLED = booleanPreferencesKey("github_config_sync_enabled")
        val GITHUB_CONFIG_REPO = stringPreferencesKey("github_config_repo")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val SETTINGS_SYNC_UPDATED_AT = longPreferencesKey("settings_sync_updated_at")
        val GUARDIAN_DETAIL_CONCURRENCY = intPreferencesKey("guardian_detail_concurrency")
        val ONLINE_READING_SOURCE = stringPreferencesKey("online_reading_source")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_LOCALE = stringPreferencesKey("tts_locale")
        val TTS_AUTO_STUDY = booleanPreferencesKey("tts_auto_study")
        val TTS_PREWARM_CONCURRENCY = intPreferencesKey("tts_prewarm_concurrency")
        val TTS_PREWARM_RETRY = intPreferencesKey("tts_prewarm_retry")
        val POOL_WINDOW_SIZE = intPreferencesKey("pool_window_size")
        val POOL_MAX_CONCURRENT = intPreferencesKey("pool_max_concurrent")
        val POOL_REQUESTS_PER_MINUTE = intPreferencesKey("pool_requests_per_minute")
        val POOL_RETRY_MODE = stringPreferencesKey("pool_retry_mode")
        val POOL_MANAGED_MODE = booleanPreferencesKey("pool_managed_mode")
        val BRAINSTORM_CLUSTER_SIZE = intPreferencesKey("brainstorm_cluster_size")
        val BRAINSTORM_QUALITY_MIN_CONFIDENCE = floatPreferencesKey("brainstorm_quality_min_confidence")
        val BRAINSTORM_ACTIVE_RECALL = booleanPreferencesKey("brainstorm_active_recall")
        val STUDY_WORD_NOTE_ENABLED = booleanPreferencesKey("study_word_note_enabled")
        val SCAN_RESCORE_AFTER_HOURS = intPreferencesKey("scan_rescore_after_hours")
        val APP_LOCALE = stringPreferencesKey("app_locale")
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
    private val nonSyncablePreferenceKeys = setOf(
        LAST_SYNC_AT.name,
        API_KEY.name,
        "github_pat"
    )
    private val nonSyncablePreferencePrefixes = setOf(
        "last_selected_unit_ids_",
        "api_key_",
        "token_",
        "secret_",
        "credential_",
        "password_"
    )
    private val syncablePreferenceTypes: Map<String, SyncValueType> = buildSyncablePreferenceTypes()

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

    val backgroundTaskConcurrency: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[BACKGROUND_TASK_CONCURRENCY] ?: 2).coerceIn(1, 6)
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

    val aiJsonRepairEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AI_JSON_REPAIR_ENABLED] ?: false
    }

    val wordOrganizeHighQualityEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[WORD_ORGANIZE_HIGH_QUALITY_ENABLED] ?: false
    }

    val wordOrganizeReferenceSource: Flow<WordReferenceSource> = dataStore.data.map { prefs ->
        prefs[WORD_ORGANIZE_REFERENCE_SOURCE]
            ?.let { raw -> runCatching { WordReferenceSource.valueOf(raw) }.getOrNull() }
            ?: WordReferenceSource.FAST
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

    val poolWindowSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs[POOL_WINDOW_SIZE] ?: 50
    }

    val poolMaxConcurrent: Flow<Int> = dataStore.data.map { prefs ->
        prefs[POOL_MAX_CONCURRENT] ?: 3
    }

    val poolRequestsPerMinute: Flow<Int> = dataStore.data.map { prefs ->
        prefs[POOL_REQUESTS_PER_MINUTE] ?: 30
    }

    val poolRetryMode: Flow<PoolRetryMode> = dataStore.data.map { prefs ->
        parsePoolRetryMode(prefs[POOL_RETRY_MODE])
    }

    val poolManagedMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[POOL_MANAGED_MODE] ?: false
    }

    /** 头脑风暴学习簇的目标大小（每个连贯小簇最多几个词，越大越"成片"背、越小越分散）。 */
    val brainstormClusterSize: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[BRAINSTORM_CLUSTER_SIZE] ?: 6).coerceIn(2, 12)
    }

    /** 头脑风暴选词的关联边最低置信度门槛（低于此值的弱边不参与选词/展示）。 */
    val brainstormQualityMinConfidence: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[BRAINSTORM_QUALITY_MIN_CONFIDENCE] ?: 0.3f).coerceIn(0f, 0.9f)
    }

    /** 头脑风暴「关联主动回忆」：开启后对有明确近义/反义/易混关联的词出选择题，主动检索替代翻卡。默认关。 */
    val brainstormActiveRecall: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[BRAINSTORM_ACTIVE_RECALL] ?: false
    }

    /** 背词页单词便签：预答案态展示图与输入框，支持补充强关联词。默认关。 */
    val studyWordNoteEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[STUDY_WORD_NOTE_ENABLED] ?: false
    }

    suspend fun setGuardianDetailConcurrency(value: Int) {
        editSyncablePreferences { prefs ->
            prefs[GUARDIAN_DETAIL_CONCURRENCY] = value
        }
    }

    suspend fun setBackgroundTaskConcurrency(value: Int) {
        editSyncablePreferences { prefs ->
            prefs[BACKGROUND_TASK_CONCURRENCY] = value.coerceIn(1, 6)
        }
    }

    suspend fun setOnlineReadingSource(value: String) {
        editSyncablePreferences { prefs ->
            prefs[ONLINE_READING_SOURCE] = value
        }
    }

    suspend fun setTtsRate(value: Float) {
        editSyncablePreferences { prefs -> prefs[TTS_RATE] = value }
    }

    suspend fun setTtsPitch(value: Float) {
        editSyncablePreferences { prefs -> prefs[TTS_PITCH] = value }
    }

    suspend fun setTtsLocale(value: String) {
        editSyncablePreferences { prefs -> prefs[TTS_LOCALE] = value }
    }

    suspend fun setTtsAutoStudy(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[TTS_AUTO_STUDY] = value }
    }

    suspend fun setAiDebugMode(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[AI_DEBUG_MODE] = value }
    }

    suspend fun setAiResponseUnwrapEnabled(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[AI_RESPONSE_UNWRAP_ENABLED] = value }
    }

    suspend fun setAiJsonRepairEnabled(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[AI_JSON_REPAIR_ENABLED] = value }
    }

    suspend fun setWordOrganizeHighQualityEnabled(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[WORD_ORGANIZE_HIGH_QUALITY_ENABLED] = value }
    }

    suspend fun setWordOrganizeReferenceSource(value: WordReferenceSource) {
        editSyncablePreferences { prefs -> prefs[WORD_ORGANIZE_REFERENCE_SOURCE] = value.name }
    }

    suspend fun setImageCompressionEnabled(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[IMAGE_COMPRESSION_ENABLED] = value }
    }

    suspend fun setImageCompressionTargetBytes(value: Int) {
        editSyncablePreferences { prefs ->
            prefs[IMAGE_COMPRESSION_TARGET_BYTES] = clampImageCompressionTarget(value)
        }
    }

    suspend fun setTtsPrewarmConcurrency(value: Int) {
        editSyncablePreferences { prefs -> prefs[TTS_PREWARM_CONCURRENCY] = value }
    }

    suspend fun setTtsPrewarmRetry(value: Int) {
        editSyncablePreferences { prefs -> prefs[TTS_PREWARM_RETRY] = value }
    }

    suspend fun getPoolWindowSize(): Int {
        val prefs = dataStore.data.first()
        return prefs[POOL_WINDOW_SIZE] ?: 50
    }

    suspend fun setPoolWindowSize(value: Int) {
        editSyncablePreferences { prefs -> prefs[POOL_WINDOW_SIZE] = value.coerceIn(10, 200) }
    }

    suspend fun getPoolMaxConcurrent(): Int {
        val prefs = dataStore.data.first()
        return (prefs[POOL_MAX_CONCURRENT] ?: 3).coerceIn(1, 10)
    }

    suspend fun setPoolMaxConcurrent(value: Int) {
        editSyncablePreferences { prefs -> prefs[POOL_MAX_CONCURRENT] = value.coerceIn(1, 10) }
    }

    suspend fun getPoolRequestsPerMinute(): Int {
        val prefs = dataStore.data.first()
        return (prefs[POOL_REQUESTS_PER_MINUTE] ?: 30).coerceIn(1, 120)
    }

    suspend fun setPoolRequestsPerMinute(value: Int) {
        editSyncablePreferences { prefs -> prefs[POOL_REQUESTS_PER_MINUTE] = value.coerceIn(1, 120) }
    }

    suspend fun getPoolRetryMode(): PoolRetryMode {
        val prefs = dataStore.data.first()
        return parsePoolRetryMode(prefs[POOL_RETRY_MODE])
    }

    suspend fun setPoolRetryMode(value: PoolRetryMode) {
        editSyncablePreferences { prefs -> prefs[POOL_RETRY_MODE] = value.name }
    }

    private fun parsePoolRetryMode(raw: String?): PoolRetryMode =
        runCatching { PoolRetryMode.valueOf(raw ?: PoolRetryMode.AGGRESSIVE.name) }
            .getOrDefault(PoolRetryMode.AGGRESSIVE)

    suspend fun getPoolManagedMode(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[POOL_MANAGED_MODE] ?: false
    }

    suspend fun setPoolManagedMode(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[POOL_MANAGED_MODE] = value }
    }

    suspend fun getBrainstormClusterSize(): Int {
        val prefs = dataStore.data.first()
        return (prefs[BRAINSTORM_CLUSTER_SIZE] ?: 6).coerceIn(2, 12)
    }

    suspend fun setBrainstormClusterSize(value: Int) {
        editSyncablePreferences { prefs -> prefs[BRAINSTORM_CLUSTER_SIZE] = value.coerceIn(2, 12) }
    }

    suspend fun getBrainstormQualityMinConfidence(): Float {
        val prefs = dataStore.data.first()
        return (prefs[BRAINSTORM_QUALITY_MIN_CONFIDENCE] ?: 0.3f).coerceIn(0f, 0.9f)
    }

    suspend fun setBrainstormQualityMinConfidence(value: Float) {
        editSyncablePreferences { prefs -> prefs[BRAINSTORM_QUALITY_MIN_CONFIDENCE] = value.coerceIn(0f, 0.9f) }
    }

    suspend fun getBrainstormActiveRecall(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[BRAINSTORM_ACTIVE_RECALL] ?: false
    }

    suspend fun setBrainstormActiveRecall(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[BRAINSTORM_ACTIVE_RECALL] = value }
    }

    suspend fun getStudyWordNoteEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[STUDY_WORD_NOTE_ENABLED] ?: false
    }

    suspend fun setStudyWordNoteEnabled(value: Boolean) {
        editSyncablePreferences { prefs -> prefs[STUDY_WORD_NOTE_ENABLED] = value }
    }

    suspend fun getProviders(): List<AiProviderProfile> {
        return readProviders(dataStore.data.first())
    }

    suspend fun getProvider(providerName: String): AiProviderProfile? {
        return getProviders().firstOrNull { it.name == providerName }
    }

    suspend fun addProvider(profile: AiProviderProfile) {
        editSyncablePreferences { prefs ->
            val providers = readProviders(prefs).toMutableList()
            if (providers.any { it.name.equals(profile.name, ignoreCase = true) }) {
                return@editSyncablePreferences
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
        editSyncablePreferences { prefs ->
            val providers = readProviders(prefs).toMutableList()
            val index = providers.indexOfFirst { it.name == profile.name }
            if (index < 0) return@editSyncablePreferences
            providers[index] = profile.copy(baseUrl = normalizeBaseUrl(profile.baseUrl, profile.provider))
            prefs[AI_PROVIDERS_JSON] = json.encodeToString(providers)
        }
    }

    suspend fun deleteProvider(providerName: String): Boolean {
        var removed = false
        editSyncablePreferences { prefs ->
            val providers = readProviders(prefs).toMutableList()
            val index = providers.indexOfFirst { it.name == providerName }
            if (index < 0) return@editSyncablePreferences
            if (providers.size <= 1) return@editSyncablePreferences
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
        editSyncablePreferences { prefs ->
            val providers = readProviders(prefs)
            if (providers.none { it.name == providerName }) return@editSyncablePreferences
            prefs[AI_DEFAULT_PROVIDER] = providerName
        }
    }

    fun getProviderApiKey(providerName: String): String = encryptedApiKeyStore.getApiKey(providerName)

    fun setProviderApiKey(providerName: String, key: String) {
        encryptedApiKeyStore.setApiKey(providerName, key)
    }

    suspend fun setScopeConfig(scope: AiSettingsScope, providerName: String, model: String) {
        editSyncablePreferences { prefs ->
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
        return getAiConfigInternal(scope, allowApiKeyFallback = true)
    }

    suspend fun getConfiguredAiConfig(scope: AiSettingsScope): AiConfig {
        return getAiConfigInternal(scope, allowApiKeyFallback = false)
    }

    private suspend fun getAiConfigInternal(scope: AiSettingsScope, allowApiKeyFallback: Boolean): AiConfig {
        val prefs = dataStore.data.first()
        val providers = readProviders(prefs)
        val defaultProvider = resolveDefaultProvider(prefs, providers)
        val scopeConfig = readScopeConfig(prefs, scope)
        val chosen = providers.firstOrNull { it.name == scopeConfig.providerName } ?: defaultProvider
        val baseUrl = normalizeBaseUrl(chosen.baseUrl, chosen.provider)
        val model = scopeConfig.model.ifBlank { defaultModel(chosen.provider) }
        val apiKey = encryptedApiKeyStore.getApiKey(chosen.name)

        if (allowApiKeyFallback && apiKey.isBlank() && chosen.name != defaultProvider.name) {
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

    suspend fun getBackgroundTaskConcurrency(): Int {
        val prefs = dataStore.data.first()
        return (prefs[BACKGROUND_TASK_CONCURRENCY] ?: 2).coerceIn(1, 6)
    }

    suspend fun getAiResponseUnwrapEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[AI_RESPONSE_UNWRAP_ENABLED] ?: false
    }

    suspend fun getAiJsonRepairEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[AI_JSON_REPAIR_ENABLED] ?: false
    }

    suspend fun getWordOrganizeHighQualityEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[WORD_ORGANIZE_HIGH_QUALITY_ENABLED] ?: false
    }

    suspend fun getWordOrganizeReferenceSource(): WordReferenceSource {
        val prefs = dataStore.data.first()
        return prefs[WORD_ORGANIZE_REFERENCE_SOURCE]
            ?.let { raw -> runCatching { WordReferenceSource.valueOf(raw) }.getOrNull() }
            ?: WordReferenceSource.FAST
    }

    val githubOwner: Flow<String> = dataStore.data.map { it[GITHUB_OWNER] ?: "" }
    val githubRepo: Flow<String> = dataStore.data.map { it[GITHUB_REPO] ?: "" }
    val githubConfigSyncEnabled: Flow<Boolean> = dataStore.data.map { it[GITHUB_CONFIG_SYNC_ENABLED] ?: false }
    val githubConfigRepo: Flow<String> = dataStore.data.map { it[GITHUB_CONFIG_REPO] ?: "" }
    val lastSyncAt: Flow<Long> = dataStore.data.map { it[LAST_SYNC_AT] ?: 0L }

    val scanRescoreAfterHours: Flow<Int> = dataStore.data.map { (it[SCAN_RESCORE_AFTER_HOURS] ?: 24).coerceIn(1, 720) }

    val appLocale: Flow<String> = dataStore.data.map { it[APP_LOCALE] ?: "system" }

    suspend fun setScanRescoreAfterHours(value: Int) {
        editSyncablePreferences { it[SCAN_RESCORE_AFTER_HOURS] = value.coerceIn(1, 720) }
    }

    suspend fun setAppLocale(locale: String) {
        editSyncablePreferences { it[APP_LOCALE] = locale }
    }

    suspend fun setGitHubOwner(owner: String) {
        editTransportPreferences { it[GITHUB_OWNER] = owner }
    }

    suspend fun setGitHubRepo(repo: String) {
        editTransportPreferences { it[GITHUB_REPO] = repo }
    }

    suspend fun setGitHubConfigSyncEnabled(enabled: Boolean) {
        editTransportPreferences { it[GITHUB_CONFIG_SYNC_ENABLED] = enabled }
    }

    suspend fun setGitHubConfigRepo(repo: String) {
        editTransportPreferences { it[GITHUB_CONFIG_REPO] = repo }
    }

    suspend fun setLastSyncAt(timestamp: Long) {
        dataStore.edit { it[LAST_SYNC_AT] = timestamp }
    }

    fun getGitHubPat(): String = encryptedApiKeyStore.getGitHubPat()

    fun setGitHubPat(token: String) = encryptedApiKeyStore.setGitHubPat(token)

    suspend fun exportSyncSnapshot(
        appVersion: String,
        deviceName: String
    ): SettingsSyncJsonModel {
        val exportedAt = currentSettingsSyncUpdatedAt()
        val prefs = dataStore.data.first()
        val rawPreferences = prefs.asMap().mapKeys { it.key.name }
        return SettingsSyncJsonModel(
            exportedAt = exportedAt,
            appVersion = appVersion,
            deviceName = deviceName,
            preferences = SettingsSyncCodec.encodePreferences(
                preferences = rawPreferences,
                excludedKeys = nonSyncablePreferenceKeys,
                excludedPrefixes = nonSyncablePreferencePrefixes
            )
        )
    }

    suspend fun importSyncSnapshot(snapshot: SettingsSyncJsonModel) {
        val importedValues = SettingsSyncCodec.decodeSnapshot(snapshot)
        validateImportedPreferences(importedValues)
        val importedUpdatedAt = maxOf(
            snapshot.exportedAt,
            (importedValues[SETTINGS_SYNC_UPDATED_AT.name] as? Long) ?: 0L
        )
        dataStore.edit { prefs ->
            clearSyncablePreferences(prefs)
            importedValues.forEach { (key, value) ->
                if (!isSyncablePreferenceKey(key)) return@forEach
                applyImportedPreference(prefs, key, value)
            }
            if (importedUpdatedAt > 0L) {
                prefs[SETTINGS_SYNC_UPDATED_AT] = importedUpdatedAt
            }
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

    private suspend fun editSyncablePreferences(block: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            block(prefs)
            prefs[SETTINGS_SYNC_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun markSettingsSyncUpdatedAt(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[SETTINGS_SYNC_UPDATED_AT] = timestamp
        }
    }

    private suspend fun currentSettingsSyncUpdatedAt(): Long {
        return dataStore.data.first()[SETTINGS_SYNC_UPDATED_AT] ?: 0L
    }

    private suspend fun editTransportPreferences(block: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            block(prefs)
        }
    }

    private fun clearSyncablePreferences(prefs: MutablePreferences) {
        prefs.asMap().keys.forEach { key ->
            if (!isSyncablePreferenceKey(key.name)) return@forEach
            removePreference(prefs, key)
        }
    }

    private fun isSyncablePreferenceKey(key: String): Boolean {
        if (key in nonSyncablePreferenceKeys) return false
        if (nonSyncablePreferencePrefixes.any(key::startsWith)) return false
        return true
    }

    private fun validateImportedPreferences(importedValues: Map<String, Any>) {
        importedValues.forEach { (key, value) ->
            if (!isSyncablePreferenceKey(key)) return@forEach
            val expectedType = syncablePreferenceTypes[key] ?: return@forEach
            val typeMatches = when (expectedType) {
                SyncValueType.STRING -> value is String
                SyncValueType.INT -> value is Int
                SyncValueType.LONG -> value is Long
                SyncValueType.FLOAT -> value is Float
                SyncValueType.BOOLEAN -> value is Boolean
            }
            if (!typeMatches) {
                throw IllegalStateException("Invalid imported type for settings key: $key")
            }
        }
    }

    private fun applyImportedPreference(
        prefs: MutablePreferences,
        key: String,
        value: Any
    ) {
        when (syncablePreferenceTypes[key]) {
            SyncValueType.STRING -> prefs[stringPreferencesKey(key)] = value as String
            SyncValueType.INT -> prefs[intPreferencesKey(key)] = value as Int
            SyncValueType.LONG -> prefs[longPreferencesKey(key)] = value as Long
            SyncValueType.FLOAT -> prefs[floatPreferencesKey(key)] = value as Float
            SyncValueType.BOOLEAN -> prefs[booleanPreferencesKey(key)] = value as Boolean
            null -> Unit
        }
    }

    private fun buildSyncablePreferenceTypes(): Map<String, SyncValueType> = buildMap {
        fun register(key: Preferences.Key<String>) {
            put(key.name, SyncValueType.STRING)
        }

        fun register(key: Preferences.Key<Int>) {
            put(key.name, SyncValueType.INT)
        }

        fun register(key: Preferences.Key<Long>) {
            put(key.name, SyncValueType.LONG)
        }

        fun register(key: Preferences.Key<Float>) {
            put(key.name, SyncValueType.FLOAT)
        }

        fun register(key: Preferences.Key<Boolean>) {
            put(key.name, SyncValueType.BOOLEAN)
        }

        register(MODEL)
        register(BASE_URL)
        register(PROVIDER)
        register(ANTHROPIC_MODEL)
        register(OPENAI_MODEL)
        register(ANTHROPIC_BASE_URL)
        register(OPENAI_BASE_URL)
        register(FAST_ANTHROPIC_MODEL)
        register(FAST_OPENAI_MODEL)
        register(AI_PROVIDERS_JSON)
        register(AI_DEFAULT_PROVIDER)
        register(AI_SCOPE_CONFIGS_JSON)
        register(AI_DEBUG_MODE)
        register(AI_RESPONSE_UNWRAP_ENABLED)
        register(AI_JSON_REPAIR_ENABLED)
        register(WORD_ORGANIZE_HIGH_QUALITY_ENABLED)
        register(WORD_ORGANIZE_REFERENCE_SOURCE)
        register(IMAGE_COMPRESSION_ENABLED)
        register(IMAGE_COMPRESSION_TARGET_BYTES)
        register(BACKGROUND_TASK_CONCURRENCY)
        register(GITHUB_OWNER)
        register(GITHUB_REPO)
        register(GITHUB_CONFIG_SYNC_ENABLED)
        register(GITHUB_CONFIG_REPO)
        register(SETTINGS_SYNC_UPDATED_AT)
        register(GUARDIAN_DETAIL_CONCURRENCY)
        register(ONLINE_READING_SOURCE)
        register(TTS_RATE)
        register(TTS_PITCH)
        register(TTS_LOCALE)
        register(TTS_AUTO_STUDY)
        register(TTS_PREWARM_CONCURRENCY)
        register(TTS_PREWARM_RETRY)
        register(POOL_WINDOW_SIZE)
        register(POOL_MAX_CONCURRENT)
        register(POOL_REQUESTS_PER_MINUTE)
        register(POOL_RETRY_MODE)
        register(POOL_MANAGED_MODE)
        register(BRAINSTORM_CLUSTER_SIZE)
        register(BRAINSTORM_QUALITY_MIN_CONFIDENCE)
        register(BRAINSTORM_ACTIVE_RECALL)
        register(STUDY_WORD_NOTE_ENABLED)
        register(SCAN_RESCORE_AFTER_HOURS)
        register(APP_LOCALE)
    }

    @Suppress("UNCHECKED_CAST")
    private fun removePreference(
        prefs: MutablePreferences,
        key: Preferences.Key<*>
    ) {
        prefs.remove(key as Preferences.Key<Any>)
    }
}
