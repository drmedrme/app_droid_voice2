package com.voice2.app.ui.todos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice2.app.data.api.TodoItem
import com.voice2.app.data.repository.Voice2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatGroup(
    val chatId: UUID?,
    val chatTitle: String?,
    val todos: List<TodoItem>
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: Voice2Repository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodoUiState>(TodoUiState.Loading)
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _editingTodoId = MutableStateFlow<UUID?>(null)
    val editingTodoId: StateFlow<UUID?> = _editingTodoId.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _voiceResult = MutableStateFlow<String?>(null)
    val voiceResult: StateFlow<String?> = _voiceResult.asStateFlow()

    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        loadTodos()
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _voiceResult.value = matches[0]
                }
                _isListening.value = false
            }
            override fun onError(error: Int) {
                Log.e("Voice2", "Todo speech recognizer error code: $error")
                _isListening.value = false
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

    fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            speechRecognizer.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("Voice2", "Voice input start failed: ${e.message}")
            _isListening.value = false
        }
    }

    fun stopVoiceInput() {
        speechRecognizer.stopListening()
        _isListening.value = false
    }

    fun clearVoiceResult() {
        _voiceResult.value = null
    }

    fun loadTodos() {
        viewModelScope.launch {
            val isRefresh = _uiState.value is TodoUiState.Success
            if (isRefresh) {
                _isRefreshing.value = true
            } else {
                _uiState.value = TodoUiState.Loading
            }
            repository.getTodos()
                .onSuccess { todos -> _uiState.value = TodoUiState.Success(buildGroups(todos)) }
                .onFailure { e -> _uiState.value = TodoUiState.Error(e.message ?: "Unknown error") }
            _isRefreshing.value = false
        }
    }

    private fun buildGroups(todos: List<TodoItem>): List<ChatGroup> {
        val grouped = todos.groupBy { it.chatId }
        val chatGroups = grouped
            .filter { it.key != null }
            .map { (chatId, items) ->
                ChatGroup(
                    chatId = chatId,
                    chatTitle = items.firstNotNullOfOrNull { it.chatTitle },
                    todos = items
                )
            }
            .sortedBy { it.chatTitle?.lowercase() ?: "" }
        val standalone = grouped[null]?.let { items ->
            listOf(ChatGroup(chatId = null, chatTitle = null, todos = items))
        } ?: emptyList()
        return chatGroups + standalone
    }

    fun createTodo(description: String) {
        viewModelScope.launch {
            repository.createTodo(description)
                .onSuccess { loadTodos() }
        }
    }

    fun toggleTodo(id: UUID) {
        viewModelScope.launch {
            repository.toggleTodo(id)
                .onSuccess { loadTodos() }
        }
    }

    fun deleteTodo(id: UUID) {
        viewModelScope.launch {
            repository.deleteTodo(id)
                .onSuccess { loadTodos() }
        }
    }

    fun startEditing(id: UUID) {
        _editingTodoId.value = id
    }

    fun cancelEditing() {
        _editingTodoId.value = null
        _voiceResult.value = null
    }

    fun confirmEdit(id: UUID, newDescription: String) {
        viewModelScope.launch {
            repository.updateTodoDescription(id, newDescription)
                .onSuccess {
                    _editingTodoId.value = null
                    _voiceResult.value = null
                    loadTodos()
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
    }
}

sealed class TodoUiState {
    object Loading : TodoUiState()
    data class Success(val groups: List<ChatGroup>) : TodoUiState()
    data class Error(val message: String) : TodoUiState()
}
