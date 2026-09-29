package com.lcdr.assistant.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdr.assistant.data.repository.AuthRepository
import com.lcdr.assistant.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(name = value, error = null) }
    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, error = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, error = null) }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank() || state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "All fields required")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Passwords do not match")
            return
        }
        if (state.password.length < 8) {
            _uiState.value = state.copy(error = "Password must be at least 8 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            when (val result = authRepository.register(state.email.trim(), state.password, state.name.trim())) {
                is AuthResult.Success -> onSuccess()
                is AuthResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.message
                )
            }
        }
    }
}
