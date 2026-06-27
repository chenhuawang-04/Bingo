package com.xty.englishhelper.data.preferences

import com.xty.englishhelper.data.json.SettingsPreferenceEntryJsonModel
import com.xty.englishhelper.data.json.SettingsSyncJsonModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSyncCodecTest {

    @Test
    fun `encodePreferences keeps primitive settings and excludes configured keys`() {
        val preferences = mapOf<String, Any>(
            "github_owner" to "xty",
            "pool_window_size" to 64,
            "brainstorm_quality_min_confidence" to 0.45f,
            "github_config_sync_enabled" to true,
            "last_sync_at" to 123456789L,
            "last_selected_unit_ids_7" to "1,2,3"
        )

        val entries = SettingsSyncCodec.encodePreferences(
            preferences = preferences,
            excludedKeys = setOf("last_sync_at"),
            excludedPrefixes = setOf("last_selected_unit_ids_")
        )

        val keys = entries.map { it.key }.toSet()
        assertTrue("github_owner" in keys)
        assertTrue("pool_window_size" in keys)
        assertTrue("brainstorm_quality_min_confidence" in keys)
        assertTrue("github_config_sync_enabled" in keys)
        assertFalse("last_sync_at" in keys)
        assertFalse("last_selected_unit_ids_7" in keys)
    }

    @Test
    fun `decodePreferences restores primitive values by type`() {
        val entries = SettingsSyncCodec.encodePreferences(
            preferences = mapOf(
                "app_locale" to "en-US",
                "pool_max_concurrent" to 4,
                "settings_sync_updated_at" to 99887766L,
                "tts_rate" to 1.15f,
                "github_config_sync_enabled" to true
            )
        )

        val decoded = SettingsSyncCodec.decodePreferences(entries)

        assertEquals("en-US", decoded["app_locale"])
        assertEquals(4, decoded["pool_max_concurrent"])
        assertEquals(99887766L, decoded["settings_sync_updated_at"])
        assertEquals(1.15f, decoded["tts_rate"])
        assertEquals(true, decoded["github_config_sync_enabled"])
    }

    @Test
    fun `encode and decode preserve study word note setting`() {
        val entries = SettingsSyncCodec.encodePreferences(
            preferences = mapOf(
                "study_word_note_enabled" to true
            )
        )

        val decoded = SettingsSyncCodec.decodePreferences(entries)

        assertEquals(true, decoded["study_word_note_enabled"])
    }

    @Test
    fun `decodePreferences rejects unsupported entry type`() {
        val error = expectIllegalState {
            SettingsSyncCodec.decodePreferences(
                listOf(
                    SettingsPreferenceEntryJsonModel(
                        key = "app_locale",
                        type = "double"
                    )
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsupported settings entry type"))
    }

    @Test
    fun `decodePreferences rejects missing typed value`() {
        val error = expectIllegalState {
            SettingsSyncCodec.decodePreferences(
                listOf(
                    SettingsPreferenceEntryJsonModel(
                        key = "pool_max_concurrent",
                        type = "int"
                    )
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Missing int value"))
    }

    @Test
    fun `decodeSnapshot rejects unsupported schema version`() {
        val error = expectIllegalState {
            SettingsSyncCodec.decodeSnapshot(
                SettingsSyncJsonModel(
                    schemaVersion = SettingsSyncJsonModel.CURRENT_SCHEMA_VERSION + 1,
                    preferences = emptyList()
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsupported settings sync schema version"))
    }

    private fun expectIllegalState(block: () -> Unit): IllegalStateException {
        return try {
            block()
            throw AssertionError("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            error
        }
    }
}
