package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextMuted

@Composable
fun StatusBadge(
    isOnline: Boolean,
    latencyMs: Long,
    modifier: Modifier = Modifier
) {
    // Slow breathing halo on the live dot — a quiet sign of life
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOnline) 1.35f else 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulseScale",
    )

    val badgeColor = when {
        !isOnline -> TerminalRed
        latencyMs in 1..90 -> TerminalGreen
        latencyMs > 90 -> TerminalAmber
        else -> TerminalGreen
    }
    val text = if (isOnline) {
        if (latencyMs > 0) "${latencyMs}ms" else "Online"
    } else {
        "Offline"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(badgeColor.copy(alpha = 0.10f))
            .border(1.dp, badgeColor.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.30f))
                )
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = badgeColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
        )
    }
}

@Composable
fun ServiceTag(
    name: String,
    isActive: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) accentColor.copy(alpha = 0.10f)
                else DarkSurfaceVariant.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (isActive) accentColor.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = if (isActive) accentColor else TextMuted
        )
    }
}
