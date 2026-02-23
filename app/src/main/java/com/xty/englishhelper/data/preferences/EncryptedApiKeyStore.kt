package com.xty.englishhelper.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xty.englishhelper.domain.model.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun getApiKey(provider: AiProvider): String {
        val scoped = when (provider) {
            AiProvider.ANTHROPIC -> prefs.getString(ANTHROPIC_API_KEY, null)
            AiProvider.OPENAI_COMPATIBLE -> prefs.getString(OPENAI_API_KEY, null)
        }
        return scoped ?: getApiKey()
    }

    fun setApiKey(provider: AiProvider, key: String) {
        when (provider) {
            AiProvider.ANTHROPIC -> prefs.edit().putString(ANTHROPIC_API_KEY, key).apply()
            AiProvider.OPENAI_COMPATIBLE -> prefs.edit().putString(OPENAI_API_KEY, key).apply()
        }
    }
}
