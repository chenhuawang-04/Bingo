package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.xty.englishhelper.data.json.SettingsPreferenceEntryJsonModel
import com.xty.englishhelper.data.json.SettingsSyncJsonModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsDataStoreImportTest {

    @Test
    fun `importSyncSnapshot rejects unsupported schema before touching datastore`() = runTest {
        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
        val encryptedApiKeyStore = mockk<EncryptedApiKeyStore>(relaxed = true)
        val subject = SettingsDataStore(dataStore, encryptedApiKeyStore)

        val error = expectIllegalState {
            subject.importSyncSnapshot(
                SettingsSyncJsonModel(
                    schemaVersion = SettingsSyncJsonModel.CURRENT_SCHEMA_VERSION + 1
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsupported settings sync schema version"))
        coVerify(exactly = 0) { dataStore.updateData(any()) }
    }

    @Test
    fun `importSyncSnapshot rejects malformed entries before touching datastore`() = runTest {
        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
        val encryptedApiKeyStore = mockk<EncryptedApiKeyStore>(relaxed = true)
        val subject = SettingsDataStore(dataStore, encryptedApiKeyStore)

        val error = expectIllegalState {
            subject.importSyncSnapshot(
                SettingsSyncJsonModel(
                    preferences = listOf(
                        SettingsPreferenceEntryJsonModel(
                            key = "pool_max_concurrent",
                            type = "int"
                        )
                    )
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Missing int value"))
        coVerify(exactly = 0) { dataStore.updateData(any()) }
    }

    @Test
    fun `exportSyncSnapshot includes study word note and excludes local only keys`() = runTest {
        val subject = createRealSubject()

        subject.setStudyWordNoteEnabled(true)
        subject.setLastSyncAt(123456789L)
        subject.setLastSelectedUnitIds(7L, setOf(1L, 2L, 3L))

        val snapshot = subject.exportSyncSnapshot(
            appVersion = "8.1.3",
            deviceName = "unit-test"
        )
        val decoded = SettingsSyncCodec.decodeSnapshot(snapshot)

        assertEquals(true, decoded["study_word_note_enabled"])
        assertFalse(decoded.containsKey("last_sync_at"))
        assertFalse(decoded.containsKey("last_selected_unit_ids_7"))
    }

    @Test
    fun `exportSyncSnapshot keeps zero exportedAt when only github transport settings changed`() = runTest {
        val subject = createRealSubject()

        subject.setGitHubOwner("xty")
        subject.setGitHubRepo("english-helper")
        subject.setGitHubConfigSyncEnabled(true)
        subject.setGitHubConfigRepo("english-helper-config")

        val snapshot = subject.exportSyncSnapshot(
            appVersion = "8.1.3",
            deviceName = "unit-test"
        )

        assertEquals(0L, snapshot.exportedAt)
    }

    @Test
    fun `importSyncSnapshot applies study word note and ignores local only keys`() = runTest {
        val subject = createRealSubject()
        subject.setStudyWordNoteEnabled(false)
        subject.setLastSyncAt(123456789L)
        subject.setLastSelectedUnitIds(7L, setOf(9L))

        val snapshot = SettingsSyncJsonModel(
            preferences = SettingsSyncCodec.encodePreferences(
                preferences = mapOf(
                    "study_word_note_enabled" to true,
                    "last_sync_at" to 999999999L,
                    "last_selected_unit_ids_7" to "1,2"
                )
            )
        )

        subject.importSyncSnapshot(snapshot)

        assertTrue(subject.getStudyWordNoteEnabled())
        assertEquals(123456789L, subject.lastSyncAt.first())
        assertEquals(setOf(9L), subject.getLastSelectedUnitIds(7L))
    }

    @Test
    fun `importSyncSnapshot rejects wrong type for known key and keeps existing values`() = runTest {
        val subject = createRealSubject()
        subject.setStudyWordNoteEnabled(true)

        val error = expectIllegalState {
            subject.importSyncSnapshot(
                SettingsSyncJsonModel(
                    preferences = listOf(
                        SettingsPreferenceEntryJsonModel(
                            key = "study_word_note_enabled",
                            type = "string",
                            stringValue = "true"
                        )
                    )
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Invalid imported type"))
        assertTrue(subject.getStudyWordNoteEnabled())
    }

    @Test
    fun `importSyncSnapshot ignores unknown keys and still imports known values`() = runTest {
        val subject = createRealSubject()
        subject.setStudyWordNoteEnabled(false)

        subject.importSyncSnapshot(
            SettingsSyncJsonModel(
                preferences = SettingsSyncCodec.encodePreferences(
                    preferences = mapOf(
                        "study_word_note_enabled" to true,
                        "unknown_future_setting" to "ignored"
                    )
                )
            )
        )

        assertTrue(subject.getStudyWordNoteEnabled())
    }

    private suspend fun expectIllegalState(block: suspend () -> Unit): IllegalStateException {
        return try {
            block()
            throw AssertionError("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            error
        }
    }

    private fun TestScope.createRealSubject(): SettingsDataStore {
        val file = File.createTempFile("settings-datastore-import", ".preferences_pb").apply {
            deleteOnExit()
        }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        return SettingsDataStore(
            dataStore = dataStore,
            encryptedApiKeyStore = mockk(relaxed = true)
        )
    }
}
