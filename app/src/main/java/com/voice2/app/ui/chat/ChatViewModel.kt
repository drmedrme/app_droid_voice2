package com.voice2.app.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.voice2.app.data.api.Transcription
import com.voice2.app.data.audio.AudioRecorder
import com.voice2.app.data.preferences.SettingsPreferences
import com.voice2.app.data.preferences.TranscriptionMode
import com.voice2.app.data.repository.Voice2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: Voice2Repository,
    private val audioRecorder: AudioRecorder,
    private val settingsPreferences: SettingsPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestedTags = MutableStateFlow<List<String>>(emptyList())
    val suggestedTags: StateFlow<List<String>> = _suggestedTags.asStateFlow()

    private var audioFile: File? = null
    private var lastPhotoUri: Uri? = null
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var searchJob: Job? = null

    init {
        loadChats()
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    uploadText(matches[0])
                }
                _isRecording.value = false
            }
            override fun onError(error: Int) { 
                Log.e("Voice2", "Speech recognizer error code: $error")
                _isRecording.value = false 
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun setLastPhotoUri(uri: Uri?) {
        lastPhotoUri = uri
    }

    fun loadChats() {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            repository.getChats()
                .onSuccess { chats -> _uiState.value = ChatUiState.Success(chats) }
                .onFailure { e -> _uiState.value = ChatUiState.Error(e.message ?: "Unknown error") }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            if (query.isBlank()) loadChats() else performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = ChatUiState.Loading
        repository.searchChats(query)
            .onSuccess { chats -> _uiState.value = ChatUiState.Success(chats) }
            .onFailure { e -> _uiState.value = ChatUiState.Error(e.message ?: "Search failed") }
    }

    fun toggleRecording() {
        viewModelScope.launch {
            val mode = settingsPreferences.transcriptionMode.first()
            if (_isRecording.value) {
                if (mode == TranscriptionMode.HIGH_QUALITY) stopAudioRecording()
                else {
                    speechRecognizer.stopListening()
                    _isRecording.value = false
                }
            } else {
                delay(500)
                if (mode == TranscriptionMode.HIGH_QUALITY) startAudioRecording()
                else startSpeechRecognition()
            }
        }
    }

    private fun startAudioRecording() {
        val file = File(context.cacheDir, "recording.m4a")
        audioFile = file
        try {
            audioRecorder.start(file)
            _isRecording.value = true
        } catch (e: Exception) {
            _uiState.value = ChatUiState.Error("Recording failed: \$e.message}")
        }
    }

    private fun stopAudioRecording() {
        try {
            audioRecorder.stop()
            _isRecording.value = false
            audioFile?.let { uploadAudio(it) }
        } catch (e: Exception) {
            _uiState.value = ChatUiState.Error("Stop failed: \$e.message}")
        }
    }

    private fun startSpeechRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            speechRecognizer.startListening(intent)
            _isRecording.value = true
        } catch (e: Exception) {
            _uiState.value = ChatUiState.Error("Speech start failed: \$e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()
            if (location != null) Pair(location.latitude, location.longitude) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun uploadAudio(file: File) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            val location = getCurrentLocation()
            repository.uploadAudio(file, location?.first, location?.second)
                .onSuccess { transcription -> 
                    lastPhotoUri?.let { uri ->
                        repository.addTag(transcription.id, "LOCAL_PHOTO:\$uri}")
                    }
                    lastPhotoUri = null
                    loadChats() 
                }
                .onFailure { e -> _uiState.value = ChatUiState.Error("Upload failed: \$e.message}") }
        }
    }

    private fun uploadText(text: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            val location = getCurrentLocation()
            repository.transcribeText(text, location?.first, location?.second)
                .onSuccess { transcription -> 
                    lastPhotoUri?.let { uri ->
                        repository.addTag(transcription.id, "LOCAL_PHOTO:\$uri}")
                    }
                    lastPhotoUri = null
                    loadChats() 
                }
                .onFailure { e -> _uiState.value = ChatUiState.Error("Text upload failed: \$e.message}") }
        }
    }

    fun suggestTags(chatId: UUID) {
        viewModelScope.launch {
            repository.suggestTags(chatId).onSuccess { _suggestedTags.value = it }
        }
    }

    fun addTag(chatId: UUID, name: String) {
        viewModelScope.launch {
            repository.addTag(chatId, name).onSuccess { loadChats() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
    }
}

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(val chats: List<Transcription>) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
