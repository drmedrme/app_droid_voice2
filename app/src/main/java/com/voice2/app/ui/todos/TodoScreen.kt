package com.voice2.app.ui.todos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voice2.app.data.api.TodoItem
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val editingTodoId by viewModel.editingTodoId.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val voiceResult by viewModel.voiceResult.collectAsState()
    var newTodoText by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.startVoiceInput()
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadTodos() }
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showInput = !showInput }) {
                Icon(Icons.Default.Add, contentDescription = "Add Todo")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val hasCompleted = (uiState as? TodoUiState.Success)
                ?.groups?.any { g -> g.todos.any { it.completed } } == true

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Todos",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                AnimatedVisibility(
                    visible = hasCompleted,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = { viewModel.deleteCompletedTodos() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Clear completed")
                    }
                }
            }

            // Add todo input — native expand/collapse
            AnimatedVisibility(
                visible = showInput,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("What needs to be done?") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newTodoText.isNotBlank()) {
                                viewModel.createTodo(newTodoText.trim())
                                newTodoText = ""
                                showInput = false
                            }
                        },
                        enabled = newTodoText.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                when (val state = uiState) {
                    is TodoUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is TodoUiState.Error -> Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                    is TodoUiState.Success -> {
                        val allTodos = state.groups.flatMap { it.todos }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            if (allTodos.isEmpty()) {
                                item(key = "empty") {
                                    Box(
                                        modifier = Modifier.fillParentMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No todos yet. Tap + to add one.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(32.dp)
                                        )
                                    }
                                }
                            }

                            state.groups.forEach { group ->
                                val pending = group.todos.filter { !it.completed }
                                val completed = group.todos.filter { it.completed }

                                if (group.todos.isNotEmpty()) {
                                    item(key = "group_header_${group.chatId ?: "standalone"}") {
                                        ChatGroupHeader(
                                            title = group.chatTitle ?: "Standalone",
                                            count = group.todos.size,
                                            modifier = Modifier.animateItemPlacement()
                                        )
                                    }

                                    if (pending.isNotEmpty()) {
                                        item(key = "pending_header_${group.chatId ?: "standalone"}") {
                                            Text(
                                                "Pending (${pending.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                                    .animateItemPlacement()
                                            )
                                        }
                                        items(pending, key = { it.id }) { todo ->
                                            SwipeTodoEntry(
                                                todo = todo,
                                                isEditing = editingTodoId == todo.id,
                                                isListening = isListening && editingTodoId == todo.id,
                                                voiceResult = if (editingTodoId == todo.id) voiceResult else null,
                                                onToggle = { viewModel.toggleTodo(todo.id) },
                                                onDelete = { viewModel.deleteTodo(todo.id) },
                                                onLongPress = { viewModel.startEditing(todo.id) },
                                                onEditConfirm = { desc -> viewModel.confirmEdit(todo.id, desc) },
                                                onEditCancel = { viewModel.cancelEditing() },
                                                onStartVoice = {
                                                    if (hasAudioPermission) {
                                                        viewModel.startVoiceInput()
                                                    } else {
                                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                },
                                                onStopVoice = { viewModel.stopVoiceInput() },
                                                onVoiceResultConsumed = { viewModel.clearVoiceResult() },
                                                modifier = Modifier.animateItemPlacement()
                                            )
                                        }
                                    }

                                    if (completed.isNotEmpty()) {
                                        item(key = "completed_header_${group.chatId ?: "standalone"}") {
                                            Text(
                                                "Completed (${completed.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                                    .animateItemPlacement()
                                            )
                                        }
                                        items(completed, key = { it.id }) { todo ->
                                            SwipeTodoEntry(
                                                todo = todo,
                                                isEditing = editingTodoId == todo.id,
                                                isListening = isListening && editingTodoId == todo.id,
                                                voiceResult = if (editingTodoId == todo.id) voiceResult else null,
                                                onToggle = { viewModel.toggleTodo(todo.id) },
                                                onDelete = { viewModel.deleteTodo(todo.id) },
                                                onLongPress = { viewModel.startEditing(todo.id) },
                                                onEditConfirm = { desc -> viewModel.confirmEdit(todo.id, desc) },
                                                onEditCancel = { viewModel.cancelEditing() },
                                                onStartVoice = {
                                                    if (hasAudioPermission) {
                                                        viewModel.startVoiceInput()
                                                    } else {
                                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                },
                                                onStopVoice = { viewModel.stopVoiceInput() },
                                                onVoiceResultConsumed = { viewModel.clearVoiceResult() },
                                                modifier = Modifier.animateItemPlacement()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ChatGroupHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipeTodoEntry(
    todo: TodoItem,
    isEditing: Boolean,
    isListening: Boolean,
    voiceResult: String?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    onEditConfirm: (String) -> Unit,
    onEditCancel: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onVoiceResultConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            if (value == DismissValue.DismissedToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        modifier = modifier,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.DismissedToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = color)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(end = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }
            }
        },
        dismissContent = {
            AnimatedContent(
                targetState = isEditing,
                transitionSpec = {
                    (fadeIn() + expandVertically()).togetherWith(
                        fadeOut() + shrinkVertically()
                    ) using SizeTransform(clip = false)
                },
                label = "edit_toggle"
            ) { editing ->
                if (editing) {
                    EditableTodoEntry(
                        todo = todo,
                        isListening = isListening,
                        voiceResult = voiceResult,
                        onConfirm = onEditConfirm,
                        onCancel = onEditCancel,
                        onStartVoice = onStartVoice,
                        onStopVoice = onStopVoice,
                        onVoiceResultConsumed = onVoiceResultConsumed
                    )
                } else {
                    TodoEntry(
                        todo = todo,
                        onToggle = onToggle,
                        onLongPress = onLongPress
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoEntry(todo: TodoItem, onToggle: () -> Unit, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (todo.completed)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (todo.completed) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.completed, onCheckedChange = { onToggle() })
            Text(
                text = todo.description,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EditableTodoEntry(
    todo: TodoItem,
    isListening: Boolean,
    voiceResult: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onVoiceResultConsumed: () -> Unit
) {
    var text by remember(todo.id) { mutableStateOf(todo.description) }

    // Append voice result when it arrives
    LaunchedEffect(voiceResult) {
        if (voiceResult != null) {
            text = if (text.isBlank()) voiceResult else "$text $voiceResult"
            onVoiceResultConsumed()
        }
    }

    val micTint by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "mic_tint"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
            IconButton(
                onClick = { if (isListening) onStopVoice() else onStartVoice() }
            ) {
                Icon(
                    if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop voice" else "Voice input",
                    tint = micTint
                )
            }
            IconButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }
    }
}
