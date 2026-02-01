package com.voice2.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice2.app.data.api.Transcription
import com.voice2.app.data.audio.AudioRecorder
import com.voice2.app.data.preferences.SettingsPreferences
import com.voice2.app.data.preferences.TranscriptionMode
import com.voice2.app.data.repository.Voice2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val repository: Voice2Repository,
    private val audioRecorder: AudioRecorder,
    private val settingsPreferences: SettingsPreferences,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _suggestedTags = MutableStateFlow<List<String>>(emptyList())
    val suggestedTags: StateFlow<List<String>> = _suggestedTags.asStateFlow()

    private val _relatedChats = MutableStateFlow<List<Transcription>>(emptyList())
    val relatedChats: StateFlow<List<Transcription>> = _relatedChats.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _isEditingText = MutableStateFlow(false)
    val isEditingText: StateFlow<Boolean> = _isEditingText.asStateFlow()

    private val _shareText = MutableStateFlow<String?>(null)
    val shareText: StateFlow<String?> = _shareText.asStateFlow()

    private val _isAppendRecording = MutableStateFlow(false)
    val isAppendRecording: StateFlow<Boolean> = _isAppendRecording.asStateFlow()

    private var appendAudioFile: File? = null
    private var appendSpeechRecognizer: SpeechRecognizer? = null

    init {
        loadChat()
        loadRelatedChats()
    }

    fun loadChat() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.getChat(UUID.fromString(chatId))
                .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Error loading chat") }
        }
    }

    fun enhance() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.enhanceChat(UUID.fromString(chatId))
                .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Enhance failed") }
        }
    }

    fun rewrite(mode: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.rewriteChat(UUID.fromString(chatId), mode)
                .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Rewrite failed") }
        }
    }

    fun revert() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.revertChat(UUID.fromString(chatId))
                .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Revert failed") }
        }
    }

    fun suggestTags() {
        viewModelScope.launch {
            repository.suggestTags(UUID.fromString(chatId))
                .onSuccess { tags -> _suggestedTags.value = tags }
        }
    }

    fun addTag(name: String) {
        viewModelScope.launch {
            repository.addTag(UUID.fromString(chatId), name)
                .onSuccess { chat ->
                    _uiState.value = DetailUiState.Success(chat)
                    _suggestedTags.value = _suggestedTags.value.filter { it != name }
                }
        }
    }

    fun createAlbum() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.createAlbum(UUID.fromString(chatId))
                .onSuccess { loadChat() }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Album creation failed") }
        }
    }

    fun loadRelatedChats() {
        viewModelScope.launch {
            repository.getRelatedChats(UUID.fromString(chatId))
                .onSuccess { _relatedChats.value = it }
        }
    }

    fun deleteChat(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            repository.deleteChat(UUID.fromString(chatId))
                .onSuccess { onDeleted() }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Delete failed") }
        }
    }

    fun extractTodos() {
        viewModelScope.launch {
            repository.extractTodos(UUID.fromString(chatId))
                .onSuccess { todos ->
                    _actionMessage.value = "Extracted ${todos.size} todo(s)"
                }
                .onFailure { e ->
                    _actionMessage.value = "Extract failed: ${e.message}"
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    // --- Summarize ---

    fun summarize() {
        viewModelScope.launch {
            _isSummarizing.value = true
            repository.summarizeChat(UUID.fromString(chatId))
                .onSuccess { _summary.value = it }
                .onFailure { e -> _actionMessage.value = "Summarize failed: ${e.message}" }
            _isSummarizing.value = false
        }
    }

    fun clearSummary() {
        _summary.value = null
    }

    // --- Edit Text ---

    fun startEditingText() {
        _isEditingText.value = true
    }

    fun cancelEditingText() {
        _isEditingText.value = false
    }

    fun saveText(newText: String) {
        viewModelScope.launch {
            _isEditingText.value = false
            _uiState.value = DetailUiState.Loading
            repository.updateChatText(UUID.fromString(chatId), newText)
                .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                .onFailure { e -> _uiState.value = DetailUiState.Error(e.message ?: "Save failed") }
        }
    }

    // --- Share / Export Markdown ---

    fun shareMarkdown() {
        viewModelScope.launch {
            repository.exportMarkdown(UUID.fromString(chatId))
                .onSuccess { _shareText.value = it }
                .onFailure { e -> _actionMessage.value = "Export failed: ${e.message}" }
        }
    }

    fun clearShareText() {
        _shareText.value = null
    }

    // --- Append Audio ---

    fun startAppendRecording() {
        viewModelScope.launch {
            val mode = settingsPreferences.transcriptionMode.first()
            if (mode == TranscriptionMode.HIGH_QUALITY) {
                val file = File(context.cacheDir, "append_recording.m4a")
                appendAudioFile = file
                try {
                    audioRecorder.start(file)
                    _isAppendRecording.value = true
                } catch (e: Exception) {
                    _actionMessage.value = "Recording failed: ${e.message}"
                }
            } else {
                try {
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    appendSpeechRecognizer = recognizer
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                appendTextToChat(matches[0])
                            }
                            _isAppendRecording.value = false
                        }
                        override fun onError(error: Int) {
                            Log.e("Voice2", "Append speech error: $error")
                            _isAppendRecording.value = false
                            _actionMessage.value = "Speech recognition failed"
                        }
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    }
                    recognizer.startListening(intent)
                    _isAppendRecording.value = true
                } catch (e: Exception) {
                    _actionMessage.value = "Speech start failed: ${e.message}"
                }
            }
        }
    }

    fun stopAppendRecording() {
        viewModelScope.launch {
            val mode = settingsPreferences.transcriptionMode.first()
            if (mode == TranscriptionMode.HIGH_QUALITY) {
                try {
                    audioRecorder.stop()
                    _isAppendRecording.value = false
                    appendAudioFile?.let { file ->
                        _uiState.value = DetailUiState.Loading
                        repository.appendAudio(UUID.fromString(chatId), file)
                            .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                            .onFailure { e ->
                                _actionMessage.value = "Append failed: ${e.message}"
                                loadChat()
                            }
                    }
                } catch (e: Exception) {
                    _isAppendRecording.value = false
                    _actionMessage.value = "Stop failed: ${e.message}"
                }
            } else {
                appendSpeechRecognizer?.stopListening()
                _isAppendRecording.value = false
            }
        }
    }

    private fun appendTextToChat(spokenText: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DetailUiState.Success) {
                val newText = currentState.chat.text + "\n\n" + spokenText
                _uiState.value = DetailUiState.Loading
                repository.updateChatText(UUID.fromString(chatId), newText)
                    .onSuccess { chat -> _uiState.value = DetailUiState.Success(chat) }
                    .onFailure { e ->
                        _actionMessage.value = "Append text failed: ${e.message}"
                        loadChat()
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        appendSpeechRecognizer?.destroy()
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val chat: Transcription) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
