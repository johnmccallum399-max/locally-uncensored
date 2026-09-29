package com.lcdr.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdr.assistant.data.repository.ChatEvent
import com.lcdr.assistant.data.repository.ChatRepository
import com.lcdr.assistant.domain.model.Message
import com.lcdr.assistant.domain.model.Role
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolActivity(
    val name: String,
    val state: ToolState,
    val result: String? = null
)

enum class ToolState { STARTED, EXECUTING, DONE, ERROR }

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val pendingTools: List<ToolActivity> = emptyList(),
    val error: String? = null,
    val inputText: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    init {
        observeMessages()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isStreaming) return

        _uiState.update { it.copy(inputText = "", streamingText = "", isStreaming = true, error = null, pendingTools = emptyList()) }

        streamingJob = viewModelScope.launch {
            chatRepository.sendMessage(text)
                .collect { event ->
                    when (event) {
                        is ChatEvent.TextDelta -> _uiState.update {
                            it.copy(streamingText = it.streamingText + event.text)
                        }
                        is ChatEvent.ToolCallStarted -> _uiState.update {
                            it.copy(pendingTools = it.pendingTools + ToolActivity(event.name, ToolState.STARTED))
                        }
                        is ChatEvent.ToolExecuting -> _uiState.update {
                            it.copy(pendingTools = it.pendingTools.map { t ->
                                if (t.name == event.name) t.copy(state = ToolState.EXECUTING) else t
                            })
                        }
                        is ChatEvent.ToolCompleted -> _uiState.update {
                            val state = if (event.result.isError) ToolState.ERROR else ToolState.DONE
                            it.copy(pendingTools = it.pendingTools.map { t ->
                                if (t.name == event.name) t.copy(state = state, result = event.result.content)
                                else t
                            })
                        }
                        is ChatEvent.Error -> _uiState.update {
                            it.copy(isStreaming = false, error = event.message)
                        }
                        is ChatEvent.Done -> _uiState.update {
                            it.copy(isStreaming = false, streamingText = "")
                        }
                    }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearHistory() }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        streamingJob?.cancel()
        super.onCleared()
    }
}
