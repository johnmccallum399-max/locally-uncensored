package com.lcdr.assistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdr.assistant.data.repository.MemoryRepository
import com.lcdr.assistant.domain.model.MemoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<MemoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val newKey: String = "",
    val newValue: String = "",
    val error: String? = null
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            memoryRepository.observeMemories().collect { memories ->
                _uiState.update { it.copy(memories = memories) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            memoryRepository.refreshFromRemote()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onKeyChange(v: String) = _uiState.update { it.copy(newKey = v) }
    fun onValueChange(v: String) = _uiState.update { it.copy(newValue = v) }

    fun save() {
        val key = _uiState.value.newKey.trim()
        val value = _uiState.value.newValue.trim()
        if (key.isBlank() || value.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = memoryRepository.remember(key, value)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    newKey = if (result.isSuccess) "" else it.newKey,
                    newValue = if (result.isSuccess) "" else it.newValue,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            memoryRepository.delete(id)
        }
    }
}
