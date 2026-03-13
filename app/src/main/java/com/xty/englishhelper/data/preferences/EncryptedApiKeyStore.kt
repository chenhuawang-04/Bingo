package com.xty.englishhelper.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xty.englishhelper.domain.model.AiSettingsScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val LEGACY_API_KEY = "api_key"
        private const val ANTHROPIC_API_KEY = "api_key_anthropic"
        private const val OPENAI_API_KEY = "api_key_openai"
        private const val GITHUB_PAT = "github_pat"
        private const val PROVIDER_API_KEY_PREFIX = "api_key_provider_"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "encrypted_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String = prefs.getString(LEGACY_API_KEY, "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString(LEGACY_API_KEY, key).apply()
    }

    fun getApiKey(providerName: String): String {
        return prefs.getString(providerKey(providerName), "") ?: ""
    }

    fun setApiKey(providerName: String, key: String) {
        prefs.edit().putString(providerKey(providerName), key).apply()
    }

    fun removeApiKey(providerName: String) {
        prefs.edit().remove(providerKey(providerName)).apply()
    }

    fun hasApiKey(providerName: String): Boolean = getApiKey(providerName).isNotBlank()

    fun migrateProviderKeysIfNeeded(
        anthropicProviderName: String?,
        openAiProviderName: String?,
        scopes: List<AiSettingsScope>
    ) {
        if (!anthropicProviderName.isNullOrBlank() && getApiKey(anthropicProviderName).isBlank()) {
            val legacy = prefs.getString(ANTHROPIC_API_KEY, null)
                ?: prefs.getString(LEGACY_API_KEY, null)
                ?: findLegacyScopedKey(scopes, "anthropic")
            if (!legacy.isNullOrBlank()) {
                setApiKey(anthropicProviderName, legacy)
            }
        }
        if (!openAiProviderName.isNullOrBlank() && getApiKey(openAiProviderName).isBlank()) {
            val legacy = prefs.getString(OPENAI_API_KEY, null)
                ?: prefs.getString(LEGACY_API_KEY, null)
                ?: findLegacyScopedKey(scopes, "openai_compatible")
            if (!legacy.isNullOrBlank()) {
                setApiKey(openAiProviderName, legacy)
            }
        }
    }

    fun getGitHubPat(): String = prefs.getString(GITHUB_PAT, "") ?: ""

    fun setGitHubPat(token: String) {
        prefs.edit().putString(GITHUB_PAT, token).apply()
    }

    fun clearGitHubPat() {
        prefs.edit().remove(GITHUB_PAT).apply()
    }

    private fun providerKey(providerName: String): String {
        val encoded = URLEncoder.encode(providerName, "UTF-8")
        return "$PROVIDER_API_KEY_PREFIX$encoded"
    }

    private fun findLegacyScopedKey(scopes: List<AiSettingsScope>, providerKeySuffix: String): String? {
        return scopes.asSequence()
            .map { scope -> "api_key_${scope.prefix}$providerKeySuffix" }
            .mapNotNull { key -> prefs.getString(key, null) }
            .firstOrNull { it.isNotBlank() }
    }
}
