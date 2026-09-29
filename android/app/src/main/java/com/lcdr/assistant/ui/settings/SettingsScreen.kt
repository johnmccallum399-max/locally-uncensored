package com.lcdr.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lcdr.assistant.tools.ALL_TOOL_DEFINITIONS
import com.lcdr.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var personaNameEdit by remember(uiState.personaName) { mutableStateOf(uiState.personaName) }
    var promptEdit by remember(uiState.systemPromptOverride) { mutableStateOf(uiState.systemPromptOverride) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding)
        ) {
            // Persona
            item {
                SectionHeader("Persona")
                OutlinedTextField(
                    value = personaNameEdit,
                    onValueChange = { personaNameEdit = it },
                    label = { Text("Assistant Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setPersonaName(personaNameEdit) }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = promptEdit,
                    onValueChange = { promptEdit = it },
                    label = { Text("System prompt override (blank = default)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 6,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setSystemPromptOverride(promptEdit) }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                )
            }

            // Voice mode
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Voice Mode")
                listOf("pipeline" to "On-device (STT/TTS)", "realtime" to "Realtime WebRTC").forEach { (mode, label) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = TextPrimary)
                        RadioButton(
                            selected = uiState.voiceMode == mode,
                            onClick = { viewModel.setVoiceMode(mode) }
                        )
                    }
                }
            }

            // Security
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Security")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Biometric lock on open", color = TextPrimary)
                    Switch(checked = uiState.biometricEnabled, onCheckedChange = viewModel::setBiometric)
                }
            }

            // Daily briefing
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Daily Briefing")
                OutlinedTextField(
                    value = uiState.briefingTime,
                    onValueChange = viewModel::setBriefingTime,
                    label = { Text("Briefing time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Tool permissions
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Tool Permissions")
            }
            items(ALL_TOOL_DEFINITIONS, key = { it.name }) { tool ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = uiState.toolStates[tool.name] ?: true,
                        onCheckedChange = { viewModel.setToolEnabled(tool.name, it) }
                    )
                }
            }

            // Device context
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Device Context Injected to Prompt")
                listOf("battery" to "Battery level", "location" to "Location", "calendar" to "Today's events").forEach { (key, label) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = TextPrimary)
                        Switch(
                            checked = uiState.contextStates[key] ?: true,
                            onCheckedChange = { viewModel.setContextEnabled(key, it) }
                        )
                    }
                }
            }

            // Action history (last 5)
            if (uiState.actionLog.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Recent Action History")
                }
                items(uiState.actionLog.take(5), key = { it.id }) { entry ->
                    Text(
                        text = "• ${entry.toolName}: ${entry.result.take(60)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.isError) DangerRed else TextSecondary
                    )
                }
            }

            // Logout
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("LOG OUT") }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log Out") },
            text = { Text("This clears your session. You'll need to re-authenticate.") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(onLogout) }) {
                    Text("LOG OUT", color = DangerRed)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = OfficerGold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
