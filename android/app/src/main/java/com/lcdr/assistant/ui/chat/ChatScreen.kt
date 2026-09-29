package com.lcdr.assistant.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lcdr.assistant.domain.model.Message
import com.lcdr.assistant.domain.model.Role
import com.lcdr.assistant.ui.chat.components.MessageBubble
import com.lcdr.assistant.ui.chat.components.MessageComposer
import com.lcdr.assistant.ui.chat.components.ToolCallChip
import com.lcdr.assistant.ui.theme.OfficerGold
import com.lcdr.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToVoice: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        val itemCount = uiState.messages.size +
                (if (uiState.streamingText.isNotEmpty()) 1 else 0) +
                uiState.pendingTools.size
        if (itemCount > 0) {
            scope.launch { listState.animateScrollToItem(itemCount - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LCDR", color = OfficerGold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Personal Device Assistant",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Delete, "Clear conversation", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                // Streaming text bubble
                if (uiState.streamingText.isNotEmpty()) {
                    item(key = "streaming") {
                        MessageBubble(
                            message = Message(
                                conversationId = "main",
                                role = Role.ASSISTANT,
                                content = uiState.streamingText
                            ),
                            isStreaming = true
                        )
                    }
                }

                // Tool call chips
                if (uiState.pendingTools.isNotEmpty()) {
                    items(uiState.pendingTools, key = { "tool_${it.name}" }) { tool ->
                        ToolCallChip(activity = tool)
                    }
                }

                // Typing indicator
                if (uiState.isStreaming && uiState.streamingText.isEmpty() && uiState.pendingTools.isEmpty()) {
                    item(key = "typing") {
                        TypingIndicator()
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = viewModel::dismissError, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Composer
            MessageComposer(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onVoice = onNavigateToVoice,
                isLoading = uiState.isStreaming
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Conversation") },
            text = { Text("This deletes all messages. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearConfirm = false
                }) { Text("CLEAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val alpha by animateFloatAsState(
                targetValue = if (i % 2 == 0) 0.8f else 0.3f,
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
        Text("LCDR is thinking…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
