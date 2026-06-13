package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoSyncScheduler @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val backgroundTaskManager: BackgroundTaskManager
) {
    suspend fun checkAndSync() {
        val lastSyncAt = settingsDataStore.lastSyncAt.first()
        if (isSameDay(lastSyncAt)) return

        backgroundTaskManager.enqueueCloudSync(
            force = false,
            syncMode = "SMART",
            triggeredBy = "auto_daily"
        )
    }

    private fun isSameDay(timestamp: Long): Boolean {
        if (timestamp == 0L) return false
        val cal = Calendar.getInstance()
        val today = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val syncCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val syncDay = syncCal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return today == syncDay
    }
}
