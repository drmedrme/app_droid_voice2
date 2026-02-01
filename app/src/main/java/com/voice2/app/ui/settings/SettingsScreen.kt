package com.voice2.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voice2.app.BuildConfig
import com.voice2.app.data.preferences.ThemeMode
import com.voice2.app.data.preferences.TranscriptionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val transcriptionMode by viewModel.transcriptionMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val testState by viewModel.testState.collectAsState()
    var baseUrlInput by remember(baseUrl) { mutableStateOf(baseUrl) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Transcription Mode
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Transcription Mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Choose between fast on-device recognition or high-quality backend processing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fast (On-Device)", modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = transcriptionMode == TranscriptionMode.FAST,
                        onClick = { viewModel.setTranscriptionMode(TranscriptionMode.FAST) }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("High Quality (OpenAI)", modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = transcriptionMode == TranscriptionMode.HIGH_QUALITY,
                        onClick = { viewModel.setTranscriptionMode(TranscriptionMode.HIGH_QUALITY) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.SYSTEM -> "System"
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server URL
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Server", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Backend URL for the Voice2 API. Takes effect immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                    placeholder = { Text("https://192.168.2.120:4712") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setBaseUrl(baseUrlInput) },
                        enabled = baseUrlInput.isNotBlank() && baseUrlInput != baseUrl
                    ) {
                        Text("Save")
                    }

                    OutlinedButton(
                        onClick = { viewModel.testConnection(baseUrlInput) },
                        enabled = baseUrlInput.isNotBlank() && testState !is TestConnectionState.Testing
                    ) {
                        if (testState is TestConnectionState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }
                }

                when (val state = testState) {
                    is TestConnectionState.Success -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Connected successfully",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is TestConnectionState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Version info
        Text(
            text = "Voice2 ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}
