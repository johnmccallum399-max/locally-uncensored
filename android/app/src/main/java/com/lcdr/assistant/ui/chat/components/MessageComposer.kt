package com.lcdr.assistant.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lcdr.assistant.ui.theme.OfficerGold
import com.lcdr.assistant.ui.theme.TacticalBlue

@Composable
fun MessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Command…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isLoading) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TacticalBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Voice FAB
            FilledIconButton(
                onClick = onVoice,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = OfficerGold)
            ) {
                Icon(Icons.Default.Mic, "Voice", tint = MaterialTheme.colorScheme.background)
            }

            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = TacticalBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
}
