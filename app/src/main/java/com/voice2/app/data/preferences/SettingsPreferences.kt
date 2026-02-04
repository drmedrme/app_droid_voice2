package com.voice2.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val speechPauseDurationKey = longPreferencesKey("speech_pause_duration_ms")

    companion object {
        const val DEFAULT_SPEECH_PAUSE_MS = 3000L  // 3 seconds default
        const val MIN_SPEECH_PAUSE_MS = 1500L
        const val MAX_SPEECH_PAUSE_MS = 10000L
    }

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

    /** Silence duration (ms) before Android speech recognizer finalizes. */
    val speechPauseDuration: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[speechPauseDurationKey] ?: DEFAULT_SPEECH_PAUSE_MS
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

    suspend fun setSpeechPauseDuration(ms: Long) {
        context.dataStore.edit { preferences ->
            preferences[speechPauseDurationKey] = ms.coerceIn(MIN_SPEECH_PAUSE_MS, MAX_SPEECH_PAUSE_MS)
        }
    }
}
