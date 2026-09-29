package com.lcdr.assistant.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.lcdr.assistant.ui.chat.ToolActivity
import com.lcdr.assistant.ui.chat.ToolState
import com.lcdr.assistant.ui.theme.*

@Composable
fun ToolCallChip(activity: ToolActivity) {
    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotate"
    )

    Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .background(NavyContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (activity.state) {
            ToolState.STARTED, ToolState.EXECUTING -> {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).rotate(rotation),
                    tint = OfficerGold
                )
            }
            ToolState.DONE -> {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = SuccessGreen)
            }
            ToolState.ERROR -> {
                Icon(Icons.Default.Error, null, modifier = Modifier.size(14.dp), tint = DangerRed)
            }
        }

        Column {
            Text(
                text = "⚙ ${activity.name}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            if (activity.result != null && (activity.state == ToolState.DONE || activity.state == ToolState.ERROR)) {
                val preview = activity.result.take(80) + if (activity.result.length > 80) "…" else ""
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activity.state == ToolState.ERROR) DangerRed else TextPrimary
                )
            }
        }
    }
}
