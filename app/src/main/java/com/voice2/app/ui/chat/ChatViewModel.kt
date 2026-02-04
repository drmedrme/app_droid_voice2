package com.voice2.app.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.voice2.app.data.api.TagFacet
import com.voice2.app.data.api.Transcription
import com.voice2.app.data.audio.AudioRecorder
import com.voice2.app.data.preferences.SettingsPreferences
import com.voice2.app.data.preferences.TranscriptionMode
import com.voice2.app.data.repository.Voice2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: Voice2Repository,
    private val audioRecorder: AudioRecorder,
    private val settingsPreferences: SettingsPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestedTags = MutableStateFlow<List<String>>(emptyList())
    val suggestedTags: StateFlow<List<String>> = _suggestedTags.asStateFlow()

    private val _isSearchListening = MutableStateFlow(false)
    val isSearchListening: StateFlow<Boolean> = _isSearchListening.asStateFlow()

    // Advanced search filters
    private val _searchFiltersExpanded = MutableStateFlow(false)
    val searchFiltersExpanded: StateFlow<Boolean> = _searchFiltersExpanded.asStateFlow()

    private val _fuzzyEnabled = MutableStateFlow(false)
    val fuzzyEnabled: StateFlow<Boolean> = _fuzzyEnabled.asStateFlow()

    private val _boostRecent = MutableStateFlow(false)
    val boostRecent: StateFlow<Boolean> = _boostRecent.asStateFlow()

    private val _dateFrom = MutableStateFlow<String?>(null)
    val dateFrom: StateFlow<String?> = _dateFrom.asStateFlow()

    private val _dateTo = MutableStateFlow<String?>(null)
    val dateTo: StateFlow<String?> = _dateTo.asStateFlow()

    private val _selectedTags = MutableStateFlow<List<String>>(emptyList())
    val selectedTags: StateFlow<List<String>> = _selectedTags.asStateFlow()

    private val _tagFacets = MutableStateFlow<List<TagFacet>>(emptyList())
    val tagFacets: StateFlow<List<TagFacet>> = _tagFacets.asStateFlow()

    private var audioFile: File? = null
    private var lastPhotoUri: Uri? = null
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val searchSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var searchJob: Job? = null

    init {
        loadChats()
        setupSpeechRecognizer()
        setupSearchSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    uploadText(matches[0])
                }
                _isRecording.value = false
            }
            override fun onError(error: Int) {
                Log.e("Voice2", "Speech recognizer error code: $error")
                _isRecording.value = false
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

    private fun setupSearchSpeechRecognizer() {
        searchSpeechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onSearchQueryChange(matches[0])
                }
                _isSearchListening.value = false
            }
            override fun onError(error: Int) {
                Log.e("Voice2", "Search speech recognizer error code: $error")
                _isSearchListening.value = false
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

    fun startVoiceSearch() {
        viewModelScope.launch {
            try {
                val pauseMs = settingsPreferences.speechPauseDuration.first()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                }
                searchSpeechRecognizer.startListening(intent)
                _isSearchListening.value = true
            } catch (e: Exception) {
                Log.e("Voice2", "Voice search start failed: ${e.message}")
                _isSearchListening.value = false
            }
        }
    }

    fun stopVoiceSearch() {
        searchSpeechRecognizer.stopListening()
        _isSearchListening.value = false
    }

    fun setLastPhotoUri(uri: Uri?) {
        lastPhotoUri = uri
    }

    fun loadChats() {
        viewModelScope.launch {
            // Only show full-screen spinner on first load; pull-to-refresh keeps the list visible
            if (_uiState.value !is ChatUiState.Success) {
                _uiState.value = ChatUiState.Loading
            }
            _isRefreshing.value = true
            repository.getChats()
                .onSuccess { chats ->
                    _uiState.value = ChatUiState.Success(chats.filter { !it.isMerged })
                }
                .onFailure { e -> _uiState.value = ChatUiState.Error(e.message ?: "Unknown error") }
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            if (query.isBlank() && !hasActiveFilters()) loadChats() else performSearch(query)
        }
    }

    private fun hasActiveFilters(): Boolean {
        return _fuzzyEnabled.value || _boostRecent.value ||
                _dateFrom.value != null || _dateTo.value != null ||
                _selectedTags.value.isNotEmpty()
    }

    private suspend fun performSearch(query: String) {
        if (hasActiveFilters() || query.isNotBlank()) {
            val searchText = query.ifBlank { "*" }
            _uiState.value = ChatUiState.Loading
            repository.advancedSearch(
                query = searchText,
                fuzzy = _fuzzyEnabled.value,
                boostRecent = _boostRecent.value,
                dateFrom = _dateFrom.value,
                dateTo = _dateTo.value,
                tags = _selectedTags.value.ifEmpty { null }
            ).onSuccess { response ->
                _uiState.value = ChatUiState.Success(response.items)
                _tagFacets.value = response.tagFacets
            }.onFailure { e ->
                // Fallback to basic search if advanced fails
                if (query.isNotBlank()) {
                    repository.searchChats(query)
                        .onSuccess { chats -> _uiState.value = ChatUiState.Success(chats) }
                        .onFailure { e2 -> _uiState.value = ChatUiState.Error(e2.message ?: "Search failed") }
                } else {
                    _uiState.value = ChatUiState.Error(e.message ?: "Search failed")
                }
            }
        } else {
            _uiState.value = ChatUiState.Loading
            repository.searchChats(query)
                .onSuccess { chats -> _uiState.value = ChatUiState.Success(chats) }
                .onFailure { e -> _uiState.value = ChatUiState.Error(e.message ?: "Search failed") }
        }
    }

    // Filter setters

    fun toggleSearchFilters() {
        _searchFiltersExpanded.value = !_searchFiltersExpanded.value
    }

    fun setFuzzyEnabled(enabled: Boolean) {
        _fuzzyEnabled.value = enabled
        triggerSearch()
    }

    fun setBoostRecent(enabled: Boolean) {
        _boostRecent.value = enabled
        triggerSearch()
    }

    fun setDateFrom(date: String?) {
        _dateFrom.value = date
        triggerSearch()
    }

    fun setDateTo(date: String?) {
        _dateTo.value = date
        triggerSearch()
    }

    fun toggleTagFilter(tagName: String) {
        val current = _selectedTags.value.toMutableList()
        if (current.contains(tagName)) {
            current.remove(tagName)
        } else {
            current.add(tagName)
        }
        _selectedTags.value = current
        triggerSearch()
    }

    fun clearFilters() {
        _fuzzyEnabled.value = false
        _boostRecent.value = false
        _dateFrom.value = null
        _dateTo.value = null
        _selectedTags.value = emptyList()
        _tagFacets.value = emptyList()
        if (_searchQuery.value.isBlank()) loadChats() else triggerSearch()
    }

    private fun triggerSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch(_searchQuery.value)
        }
    }

    fun toggleRecording() {
        viewModelScope.launch {
            val mode = settingsPreferences.transcriptionMode.first()
            if (_isRecording.value) {
                if (mode == TranscriptionMode.HIGH_QUALITY) stopAudioRecording()
                else {
                    speechRecognizer.stopListening()
                    _isRecording.value = false
                }
            } else {
                delay(500)
                if (mode == TranscriptionMode.HIGH_QUALITY) startAudioRecording()
                else startSpeechRecognition()
            }
        }
    }

    private fun startAudioRecording() {
        val file = File(context.cacheDir, "recording.m4a")
        audioFile = file
        try {
            audioRecorder.start(file)
            _isRecording.value = true
        } catch (e: Exception) {
            _uiState.value = ChatUiState.Error("Recording failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        try {
            audioRecorder.stop()
            _isRecording.value = false
            audioFile?.let { uploadAudio(it) }
        } catch (e: Exception) {
            _uiState.value = ChatUiState.Error("Stop failed: ${e.message}")
        }
    }

    private fun startSpeechRecognition() {
        viewModelScope.launch {
            try {
                val pauseMs = settingsPreferences.speechPauseDuration.first()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                }
                speechRecognizer.startListening(intent)
                _isRecording.value = true
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Speech start failed: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()
            if (location != null) Pair(location.latitude, location.longitude) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun uploadAudio(file: File) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            val location = getCurrentLocation()
            repository.uploadAudio(file, location?.first, location?.second)
                .onSuccess { transcription ->
                    lastPhotoUri?.let { uri ->
                        val internalFile = copyPhotoToInternal(uri)
                        if (internalFile != null) {
                            repository.addTag(transcription.id, "LOCAL_PHOTO:${Uri.fromFile(internalFile)}")
                        } else {
                            Log.w("Voice2", "Photo copy failed; skipping LOCAL_PHOTO tag for ephemeral URI")
                        }
                    }
                    lastPhotoUri = null
                    loadChats()
                }
                .onFailure { e -> _uiState.value = ChatUiState.Error("Upload failed: ${e.message}") }
        }
    }

    private fun uploadText(text: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            val location = getCurrentLocation()
            repository.transcribeText(text, location?.first, location?.second)
                .onSuccess { transcription ->
                    lastPhotoUri?.let { uri ->
                        val internalFile = copyPhotoToInternal(uri)
                        if (internalFile != null) {
                            repository.addTag(transcription.id, "LOCAL_PHOTO:${Uri.fromFile(internalFile)}")
                        } else {
                            Log.w("Voice2", "Photo copy failed; skipping LOCAL_PHOTO tag for ephemeral URI")
                        }
                    }
                    lastPhotoUri = null
                    loadChats()
                }
                .onFailure { e -> _uiState.value = ChatUiState.Error("Text upload failed: ${e.message}") }
        }
    }

    fun suggestTags(chatId: UUID) {
        viewModelScope.launch {
            repository.suggestTags(chatId).onSuccess { response ->
                val names = response.existingTags.map { it.name } + response.proposedTags
                _suggestedTags.value = names.distinct()
            }
        }
    }

    fun addTag(chatId: UUID, name: String) {
        viewModelScope.launch {
            repository.addTag(chatId, name).onSuccess { loadChats() }
        }
    }

    private fun copyPhotoToInternal(uri: Uri): File? {
        return try {
            val photosDir = File(context.filesDir, "photos").also { it.mkdirs() }
            val destFile = File(photosDir, "photo_${System.currentTimeMillis()}.jpg")
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                Log.w("Voice2", "ContentResolver returned null stream for $uri")
                return null
            }
            stream.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (destFile.length() > 0) destFile else {
                destFile.delete()
                Log.w("Voice2", "Copied photo was 0 bytes, deleted empty file")
                null
            }
        } catch (e: Exception) {
            Log.e("Voice2", "Failed to copy photo to internal storage", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
        searchSpeechRecognizer.destroy()
    }
}

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(val chats: List<Transcription>) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
