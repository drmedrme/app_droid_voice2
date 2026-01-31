package com.voice2.app.ui.todos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voice2.app.data.api.TodoItem

@Composable
fun TodoScreen(
    viewModel: TodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TodoUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is TodoUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            is TodoUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.todos) { todo ->
                        TodoEntry(todo, onToggle = { viewModel.toggleTodo(todo.id, !todo.completed) })
                    }
                }
            }
        }
    }
}

@Composable
fun TodoEntry(todo: TodoItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = todo.completed, onCheckedChange = { onToggle() })
        Text(text = todo.description, modifier = Modifier.weight(1f))
    }
}
