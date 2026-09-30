package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SystemTelemetry
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.SparkBlueBright
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NodeTelemetryHeader(
    telemetry: SystemTelemetry,
    nodeName: String,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (telemetry.isOnline) TerminalGreen else TerminalRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Live diagnostics",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (telemetry.isOnline) "Direct WireGuard • ${telemetry.latencyMs}ms" else "Host Offline / Unreachable",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (telemetry.isOnline) TerminalGreen else TerminalRed
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Diagnostics",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceStatusCard(
                        icon = Icons.Default.Terminal,
                        name = "SSH :${telemetry.sshPort}",
                        isOpen = telemetry.sshUp,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceStatusCard(
                        icon = Icons.Default.Psychology,
                        name = "LLM :${telemetry.llmPort}",
                        isOpen = telemetry.llmUp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceStatusCard(
                        icon = Icons.Default.DesktopWindows,
                        name = "Desktop :${telemetry.desktopPort}",
                        isOpen = telemetry.desktopUp,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceStatusCard(
                        icon = Icons.Default.Folder,
                        name = "Files :${telemetry.filePort}",
                        isOpen = telemetry.fileUp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkBorderSubtle)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Base URL: ${telemetry.llmBaseUrl.ifBlank { "Not set" }}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SparkBlueBright
                    )
                    Text(
                        text = "IP: ${telemetry.tailscaleIp}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusCard(
    icon: ImageVector,
    name: String,
    isOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.7f))
            .border(
                1.dp,
                if (isOpen) TerminalGreen.copy(alpha = 0.22f) else DarkBorderSubtle,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOpen) CyberCyan else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Icon(
                imageVector = if (isOpen) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = if (isOpen) "Open" else "Closed",
                tint = if (isOpen) TerminalGreen else TerminalRed,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun DiagnosticsDialog(
    telemetry: SystemTelemetry,
    nodeName: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = com.example.ui.theme.DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (telemetry.isOnline) TerminalGreen else TerminalRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Service Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (telemetry.isOnline) "Direct WireGuard • ${telemetry.latencyMs}ms latency" else "Host Unreachable",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (telemetry.isOnline) TerminalGreen else TerminalRed
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceStatusCard(
                        icon = Icons.Default.Terminal,
                        name = "SSH :${telemetry.sshPort}",
                        isOpen = telemetry.sshUp,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceStatusCard(
                        icon = Icons.Default.Psychology,
                        name = "LLM :${telemetry.llmPort}",
                        isOpen = telemetry.llmUp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceStatusCard(
                        icon = Icons.Default.DesktopWindows,
                        name = "Desktop :${telemetry.desktopPort}",
                        isOpen = telemetry.desktopUp,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceStatusCard(
                        icon = Icons.Default.Folder,
                        name = "Files :${telemetry.filePort}",
                        isOpen = telemetry.fileUp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Base URL: ${telemetry.llmBaseUrl.ifBlank { "http://${telemetry.tailscaleIp}:${telemetry.llmPort}" }}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyberCyan
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-test Ports", fontSize = 12.sp, color = CyberCyan)
                    }
                }
            }
        }
    }
}
