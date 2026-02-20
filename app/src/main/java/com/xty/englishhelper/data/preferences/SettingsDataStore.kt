package com.xty.englishhelper.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xty.englishhelper.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    val apiKey: Flow<String> = dataStore.data.map { it[API_KEY] ?: "" }
    val model: Flow<String> = dataStore.data.map { it[MODEL] ?: Constants.DEFAULT_MODEL }
    val baseUrl: Flow<String> = dataStore.data.map { it[BASE_URL] ?: Constants.ANTHROPIC_BASE_URL }

    suspend fun setApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[MODEL] = model }
    }

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { it[BASE_URL] = url }
    }
}
