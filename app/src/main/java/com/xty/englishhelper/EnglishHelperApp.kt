package com.xty.englishhelper

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.xty.englishhelper.data.debug.AiDebugManager
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.sync.AutoSyncScheduler
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class EnglishHelperApp : Application() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    @Inject
    lateinit var aiDebugManager: AiDebugManager
    @Inject
    lateinit var backgroundTaskManager: BackgroundTaskManager
    @Inject
    lateinit var autoSyncScheduler: AutoSyncScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Apply saved locale
        appScope.launch {
            val locale = settingsDataStore.appLocale.first()
            applyLocale(locale)
        }

        // One-time migration of plaintext API key to encrypted storage
        val prefs = getSharedPreferences("api_key_migration", MODE_PRIVATE)
        appScope.launch {
            if (!prefs.getBoolean("api_key_migrated", false)) {
                settingsDataStore.migrateApiKeyIfNeeded()
                prefs.edit().putBoolean("api_key_migrated", true).apply()
            }
            settingsDataStore.migrateAiSettingsIfNeeded()
        }
        appScope.launch {
            settingsDataStore.aiDebugMode.collect { enabled ->
                aiDebugManager.setEnabled(enabled)
            }
        }
        appScope.launch {
            backgroundTaskManager.start()
            backgroundTaskManager.enqueueOnlineArticleScanScore()
        }
        appScope.launch {
            autoSyncScheduler.checkAndSync()
        }
    }

    companion object {
        fun applyLocale(locale: String) {
            val localeList = if (locale == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(locale)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }
}



