package com.voice2.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice2.app.data.preferences.SettingsPreferences
import com.voice2.app.data.preferences.ThemeMode
import com.voice2.app.data.preferences.TranscriptionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    val themeMode = settingsPreferences.themeMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM
    )

    val transcriptionMode = settingsPreferences.transcriptionMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, TranscriptionMode.HIGH_QUALITY
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsPreferences.setThemeMode(mode) }
    }

    fun setTranscriptionMode(mode: TranscriptionMode) {
        viewModelScope.launch { settingsPreferences.setTranscriptionMode(mode) }
    }
}
