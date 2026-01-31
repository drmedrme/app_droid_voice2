package com.voice2.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val transcriptionModeKey = stringPreferencesKey("transcription_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[themeModeKey] ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val transcriptionMode: Flow<TranscriptionMode> = context.dataStore.data.map { preferences ->
        try {
            TranscriptionMode.valueOf(preferences[transcriptionModeKey] ?: TranscriptionMode.HIGH_QUALITY.name)
        } catch (e: Exception) {
            TranscriptionMode.HIGH_QUALITY
        }
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[baseUrlKey]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    suspend fun setTranscriptionMode(mode: TranscriptionMode) {
        context.dataStore.edit { preferences ->
            preferences[transcriptionModeKey] = mode.name
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[baseUrlKey] = url
        }
    }
}
