package com.lcdr.assistant.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdr.assistant.data.repository.ChatEvent
import com.lcdr.assistant.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING }

data class VoiceUiState(
    val state: VoiceState = VoiceState.IDLE,
    val transcript: String = "",
    val response: String = "",
    val error: String? = null
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsError = "Speech engine is starting; try again shortly"
    private var responseJob: Job? = null
    private var cleared = false
    private val turn = VoiceTurn()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playback = SpeechPlayback(object : SpeechOutput {
        override fun stop() { tts?.stop() }
        override fun speak(text: String, id: String, flush: Boolean): Boolean =
            tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null, id) == TextToSpeech.SUCCESS
        override fun pause(millis: Long, id: String): Boolean =
            tts?.playSilentUtterance(millis, TextToSpeech.QUEUE_ADD, id) == TextToSpeech.SUCCESS
    }) { error ->
        _uiState.update { it.copy(state = VoiceState.IDLE, error = error) }
    }

    init { initTts() }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            // Also handles an engine invoking onInit before the constructor assignment completes.
            mainHandler.post {
                if (!cleared) configureTts(status)
            }
        }
    }

    private fun configureTts(status: Int) {
        val engine = tts ?: return
        ttsReady = false
        ttsError = "Speech engine unavailable"
        if (status != TextToSpeech.SUCCESS) return
        val voices = engine.voices.orEmpty()
        val selected = selectSpeechVoice(voices.map {
            SpeechVoice(it.name, it.locale, it.quality, it.isNetworkConnectionRequired,
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
        }, Locale.getDefault())
        if (selected == null) {
            ttsError = "Install an offline text-to-speech voice for your device language, then reopen voice mode"
            return
        }
        val voice = voices.first { it.name == selected.name }
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS ||
            engine.setSpeechRate(0.97f) != TextToSpeech.SUCCESS ||
            engine.setPitch(1.0f) != TextToSpeech.SUCCESS) return
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                viewModelScope.launch { playback.done(utteranceId) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                viewModelScope.launch { playback.error(utteranceId) }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                viewModelScope.launch { playback.error(utteranceId) }
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                viewModelScope.launch { playback.stopped(utteranceId) }
            }
        }
        ttsReady = engine.setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS
    }

    fun startListening() {
        stopListening()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.update { it.copy(error = "Speech recognition not available") }
            return
        }

        val token = turn.begin()
        _uiState.update { it.copy(state = VoiceState.LISTENING, transcript = "", error = null) }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text != null && turn.isListening(token)) _uiState.update { it.copy(transcript = text) }
                }
                override fun onResults(results: Bundle?) {
                    if (!turn.acceptRecognition(token)) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        _uiState.update { it.copy(state = VoiceState.PROCESSING, transcript = text) }
                        sendToLcdr(text, token)
                    } else {
                        _uiState.update { it.copy(state = VoiceState.IDLE) }
                    }
                }
                override fun onError(error: Int) {
                    if (!turn.acceptRecognition(token)) return
                    _uiState.update { it.copy(state = VoiceState.IDLE, error = "STT error $error") }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            startListening(intent)
        }
    }

    private fun sendToLcdr(text: String, token: Long) {
        responseJob = viewModelScope.launch {
            val response = StringBuilder()
            try {
                // Consume a single terminal event; duplicate Done events must not replay the reply.
                val terminal = chatRepository.sendMessage(text).onEach { event ->
                    if (turn.isCurrent(token) && event is ChatEvent.TextDelta) response.append(event.text)
                }.first { it is ChatEvent.Done || it is ChatEvent.Error }
                if (!turn.isCurrent(token)) return@launch
                when (terminal) {
                    is ChatEvent.Done -> {
                        val fullResponse = response.toString()
                        _uiState.update { it.copy(state = VoiceState.SPEAKING, response = fullResponse) }
                        speak(fullResponse)
                    }
                    is ChatEvent.Error -> _uiState.update {
                        it.copy(state = VoiceState.IDLE, error = terminal.message)
                    }
                    else -> {}
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (turn.isCurrent(token)) _uiState.update {
                    it.copy(state = VoiceState.IDLE,
                        error = error.message ?: "Voice response failed")
                }
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            playback.cancel()
            _uiState.update { it.copy(state = VoiceState.IDLE, error = ttsError) }
            return
        }
        playback.speak(SpeechText.chunks(text, Locale.getDefault(), TextToSpeech.getMaxSpeechInputLength()))
    }

    fun stopListening() {
        turn.cancel()
        responseJob?.cancel()
        responseJob = null
        playback.cancel()
        // stopListening may still deliver a final recognition result; cancel and invalidate instead.
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _uiState.update { it.copy(state = VoiceState.IDLE) }
    }

    override fun onCleared() {
        cleared = true
        stopListening()
        mainHandler.removeCallbacksAndMessages(null)
        ttsReady = false
        tts?.shutdown()
        tts = null
        super.onCleared()
    }
}
