package com.voice2.app.ui.chat

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestedTags by viewModel.suggestedTags.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is DetailUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is DetailUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Image Preview (Look for special LOCAL_PHOTO tag)
                        val photoTag = state.chat.tags.find { it.name.startsWith("LOCAL_PHOTO:") }
                        if (photoTag != null) {
                            val uriString = photoTag.name.substringAfter("LOCAL_PHOTO:")
                            AsyncImage(
                                model = Uri.parse(uriString),
                                contentDescription = "Captured Item",
                                modifier = Modifier.fillMaxWidth().height(300.dp).padding(bottom = 16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Text(text = state.chat.mergedTitle ?: "Transcription", style = MaterialTheme.typography.headlineSmall)
                        Text(text = state.chat.timestamp, style = MaterialTheme.typography.labelMedium)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(text = state.chat.text, style = MaterialTheme.typography.bodyLarge)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(text = "AI Actions", style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Button(
                                onClick = { viewModel.enhance() },
                                modifier = Modifier.weight(1f).padding(4.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Enhance")
                            }
                            Button(
                                onClick = { viewModel.rewrite("organized") },
                                modifier = Modifier.weight(1f).padding(4.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Organize")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Google Photos", style = MaterialTheme.typography.titleMedium)
                        
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
                        Text(text = "Tags", style = MaterialTheme.typography.titleMedium)
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            // Filter out the special internal photo tag from the visible list
                            state.chat.tags.filter { !it.name.startsWith("LOCAL_PHOTO:") }.forEach { tag ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(tag.name) },
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }

                        if (suggestedTags.isNotEmpty()) {
                            Text(text = "AI Suggested Tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                suggestedTags.forEach { tag ->
                                    AssistChip(
                                        onClick = { viewModel.addTag(tag) },
                                        label = { Text(tag) },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = { viewModel.suggestTags() }) {
                                Text("Suggest AI Tags")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier) { content() }
}
