package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SettingsSyncJsonModel(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long = 0,
    val appVersion: String = "",
    val deviceName: String = "",
    val preferences: List<SettingsPreferenceEntryJsonModel> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class SettingsPreferenceEntryJsonModel(
    val key: String,
    val type: String,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val floatValue: Float? = null,
    val booleanValue: Boolean? = null
)
