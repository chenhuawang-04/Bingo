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
        if (!prefs.getBoolean("migrated", false)) {
            appScope.launch {
                settingsDataStore.migrateApiKeyIfNeeded()
                prefs.edit().putBoolean("migrated", true).apply()
            }
        }
    }
}
