package com.lcdr.assistant.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lcdr.assistant.domain.model.Message
import com.lcdr.assistant.domain.model.Role
import com.lcdr.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean = false
) {
    val isUser = message.role == Role.USER
    val bubbleShape = if (isUser) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(
                    if (isUser) TacticalBlueDim
                    else NavyContainer
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!isUser) {
                Text(
                    text = "LCDR",
                    style = MaterialTheme.typography.labelSmall,
                    color = OfficerGold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = message.content + if (isStreaming) "▌" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontFamily = if (message.role == Role.ASSISTANT) null else null
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
