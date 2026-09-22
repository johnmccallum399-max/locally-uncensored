package com.lcdr.assistant.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.remote.ApiService
import com.lcdr.assistant.data.remote.dto.HubAgentDto
import com.lcdr.assistant.data.remote.dto.HubSessionDto
import com.lcdr.assistant.data.remote.dto.HubSessionRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HubUiState(
    val agents: List<HubAgentDto> = emptyList(),
    val sessions: List<HubSessionDto> = emptyList(),
    val isLoading: Boolean = false,
    val goal: String = "",
    val selectedAgentIds: Set<String> = emptySet(),
    val error: String? = null
)

@HiltViewModel
class HubViewModel @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    init { loadAgents() }

    fun loadAgents() {
        val token = securePrefs.getToken() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = apiService.getHubAgents("Bearer $token")
                if (response.isSuccessful) {
                    _uiState.update { it.copy(agents = response.body()?.agents ?: emptyList(), isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onGoalChange(text: String) = _uiState.update { it.copy(goal = text) }

    fun toggleAgent(id: String) {
        _uiState.update {
            val ids = it.selectedAgentIds.toMutableSet()
            if (id in ids) ids.remove(id) else ids.add(id)
            it.copy(selectedAgentIds = ids)
        }
    }

    fun createSession() {
        val state = _uiState.value
        if (state.goal.isBlank()) return
        val token = securePrefs.getToken() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val request = HubSessionRequest(
                    goal = state.goal,
                    agentIds = state.selectedAgentIds.toList()
                )
                val response = apiService.createHubSession("Bearer $token", request)
                if (response.isSuccessful) {
                    val session = response.body()
                    if (session != null) {
                        _uiState.update { it.copy(isLoading = false, sessions = it.sessions + session, goal = "") }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Session creation failed: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
