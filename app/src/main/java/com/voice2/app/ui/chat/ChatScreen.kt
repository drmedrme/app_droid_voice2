package com.voice2.app.ui.chat

import android.Manifest
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.voice2.app.data.api.Transcription
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

fun createImageUri(context: Context): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Voice2_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Voice2")
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onChatClick: (String) -> Unit,
    viewModel: ChatViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchListening by viewModel.isSearchListening.collectAsState()
    val filtersExpanded by viewModel.searchFiltersExpanded.collectAsState()
    val fuzzyEnabled by viewModel.fuzzyEnabled.collectAsState()
    val boostRecent by viewModel.boostRecent.collectAsState()
    val dateFrom by viewModel.dateFrom.collectAsState()
    val dateTo by viewModel.dateTo.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val tagFacets by viewModel.tagFacets.collectAsState()

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
        if (granted) viewModel.startVoiceSearch()
    }

    val micTint by animateColorAsState(
        targetValue = if (isSearchListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "search_mic_tint"
    )

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadChats() }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar with filter toggle
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search chats...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    IconButton(onClick = { viewModel.toggleSearchFilters() }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Search filters",
                            tint = if (filtersExpanded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (isSearchListening) {
                            viewModel.stopVoiceSearch()
                        } else if (hasAudioPermission) {
                            viewModel.startVoiceSearch()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            if (isSearchListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isSearchListening) "Stop voice search" else "Voice search",
                            tint = micTint
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        // Filter panel
        AnimatedVisibility(
            visible = filtersExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Toggle chips row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = fuzzyEnabled,
                        onClick = { viewModel.setFuzzyEnabled(!fuzzyEnabled) },
                        label = { Text("Fuzzy") }
                    )
                    FilterChip(
                        selected = boostRecent,
                        onClick = { viewModel.setBoostRecent(!boostRecent) },
                        label = { Text("Boost recent") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date range chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            showDatePicker(context) { date ->
                                viewModel.setDateFrom(date)
                            }
                        },
                        label = { Text(dateFrom?.let { "From: $it" } ?: "From date") },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = if (dateFrom != null) {
                            {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp).clickable { viewModel.setDateFrom(null) }
                                )
                            }
                        } else null
                    )
                    AssistChip(
                        onClick = {
                            showDatePicker(context) { date ->
                                viewModel.setDateTo(date)
                            }
                        },
                        label = { Text(dateTo?.let { "To: $it" } ?: "To date") },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = if (dateTo != null) {
                            {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp).clickable { viewModel.setDateTo(null) }
                                )
                            }
                        } else null
                    )
                }

                // Tag facet chips
                if (tagFacets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tagFacets.forEach { facet ->
                            val isSelected = selectedTags.contains(facet.name)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleTagFilter(facet.name) },
                                label = {
                                    Text(
                                        "${facet.name}${facet.count?.let { " ($it)" } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = facet.color?.let { hex ->
                                    parseColor(hex)?.let { color ->
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = color.copy(alpha = 0.2f),
                                            selectedLabelColor = color
                                        )
                                    }
                                } ?: FilterChipDefaults.filterChipColors()
                            )
                        }
                    }
                }

                // Clear all filters
                TextButton(
                    onClick = { viewModel.clearFilters() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Clear all filters", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when (val state = uiState) {
                is ChatUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ChatUiState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                is ChatUiState.Success -> {
                    if (state.chats.isEmpty()) {
                        Text(
                            "No chats yet. Tap the mic to start recording.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.chats, key = { it.id }) { chat ->
                                ChatEntry(
                                    chat = chat,
                                    onClick = { onChatClick(chat.id.toString()) },
                                    modifier = Modifier.animateItemPlacement(tween(300))
                                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatEntry(chat: Transcription, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = chat.mergedTitle ?: "Transcription",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = chat.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formattedTime = formatTimestamp(chat.timestamp)
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            formattedTime,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(28.dp)
                )

                val visibleTags = chat.tags.filter { !it.name.startsWith("LOCAL_PHOTO:") }.take(3)
                if (visibleTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        visibleTags.forEach { tag ->
                            val chipColor = tag.color?.let { parseColor(it) }
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(tag.name, style = MaterialTheme.typography.labelSmall)
                                },
                                modifier = Modifier.height(28.dp),
                                colors = chipColor?.let {
                                    SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = it.copy(alpha = 0.2f),
                                        labelColor = it
                                    )
                                } ?: SuggestionChipDefaults.suggestionChipColors()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val time = ZonedDateTime.parse(timestamp)
        time.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    } catch (e: Exception) {
        timestamp.take(16)
    }
}

private fun parseColor(hex: String): Color? {
    return try {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        null
    }
}

private fun showDatePicker(context: Context, onDateSelected: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val date = LocalDate.of(year, month + 1, dayOfMonth)
            onDateSelected(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}
