package com.lcdr.assistant

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.ui.navigation.NavGraph
import com.lcdr.assistant.ui.theme.LcdrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasToken = securePrefs.getToken() != null
        val biometricEnabled = securePrefs.isBiometricEnabled()

        var biometricPassed by mutableStateOf(!hasToken || !biometricEnabled)

        if (hasToken && biometricEnabled) {
            showBiometric(
                onSuccess = { biometricPassed = true },
                onFail = { finish() }
            )
        }

        splashScreen.setKeepOnScreenCondition { false }

        setContent {
            LcdrTheme {
                val passed by rememberUpdatedState(biometricPassed)
                if (passed) {
                    NavGraph(startedWithToken = hasToken)
                }
            }
        }
    }

    private fun showBiometric(onSuccess: () -> Unit, onFail: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricManager = BiometricManager.from(this)

        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationFailed() {}
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    onFail()
                }
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LCDR — Identity Verification")
            .setSubtitle("Confirm your identity to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
