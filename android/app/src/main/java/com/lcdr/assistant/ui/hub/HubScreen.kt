package com.lcdr.assistant.ui.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lcdr.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(viewModel: HubViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orchestration Hub") },
                actions = {
                    IconButton(onClick = viewModel::loadAgents) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            // New session card
            item {
                Card(colors = CardDefaults.cardColors(containerColor = NavyContainer)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("New Session", style = MaterialTheme.typography.titleMedium, color = OfficerGold)
                        OutlinedTextField(
                            value = uiState.goal,
                            onValueChange = viewModel::onGoalChange,
                            label = { Text("Mission goal") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2, maxLines = 4
                        )

                        if (uiState.agents.isNotEmpty()) {
                            Text("Select agents:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            uiState.agents.forEach { agent ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = agent.id in uiState.selectedAgentIds,
                                        onCheckedChange = { viewModel.toggleAgent(agent.id) }
                                    )
                                    Column {
                                        Text(agent.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                        Text(agent.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = viewModel::createSession,
                            enabled = uiState.goal.isNotBlank() && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DEPLOY SESSION")
                        }
                    }
                }
            }

            // Sessions list
            if (uiState.sessions.isNotEmpty()) {
                item { Text("Active Sessions", style = MaterialTheme.typography.titleSmall, color = TextSecondary) }
                items(uiState.sessions, key = { it.id }) { session ->
                    Card(colors = CardDefaults.cardColors(containerColor = NavyContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(session.goal.take(50), color = TextPrimary, modifier = Modifier.weight(1f))
                                StatusChip(session.status)
                            }
                            Text("ID: ${session.id.take(8)}…", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                    }
                }
            }

            uiState.error?.let {
                item { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
            }

            if (uiState.isLoading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (color, label) = when (status.lowercase()) {
        "running" -> SuccessGreen to "RUNNING"
        "done", "completed" -> TacticalBlue to "DONE"
        "error", "failed" -> DangerRed to "FAILED"
        else -> TextSecondary to status.uppercase()
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(label, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}
