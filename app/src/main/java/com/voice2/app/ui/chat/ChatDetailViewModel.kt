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
import com.voice2.app.data.api.CombineChatsResponse
import com.voice2.app.data.api.Tag
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

    private val _suggestedExistingTags = MutableStateFlow<List<Tag>>(emptyList())
    val suggestedExistingTags: StateFlow<List<Tag>> = _suggestedExistingTags.asStateFlow()

    private val _proposedTags = MutableStateFlow<List<String>>(emptyList())
    val proposedTags: StateFlow<List<String>> = _proposedTags.asStateFlow()

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

    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    private val _isTagPickerExpanded = MutableStateFlow(false)
    val isTagPickerExpanded: StateFlow<Boolean> = _isTagPickerExpanded.asStateFlow()

    private val _sourceChats = MutableStateFlow<List<Transcription>>(emptyList())
    val sourceChats: StateFlow<List<Transcription>> = _sourceChats.asStateFlow()

    private val _combinedInto = MutableStateFlow<Transcription?>(null)
    val combinedInto: StateFlow<Transcription?> = _combinedInto.asStateFlow()

    private val _selectedRelatedIds = MutableStateFlow<Set<UUID>>(emptySet())
    val selectedRelatedIds: StateFlow<Set<UUID>> = _selectedRelatedIds.asStateFlow()

    private val _isCombining = MutableStateFlow(false)
    val isCombining: StateFlow<Boolean> = _isCombining.asStateFlow()

    private var appendAudioFile: File? = null
    private val appendSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        setupAppendSpeechRecognizer()
        loadChat()
        loadRelatedChats()
        loadAllTags()
        loadSourceChats()
        loadCombinedInto()
    }

    private fun setupAppendSpeechRecognizer() {
        appendSpeechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    appendTextToChat(matches[0])
                }
                _isAppendRecording.value = false
            }
            override fun onError(error: Int) {
                Log.e("Voice2", "Append speech error code: $error")
                _isAppendRecording.value = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input (timeout)"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing audio permission"
                    else -> "Speech error ($error)"
                }
                _actionMessage.value = msg
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
                .onSuccess { response ->
                    _suggestedExistingTags.value = response.existingTags
                    _proposedTags.value = response.proposedTags
                }
        }
    }

    /** Apply an existing tag to this chat. */
    fun applyExistingTag(tag: Tag) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DetailUiState.Success) {
                val currentTagIds = currentState.chat.tags.map { it.id }
                if (tag.id in currentTagIds) return@launch
                repository.updateChatTags(UUID.fromString(chatId), currentTagIds + tag.id)
                    .onSuccess { chat ->
                        _uiState.value = DetailUiState.Success(chat)
                        _suggestedExistingTags.value = _suggestedExistingTags.value.filter { it.id != tag.id }
                    }
            }
        }
    }

    /** Accept a proposed tag: create it in the DB, then apply it to this chat. */
    fun acceptProposedTag(name: String) {
        viewModelScope.launch {
            repository.addTag(UUID.fromString(chatId), name)
                .onSuccess { chat ->
                    _uiState.value = DetailUiState.Success(chat)
                    _proposedTags.value = _proposedTags.value.filter { it != name }
                }
        }
    }

    /** Add a manually typed tag (creates if needed, then applies). */
    fun addTag(name: String) {
        viewModelScope.launch {
            repository.addTag(UUID.fromString(chatId), name)
                .onSuccess { chat ->
                    _uiState.value = DetailUiState.Success(chat)
                    _proposedTags.value = _proposedTags.value.filter { it != name }
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
                .onSuccess { chats ->
                    _relatedChats.value = chats.filter { !it.isMerged }
                }
        }
    }

    private fun loadSourceChats() {
        viewModelScope.launch {
            repository.getSourceChats(UUID.fromString(chatId))
                .onSuccess { _sourceChats.value = it }
        }
    }

    private fun loadCombinedInto() {
        viewModelScope.launch {
            repository.getCombinedInto(UUID.fromString(chatId))
                .onSuccess { _combinedInto.value = it }
        }
    }

    fun toggleRelatedSelection(id: UUID) {
        val current = _selectedRelatedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedRelatedIds.value = current
    }

    fun combineChats(onNavigate: (UUID) -> Unit) {
        viewModelScope.launch {
            _isCombining.value = true
            val allIds = listOf(UUID.fromString(chatId)) + _selectedRelatedIds.value.toList()
            repository.combineChats(allIds)
                .onSuccess { response ->
                    _actionMessage.value = "Combined ${allIds.size} chats. ${response.todosMoved} todo(s) moved."
                    _selectedRelatedIds.value = emptySet()
                    onNavigate(response.combinedChat.id)
                }
                .onFailure { e ->
                    _actionMessage.value = "Combine failed: ${e.message}"
                }
            _isCombining.value = false
        }
    }

    private fun loadAllTags() {
        viewModelScope.launch {
            repository.getTags()
                .onSuccess { _allTags.value = it }
        }
    }

    fun toggleTagPickerExpanded() {
        _isTagPickerExpanded.value = !_isTagPickerExpanded.value
    }

    fun toggleTag(tag: Tag) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DetailUiState.Success) {
                val currentTagIds = currentState.chat.tags.map { it.id }
                val newTagIds = if (tag.id in currentTagIds) {
                    currentTagIds.filter { it != tag.id }
                } else {
                    currentTagIds + tag.id
                }
                repository.updateChatTags(UUID.fromString(chatId), newTagIds)
                    .onSuccess { chat ->
                        _uiState.value = DetailUiState.Success(chat)
                    }
                    .onFailure { e ->
                        _actionMessage.value = "Failed to update tags: ${e.message}"
                    }
            }
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
                    val pauseMs = settingsPreferences.speechPauseDuration.first()
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                    }
                    appendSpeechRecognizer.startListening(intent)
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
                appendSpeechRecognizer.stopListening()
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
        appendSpeechRecognizer.destroy()
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val chat: Transcription) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
