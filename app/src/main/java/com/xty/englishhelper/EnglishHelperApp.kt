package com.xty.englishhelper

import android.app.Application
import com.xty.englishhelper.data.preferences.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EnglishHelperApp : Application() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // One-time migration of plaintext API key to encrypted storage
        val prefs = getSharedPreferences("api_key_migration", MODE_PRIVATE)
        appScope.launch {
            if (!prefs.getBoolean("api_key_migrated", false)) {
                settingsDataStore.migrateApiKeyIfNeeded()
                prefs.edit().putBoolean("api_key_migrated", true).apply()
            }
            settingsDataStore.migrateAiSettingsIfNeeded()
        }
    }
}
