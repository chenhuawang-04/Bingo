package com.xty.englishhelper.data.preferences

import com.xty.englishhelper.data.json.SettingsSyncJsonModel
import com.xty.englishhelper.data.json.SettingsPreferenceEntryJsonModel

internal object SettingsSyncCodec {

    fun encodePreferences(
        preferences: Map<String, Any>,
        excludedKeys: Set<String> = emptySet(),
        excludedPrefixes: Set<String> = emptySet()
    ): List<SettingsPreferenceEntryJsonModel> {
        return preferences.entries
            .asSequence()
            .filter { (key, _) -> key !in excludedKeys && excludedPrefixes.none(key::startsWith) }
            .mapNotNull { (key, value) ->
                when (value) {
                    is String -> SettingsPreferenceEntryJsonModel(
                        key = key,
                        type = TYPE_STRING,
                        stringValue = value
                    )

                    is Int -> SettingsPreferenceEntryJsonModel(
                        key = key,
                        type = TYPE_INT,
                        intValue = value
                    )

                    is Long -> SettingsPreferenceEntryJsonModel(
                        key = key,
                        type = TYPE_LONG,
                        longValue = value
                    )

                    is Float -> SettingsPreferenceEntryJsonModel(
                        key = key,
                        type = TYPE_FLOAT,
                        floatValue = value
                    )

                    is Boolean -> SettingsPreferenceEntryJsonModel(
                        key = key,
                        type = TYPE_BOOLEAN,
                        booleanValue = value
                    )

                    else -> null
                }
            }
            .sortedBy { it.key }
            .toList()
    }

    fun decodeSnapshot(snapshot: SettingsSyncJsonModel): Map<String, Any> {
        val schemaVersion = snapshot.schemaVersion
        if (schemaVersion <= 0) {
            throw IllegalStateException("Invalid settings sync schema version: $schemaVersion")
        }
        if (schemaVersion > SettingsSyncJsonModel.CURRENT_SCHEMA_VERSION) {
            throw IllegalStateException(
                "Unsupported settings sync schema version: $schemaVersion, please upgrade the app"
            )
        }
        return decodePreferences(snapshot.preferences)
    }

    fun decodePreferences(entries: List<SettingsPreferenceEntryJsonModel>): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        entries.forEachIndexed { index, entry ->
            val key = entry.key.trim()
            if (key.isBlank()) {
                throw IllegalStateException("Invalid settings entry at index $index: key is blank")
            }
            if (result.containsKey(key)) {
                throw IllegalStateException("Duplicate settings entry key: $key")
            }
            val value = when (entry.type) {
                TYPE_STRING -> entry.stringValue
                    ?: throw IllegalStateException("Missing string value for settings key: $key")

                TYPE_INT -> entry.intValue
                    ?: throw IllegalStateException("Missing int value for settings key: $key")

                TYPE_LONG -> entry.longValue
                    ?: throw IllegalStateException("Missing long value for settings key: $key")

                TYPE_FLOAT -> entry.floatValue
                    ?: throw IllegalStateException("Missing float value for settings key: $key")

                TYPE_BOOLEAN -> entry.booleanValue
                    ?: throw IllegalStateException("Missing boolean value for settings key: $key")

                else -> throw IllegalStateException("Unsupported settings entry type '${entry.type}' for key: $key")
            }
            result[key] = value
        }
        return result
    }

    private const val TYPE_STRING = "string"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_BOOLEAN = "boolean"
}
