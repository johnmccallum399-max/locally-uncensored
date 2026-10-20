package com.lcdr.assistant.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.97f)
                tts?.setPitch(1.0f)
                pickNaturalVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == LAST_UTTERANCE) {
                            _uiState.update { it.copy(state = VoiceState.IDLE) }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _uiState.update { it.copy(state = VoiceState.IDLE) }
                    }
                })
            }
        }
    }

    /** Prefer the highest-quality installed voice for the current language (neural/network voices over legacy ones). */
    private fun pickNaturalVoice() {
        val engine = tts ?: return
        val lang = Locale.getDefault().language
        val best = engine.voices
            ?.filter { it.locale.language == lang && !it.features.contains("notInstalled") }
            ?.maxByOrNull { it.quality * 2 + if (it.isNetworkConnectionRequired) 1 else 0 }
        if (best != null) engine.voice = best
    }

    /** Strip markdown/code/URLs so the engine doesn't read symbols aloud. */
    private fun cleanForSpeech(text: String): String =
        text
            .replace(Regex("```[\\s\\S]*?```"), " code omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), "link")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
            .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
            .replace(Regex("[*_~>|]+"), "")
            .replace(Regex("(?<![.!?:])\\s*\\n+\\s*"), ". ").replace(Regex("\\s*\\n+\\s*"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    /** Split into sentences so the engine uses natural phrasing and starts speaking sooner. */
    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.update { it.copy(error = "Speech recognition not available") }
            return
        }

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
                    if (text != null) _uiState.update { it.copy(transcript = text) }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        _uiState.update { it.copy(state = VoiceState.PROCESSING, transcript = text) }
                        sendToLcdr(text)
                    } else {
                        _uiState.update { it.copy(state = VoiceState.IDLE) }
                    }
                }
                override fun onError(error: Int) {
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

    private fun sendToLcdr(text: String) {
        viewModelScope.launch {
            val response = StringBuilder()
            chatRepository.sendMessage(text)
                .collect { event ->
                    when (event) {
                        is ChatEvent.TextDelta -> response.append(event.text)
                        is ChatEvent.Done -> {
                            val fullResponse = response.toString()
                            _uiState.update { it.copy(state = VoiceState.SPEAKING, response = fullResponse) }
                            speak(fullResponse)
                        }
                        is ChatEvent.Error -> _uiState.update {
                            it.copy(state = VoiceState.IDLE, error = event.message)
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun speak(text: String) {
        val sentences = splitSentences(cleanForSpeech(text))
        if (!ttsReady || sentences.isEmpty()) {
            _uiState.update { it.copy(state = VoiceState.IDLE) }
            return
        }
        sentences.forEachIndexed { i, sentence ->
            val last = i == sentences.lastIndex
            tts?.speak(
                sentence,
                if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                if (last) LAST_UTTERANCE else "lcdr_$i"
            )
            if (!last) tts?.playSilentUtterance(120, TextToSpeech.QUEUE_ADD, "pause_$i")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        tts?.stop()
        _uiState.update { it.copy(state = VoiceState.IDLE) }
    }

    override fun onCleared() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onCleared()
    }

    private companion object {
        const val LAST_UTTERANCE = "lcdr_last"
    }
}
