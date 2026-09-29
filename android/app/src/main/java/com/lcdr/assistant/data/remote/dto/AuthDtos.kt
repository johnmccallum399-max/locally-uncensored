package com.lcdr.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String
)
