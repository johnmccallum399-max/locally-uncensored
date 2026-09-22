package com.lcdr.assistant.data.repository

import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.remote.ApiService
import com.lcdr.assistant.data.remote.dto.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) {
    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext AuthResult.Error("Empty response")
                securePrefs.saveToken(body.token)
                securePrefs.saveUserId(body.user.id)
                securePrefs.saveUserName(body.user.name)
                AuthResult.Success
            } else {
                AuthResult.Error("Login failed: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    fun logout() {
        securePrefs.clearToken()
    }

    fun isLoggedIn() = securePrefs.getToken() != null

    fun getToken() = securePrefs.getToken()

    fun getUserName() = securePrefs.getUserName() ?: "Commander"
}
