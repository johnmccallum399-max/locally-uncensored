package com.lcdr.assistant.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun DeviceScreen(
    onNavigateToChat: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Dashboard") },
                actions = {
                    IconButton(onClick = viewModel::loadQuickData) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Battery card
            Card(colors = CardDefaults.cardColors(containerColor = NavyContainer)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (uiState.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        null,
                        tint = if (uiState.batteryPct < 20) DangerRed else SuccessGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text("${uiState.batteryPct}%", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(if (uiState.isCharging) "Charging" else "Discharging", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // SMS card
            InfoCard(
                title = "Recent Messages",
                icon = Icons.Default.Message,
                content = uiState.recentSms.ifEmpty { "No messages" },
                isLoading = uiState.isLoading
            )

            // Calendar card
            InfoCard(
                title = "Today's Calendar",
                icon = Icons.Default.CalendarToday,
                content = uiState.todayEvents.ifEmpty { "No events today" },
                isLoading = uiState.isLoading
            )

            // Quick actions
            Card(colors = CardDefaults.cardColors(containerColor = NavyContainer)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleSmall, color = OfficerGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionButton(
                            label = "Ask LCDR",
                            icon = Icons.Default.Chat,
                            onClick = onNavigateToChat,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionButton(
                            label = "Refresh",
                            icon = Icons.Default.Refresh,
                            onClick = viewModel::loadQuickData,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: String,
    isLoading: Boolean
) {
    Card(colors = CardDefaults.cardColors(containerColor = NavyContainer)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = TacticalBlue, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = OfficerGold)
            }
            Spacer(Modifier.height(8.dp))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(content, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
