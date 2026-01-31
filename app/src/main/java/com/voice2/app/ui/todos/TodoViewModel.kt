package com.voice2.app.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice2.app.data.api.TodoItem
import com.voice2.app.data.repository.Voice2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: Voice2Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodoUiState>(TodoUiState.Loading)
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    init {
        loadTodos()
    }

    fun loadTodos() {
        viewModelScope.launch {
            _uiState.value = TodoUiState.Loading
            repository.getTodos()
                .onSuccess { todos -> _uiState.value = TodoUiState.Success(todos) }
                .onFailure { e -> _uiState.value = TodoUiState.Error(e.message ?: "Unknown error") }
        }
    }

    fun toggleTodo(id: UUID, completed: Boolean) {
        viewModelScope.launch {
            repository.updateTodo(id, completed)
                .onSuccess { loadTodos() }
        }
    }
}

sealed class TodoUiState {
    object Loading : TodoUiState()
    data class Success(val todos: List<TodoItem>) : TodoUiState()
    data class Error(val message: String) : TodoUiState()
}
