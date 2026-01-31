package com.voice2.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice2.app.data.api.Transcription
import com.voice2.app.data.repository.Voice2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val repository: Voice2Repository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _suggestedTags = MutableStateFlow<List<String>>(emptyList())
    val suggestedTags: StateFlow<List<String>> = _suggestedTags.asStateFlow()

    init {
        loadChat()
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
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val chat: Transcription) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
