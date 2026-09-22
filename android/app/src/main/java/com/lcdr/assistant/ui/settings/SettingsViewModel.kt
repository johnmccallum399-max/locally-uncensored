package com.lcdr.assistant.ui.settings

import androidx.lifecycle.ViewModel
import com.lcdr.assistant.data.local.dao.ActionLogDao
import com.lcdr.assistant.data.local.entity.ActionLogEntity
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.repository.AuthRepository
import com.lcdr.assistant.tools.ALL_TOOL_DEFINITIONS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val personaName: String = "LCDR",
    val systemPromptOverride: String = "",
    val voiceMode: String = "pipeline",
    val biometricEnabled: Boolean = false,
    val briefingTime: String = "07:00",
    val toolStates: Map<String, Boolean> = emptyMap(),
    val contextStates: Map<String, Boolean> = emptyMap(),
    val actionLog: List<ActionLogEntity> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val authRepository: AuthRepository,
    private val actionLogDao: ActionLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            personaName = securePrefs.getPersonaName(),
            systemPromptOverride = securePrefs.getSystemPromptOverride() ?: "",
            voiceMode = securePrefs.getVoiceMode(),
            biometricEnabled = securePrefs.isBiometricEnabled(),
            briefingTime = securePrefs.getBriefingTime(),
            toolStates = ALL_TOOL_DEFINITIONS.associate { it.name to securePrefs.isToolEnabled(it.name) },
            contextStates = mapOf(
                "battery" to securePrefs.isContextEnabled("battery"),
                "location" to securePrefs.isContextEnabled("location"),
                "calendar" to securePrefs.isContextEnabled("calendar")
            )
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            actionLogDao.getAll().collect { log ->
                _uiState.update { it.copy(actionLog = log) }
            }
        }
    }

    fun setPersonaName(name: String) {
        securePrefs.saveUserName(name)
        securePrefs.setPersonaName(name)
        _uiState.update { it.copy(personaName = name) }
    }

    fun setSystemPromptOverride(prompt: String) {
        val value = prompt.takeIf { it.isNotBlank() }
        securePrefs.setSystemPromptOverride(value)
        _uiState.update { it.copy(systemPromptOverride = prompt) }
    }

    fun setVoiceMode(mode: String) {
        securePrefs.setVoiceMode(mode)
        _uiState.update { it.copy(voiceMode = mode) }
    }

    fun setBiometric(enabled: Boolean) {
        securePrefs.setBiometricEnabled(enabled)
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun setBriefingTime(time: String) {
        securePrefs.setBriefingTime(time)
        _uiState.update { it.copy(briefingTime = time) }
    }

    fun setToolEnabled(name: String, enabled: Boolean) {
        securePrefs.setToolEnabled(name, enabled)
        _uiState.update { it.copy(toolStates = it.toolStates + (name to enabled)) }
    }

    fun setContextEnabled(key: String, enabled: Boolean) {
        securePrefs.setContextEnabled(key, enabled)
        _uiState.update { it.copy(contextStates = it.contextStates + (key to enabled)) }
    }

    fun logout(onComplete: () -> Unit) {
        authRepository.logout()
        onComplete()
    }
}
