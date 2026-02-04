package com.voice2.app.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private fun formatDetailTimestamp(timestamp: String): String {
    return try {
        val time = ZonedDateTime.parse(timestamp)
        time.format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
    } catch (e: Exception) {
        timestamp.take(16)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestedExistingTags by viewModel.suggestedExistingTags.collectAsState()
    val proposedTags by viewModel.proposedTags.collectAsState()
    val relatedChats by viewModel.relatedChats.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val isEditingText by viewModel.isEditingText.collectAsState()
    val shareText by viewModel.shareText.collectAsState()
    val isAppendRecording by viewModel.isAppendRecording.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val isTagPickerExpanded by viewModel.isTagPickerExpanded.collectAsState()
    val sourceChats by viewModel.sourceChats.collectAsState()
    val combinedInto by viewModel.combinedInto.collectAsState()
    val selectedRelatedIds by viewModel.selectedRelatedIds.collectAsState()
    val isCombining by viewModel.isCombining.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCombineConfirm by remember { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.startAppendRecording()
    }

    // Handle share intent
    LaunchedEffect(shareText) {
        shareText?.let { markdown ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_TEXT, markdown)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share as Markdown"))
            viewModel.clearShareText()
        }
    }

    // Show snackbar for action messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteChat(onDeleted = onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showCombineConfirm) {
        AlertDialog(
            onDismissRequest = { showCombineConfirm = false },
            title = { Text("Combine Chats") },
            text = {
                Text(
                    "This will combine ${selectedRelatedIds.size + 1} chats:\n\n" +
                    "\u2022 AI-synthesize text from all selected notes\n" +
                    "\u2022 Move all todos to the combined chat\n" +
                    "\u2022 Merge tags from all sources\n" +
                    "\u2022 Archive original chats"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCombineConfirm = false
                        viewModel.combineChats { newId ->
                            onNavigateToChat(newId.toString())
                        }
                    },
                    enabled = !isCombining
                ) { Text(if (isCombining) "Combining..." else "Combine") }
            },
            dismissButton = {
                TextButton(onClick = { showCombineConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shareMarkdown() }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is DetailUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                is DetailUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Image Preview
                        val photoTag = state.chat.tags.find { it.name.startsWith("LOCAL_PHOTO:") }
                        if (photoTag != null) {
                            val uriString = photoTag.name.substringAfter("LOCAL_PHOTO:")
                            val photoUri = Uri.parse(uriString)
                            val photoExists = remember(uriString) {
                                if (photoUri.scheme == "file") {
                                    val path = photoUri.path
                                    path != null && java.io.File(path).exists()
                                } else true // content:// URIs -- let Coil attempt load
                            }
                            if (photoExists) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Captured Item",
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = "Photo no longer available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(24.dp).fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Title and timestamp
                        Text(
                            text = state.chat.mergedTitle ?: "Transcription",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = formatDetailTimestamp(state.chat.timestamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Merged-into banner
                        if (state.chat.isMerged && combinedInto != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFEF3C7)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            combinedInto?.let { onNavigateToChat(it.id.toString()) }
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "This chat was combined into a new note",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF92400E)
                                        )
                                        combinedInto?.mergedTitle?.let { title ->
                                            Text(
                                                title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            combinedInto?.let { onNavigateToChat(it.id.toString()) }
                                        }
                                    ) {
                                        Text("View Combined", color = Color(0xFF92400E))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Content card with edit support
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Edit button row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (!isEditingText) {
                                        IconButton(
                                            onClick = { viewModel.startEditingText() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit text",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                AnimatedContent(
                                    targetState = isEditingText,
                                    transitionSpec = {
                                        (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                                    },
                                    label = "edit_toggle"
                                ) { editing ->
                                    if (editing) {
                                        var editText by remember(state.chat.text) { mutableStateOf(state.chat.text) }
                                        Column {
                                            OutlinedTextField(
                                                value = editText,
                                                onValueChange = { editText = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 3
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(onClick = { viewModel.cancelEditingText() }) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Cancel")
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Button(onClick = { viewModel.saveText(editText) }) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Save")
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = state.chat.text,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Append recording button
                        AnimatedContent(
                            targetState = isAppendRecording,
                            label = "append_recording"
                        ) { recording ->
                            if (recording) {
                                Button(
                                    onClick = { viewModel.stopAppendRecording() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop Append Recording")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        if (hasAudioPermission) {
                                            viewModel.startAppendRecording()
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Append Recording")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // AI Actions section
                        Text(text = "AI Actions", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Enhance button
                        FilledTonalButton(
                            onClick = { viewModel.enhance() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enhance")
                        }

                        // Summarize button
                        FilledTonalButton(
                            onClick = { viewModel.summarize() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            enabled = !isSummarizing
                        ) {
                            if (isSummarizing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.ShortText, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSummarizing) "Summarizing..." else "Summarize")
                        }

                        // Summary card
                        AnimatedVisibility(
                            visible = summary != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            summary?.let { text ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Summary",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            IconButton(
                                                onClick = { viewModel.clearSummary() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Dismiss",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Rewrite modes row
                        Text(
                            text = "Rewrite As:",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                "clarity" to Color(0xFF2563EB),
                                "concise" to Color(0xFF9333EA),
                                "organized" to Color(0xFF22C55E),
                                "professional" to Color(0xFFF59E0B)
                            )
                            modes.forEach { (mode, color) ->
                                FilledTonalButton(
                                    onClick = { viewModel.rewrite(mode) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = color.copy(alpha = 0.15f),
                                        contentColor = color
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        mode.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Revert button (show when chat has been rewritten)
                        if (state.chat.originalText != null) {
                            OutlinedButton(
                                onClick = { viewModel.revert() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Revert to Original")
                            }
                        }

                        // Extract Todos button
                        OutlinedButton(
                            onClick = { viewModel.extractTodos() },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Extract Todos")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Google Photos section
                        Text(text = "Google Photos", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val time = ZonedDateTime.parse(state.chat.timestamp)
                                    val dateStr = time.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    val url = "https://photos.google.com/search/${URLEncoder.encode(dateStr, "UTF-8")}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("Voice2", "Failed to parse time for photos", e)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Find Photos for this Time")
                        }

                        if (state.chat.photoAlbumUrl == null) {
                            OutlinedButton(
                                onClick = { viewModel.createAlbum() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PhotoAlbum, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Dedicated Album")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.chat.photoAlbumUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.PhotoAlbum, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Linked Album")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tags section
                        Text(text = "Tags", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.chat.tags.filter { !it.name.startsWith("LOCAL_PHOTO:") }.forEach { tag ->
                                val chipColor = tag.color?.let { parseTagColor(it) }
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(tag.name) },
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    colors = chipColor?.let {
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = it.copy(alpha = 0.2f),
                                            labelColor = it
                                        )
                                    } ?: SuggestionChipDefaults.suggestionChipColors()
                                )
                            }
                        }

                        // Quick Tags picker
                        if (allTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleTagPickerExpanded() }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Quick Tags",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Icon(
                                    if (isTagPickerExpanded) Icons.Default.KeyboardArrowUp
                                    else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isTagPickerExpanded) "Collapse" else "Expand",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(
                                visible = isTagPickerExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val currentTagIds = state.chat.tags.map { it.id }.toSet()
                                    allTags.filter { !it.name.startsWith("LOCAL_PHOTO:") }.forEach { tag ->
                                        val isSelected = tag.id in currentTagIds
                                        val chipColor = tag.color?.let { parseTagColor(it) }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.toggleTag(tag) },
                                            label = { Text(tag.name) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            colors = chipColor?.let {
                                                FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = it.copy(alpha = 0.2f),
                                                    selectedLabelColor = it,
                                                    selectedLeadingIconColor = it
                                                )
                                            } ?: FilterChipDefaults.filterChipColors()
                                        )
                                    }
                                }
                            }
                        }

                        // Manual tag entry
                        var newTagText by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTagText,
                                onValueChange = { newTagText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Add a tag...") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = {
                                    if (newTagText.isNotBlank()) {
                                        viewModel.addTag(newTagText.trim())
                                        newTagText = ""
                                    }
                                },
                                enabled = newTagText.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add tag")
                            }
                        }

                        // AI Tag Suggestions
                        if (suggestedExistingTags.isNotEmpty() || proposedTags.isNotEmpty()) {
                            // Matching existing tags
                            if (suggestedExistingTags.isNotEmpty()) {
                                Text(
                                    text = "Matching Existing Tags",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(
                                    text = "Tap to apply:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    suggestedExistingTags.forEach { tag ->
                                        val chipColor = tag.color?.let { parseTagColor(it) }
                                        AssistChip(
                                            onClick = { viewModel.applyExistingTag(tag) },
                                            label = { Text(tag.name) },
                                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            colors = chipColor?.let {
                                                AssistChipDefaults.assistChipColors(
                                                    containerColor = it.copy(alpha = 0.15f),
                                                    labelColor = it,
                                                    leadingIconContentColor = it
                                                )
                                            } ?: AssistChipDefaults.assistChipColors()
                                        )
                                    }
                                }
                            }

                            // Proposed new tags
                            if (proposedTags.isNotEmpty()) {
                                Text(
                                    text = "Proposed New Tags",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(
                                    text = "Accept to add to your tag system:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    proposedTags.forEach { tagName ->
                                        OutlinedButton(
                                            onClick = { viewModel.acceptProposedTag(tagName) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tagName, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            TextButton(onClick = { viewModel.suggestTags() }) {
                                Text("Explore Tags")
                            }
                        }

                        // Related Chats section
                        if (relatedChats.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "Related Chats", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            relatedChats.forEach { related ->
                                val isSelected = selectedRelatedIds.contains(related.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onNavigateToChat(related.id.toString()) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleRelatedSelection(related.id) }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = related.mergedTitle ?: "Transcription",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = related.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Combine button
                            if (selectedRelatedIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showCombineConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isCombining
                                ) {
                                    if (isCombining) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Combining...")
                                    } else {
                                        Text("Combine ${selectedRelatedIds.size + 1} Chats")
                                    }
                                }
                            }
                        }

                        // Source Notes section (for combined chats)
                        if (sourceChats.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Source Notes (${sourceChats.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            sourceChats.forEach { source ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onNavigateToChat(source.id.toString()) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = source.mergedTitle ?: formatDetailTimestamp(source.timestamp),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        Text(
                                            text = source.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatDetailTimestamp(source.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom spacer for scroll clearance
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) { content() }
}

private fun parseTagColor(hex: String): Color? {
    return try {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        null
    }
}
