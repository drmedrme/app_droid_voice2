package com.voice2.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voice2.app.data.preferences.TranscriptionMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val transcriptionMode by viewModel.transcriptionMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Transcription Mode", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Choose between fast on-device recognition or high-quality backend processing.",
            style = MaterialTheme.typography.bodySmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fast (On-Device)")
            RadioButton(
                selected = transcriptionMode == TranscriptionMode.FAST,
                onClick = { viewModel.setTranscriptionMode(TranscriptionMode.FAST) }
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("High Quality (OpenAI)")
            RadioButton(
                selected = transcriptionMode == TranscriptionMode.HIGH_QUALITY,
                onClick = { viewModel.setTranscriptionMode(TranscriptionMode.HIGH_QUALITY) }
            )
        }
    }
}
