package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NodeStatus
import com.example.data.model.TailNode
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.HermesGold
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun NodeCard(
    node: TailNode,
    status: NodeStatus?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenFeature: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isOnline = status?.isOnline ?: false
    val latency = status?.latencyMs ?: -1

    SparkCard(
        modifier = modifier.testTag("node_card_${node.id}"),
        onClick = onSelect,
        containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface,
        borderColor = if (isSelected) SparkBlue.copy(alpha = 0.55f) else DarkBorder,
        gradientBorder = isSelected,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 12.dp),
    ) {
        // Top row: OS tile, name + IP, status, favorite
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = osIcon(node.osType),
                tint = if (isOnline) SparkBlue else TextMuted,
                size = 42.dp,
                iconSize = 22.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${node.tailscaleIp}  •  ${node.osType.lowercase()}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = if (isOnline) TextSecondary else TextMuted,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(isOnline = isOnline, latencyMs = latency)
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (node.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (node.isFavorite) TerminalAmber else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Service port tags
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceTag("ssh:${node.sshPort}", isActive = true, accentColor = SparkBlue)
            ServiceTag("llm:${node.llmPort}", isActive = true, accentColor = ElectricBlue)
            ServiceTag("files:${node.fileServerPort}", isActive = true, accentColor = MaterialTheme.colorScheme.tertiary)
            ServiceTag("desk:${node.remoteDesktopPort}", isActive = true, accentColor = TerminalAmber)
        }

        if (node.tags.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = node.tags,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        // Quick launch row — separated by a hairline, feels like an action bar
        if (onOpenFeature != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Hairline()
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeatureQuickButton(
                    icon = Icons.Default.Terminal,
                    label = "Terminal",
                    onClick = { onOpenFeature(0) },
                    modifier = Modifier.weight(1f)
                )
                FeatureQuickButton(
                    icon = Icons.Default.Folder,
                    label = "Files",
                    onClick = { onOpenFeature(1) },
                    modifier = Modifier.weight(1f)
                )
                FeatureQuickButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "LLM",
                    onClick = { onOpenFeature(2) },
                    modifier = Modifier.weight(1f)
                )
                FeatureQuickButton(
                    icon = Icons.Default.SmartToy,
                    label = "Hermes",
                    tint = HermesGold,
                    onClick = { onOpenFeature(3) },
                    modifier = Modifier.weight(1f)
                )
                FeatureQuickButton(
                    icon = Icons.Default.Tv,
                    label = "Desktop",
                    onClick = { onOpenFeature(4) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun osIcon(osType: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (osType.uppercase()) {
    "WINDOWS" -> Icons.Default.DesktopWindows
    "MACOS" -> Icons.Default.PhoneIphone
    "LINUX" -> Icons.Default.Laptop
    else -> Icons.Default.Computer
}

@Composable
private fun FeatureQuickButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val accent = tint ?: SparkBlue
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp)
            .testTag("quick_feat_$label"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent.copy(alpha = 0.9f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = TextSecondary
        )
    }
}
