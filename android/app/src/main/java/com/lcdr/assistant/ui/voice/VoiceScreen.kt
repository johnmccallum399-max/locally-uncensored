package com.lcdr.assistant.ui.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.lcdr.assistant.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceScreen(
    onDismiss: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        // Close button
        IconButton(
            onClick = {
                viewModel.stopListening()
                onDismiss()
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, "Close", tint = TextSecondary)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("LCDR VOICE", fontSize = 16.sp, color = OfficerGold, letterSpacing = 4.sp)

            // Mic button with pulse animation
            val buttonScale = if (uiState.state == VoiceState.LISTENING) pulseScale else 1f
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // Pulse ring
                if (uiState.state == VoiceState.LISTENING) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(buttonScale)
                            .background(TacticalBlue.copy(alpha = 0.3f), CircleShape)
                    )
                }

                FilledIconButton(
                    onClick = {
                        when {
                            !micPermission.status.isGranted -> micPermission.launchPermissionRequest()
                            uiState.state == VoiceState.IDLE -> viewModel.startListening()
                            else -> viewModel.stopListening()
                        }
                    },
                    modifier = Modifier.size(90.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = when (uiState.state) {
                            VoiceState.LISTENING -> DangerRed
                            VoiceState.SPEAKING -> SuccessGreen
                            else -> TacticalBlue
                        }
                    )
                ) {
                    Icon(
                        if (uiState.state == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
            }

            // Status label
            Text(
                text = when (uiState.state) {
                    VoiceState.IDLE -> "Tap to speak"
                    VoiceState.LISTENING -> "Listening…"
                    VoiceState.PROCESSING -> "Processing…"
                    VoiceState.SPEAKING -> "LCDR responding…"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            // Transcript
            if (uiState.transcript.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NavyContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = uiState.transcript,
                        modifier = Modifier.padding(12.dp),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Response
            if (uiState.response.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = TacticalBlueDim.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = uiState.response,
                        modifier = Modifier.padding(12.dp),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Error
            uiState.error?.let {
                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
