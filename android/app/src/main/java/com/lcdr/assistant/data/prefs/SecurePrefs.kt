package com.lcdr.assistant.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "lcdr_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun clearToken() = prefs.edit().remove(KEY_TOKEN).apply()

    fun saveUserId(id: String) = prefs.edit().putString(KEY_USER_ID, id).apply()
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveUserName(name: String) = prefs.edit().putString(KEY_USER_NAME, name).apply()
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun setBiometricEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBriefingTime(hourMinute: String) =
        prefs.edit().putString(KEY_BRIEFING_TIME, hourMinute).apply()
    fun getBriefingTime(): String = prefs.getString(KEY_BRIEFING_TIME, "07:00") ?: "07:00"

    fun setVoiceMode(mode: String) = prefs.edit().putString(KEY_VOICE_MODE, mode).apply()
    fun getVoiceMode(): String = prefs.getString(KEY_VOICE_MODE, "pipeline") ?: "pipeline"

    fun setPersonaName(name: String) = prefs.edit().putString(KEY_PERSONA_NAME, name).apply()
    fun getPersonaName(): String = prefs.getString(KEY_PERSONA_NAME, "LCDR") ?: "LCDR"

    fun setSystemPromptOverride(prompt: String?) =
        prefs.edit().putString(KEY_SYSTEM_PROMPT_OVERRIDE, prompt).apply()
    fun getSystemPromptOverride(): String? = prefs.getString(KEY_SYSTEM_PROMPT_OVERRIDE, null)

    fun setToolEnabled(toolName: String, enabled: Boolean) =
        prefs.edit().putBoolean("tool_$toolName", enabled).apply()
    fun isToolEnabled(toolName: String): Boolean =
        prefs.getBoolean("tool_$toolName", true)

    fun setContextEnabled(key: String, enabled: Boolean) =
        prefs.edit().putBoolean("ctx_$key", enabled).apply()
    fun isContextEnabled(key: String): Boolean =
        prefs.getBoolean("ctx_$key", true)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_BRIEFING_TIME = "briefing_time"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_PERSONA_NAME = "persona_name"
        private const val KEY_SYSTEM_PROMPT_OVERRIDE = "system_prompt_override"
    }
}
