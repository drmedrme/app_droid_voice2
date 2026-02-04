package com.voice2.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice2.app.data.preferences.SettingsPreferences
import com.voice2.app.data.preferences.ThemeMode
import com.voice2.app.data.preferences.SettingsPreferences.Companion.DEFAULT_SPEECH_PAUSE_MS
import com.voice2.app.data.preferences.TranscriptionMode
import com.voice2.app.di.BaseUrlInterceptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import com.voice2.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

sealed class TestConnectionState {
    object Idle : TestConnectionState()
    object Testing : TestConnectionState()
    object Success : TestConnectionState()
    data class Error(val message: String) : TestConnectionState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    val themeMode = settingsPreferences.themeMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM
    )

    val transcriptionMode = settingsPreferences.transcriptionMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, TranscriptionMode.HIGH_QUALITY
    )

    val baseUrl = settingsPreferences.baseUrl.map { url ->
        if (url.isNullOrBlank()) BuildConfig.BASE_URL else url
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly, BuildConfig.BASE_URL
    )

    val speechPauseDuration = settingsPreferences.speechPauseDuration.stateIn(
        viewModelScope, SharingStarted.Eagerly, DEFAULT_SPEECH_PAUSE_MS
    )

    private val _testState = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testState = _testState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsPreferences.baseUrl.collect { url ->
                if (!url.isNullOrBlank()) {
                    baseUrlInterceptor.baseUrl = url
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsPreferences.setThemeMode(mode) }
    }

    fun setTranscriptionMode(mode: TranscriptionMode) {
        viewModelScope.launch { settingsPreferences.setTranscriptionMode(mode) }
    }

    fun setSpeechPauseDuration(ms: Long) {
        viewModelScope.launch { settingsPreferences.setSpeechPauseDuration(ms) }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch {
            settingsPreferences.setBaseUrl(url)
            baseUrlInterceptor.baseUrl = url
        }
    }

    fun testConnection(url: String) {
        viewModelScope.launch {
            _testState.value = TestConnectionState.Testing
            try {
                val testUrl = url.trimEnd('/') + "/chats/"
                val request = Request.Builder().url(testUrl).get().build()
                withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            _testState.value = TestConnectionState.Success
                        } else {
                            _testState.value = TestConnectionState.Error("HTTP ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                _testState.value = TestConnectionState.Error(e.message ?: "Connection failed")
            }
        }
    }
}
