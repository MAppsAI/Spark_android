package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TailNode
import com.example.ui.components.AddNodeDialog
import com.example.ui.components.IconTile
import com.example.ui.components.NodeCard
import com.example.ui.components.SparkEmptyState
import com.example.ui.components.heroWash
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.SparkBlueDeep
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TailNodeViewModel

@Composable
fun NodesListScreen(
    viewModel: TailNodeViewModel,
    onNavigateToNode: (TailNode, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes by viewModel.allNodes.collectAsStateWithLifecycle()
    val nodeStatuses by viewModel.nodeStatuses.collectAsStateWithLifecycle()
    val isAddDialogOpen by viewModel.isAddNodeDialogOpen.collectAsStateWithLifecycle()

    var filterType by remember { mutableStateOf("ALL") }

    val filteredNodes = when (filterType) {
        "FAVORITES" -> nodes.filter { it.isFavorite }
        "LINUX" -> nodes.filter { it.osType == "LINUX" }
        "MACOS" -> nodes.filter { it.osType == "MACOS" }
        "WINDOWS" -> nodes.filter { it.osType == "WINDOWS" }
        else -> nodes
    }
    val onlineCount = nodes.count { nodeStatuses[it.id]?.isOnline == true }

    Scaffold(
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(SparkBlue, SparkBlueDeep)
                        )
                    )
                    .border(
                        1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(19.dp)
                    )
                    .clickable { viewModel.openAddNodeDialog() }
                    .testTag("add_node_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Tailscale Computer",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = DarkCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Hero header with brand wash ──────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroWash())
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(
                            icon = Icons.Outlined.Hub,
                            tint = SparkBlue,
                            size = 44.dp,
                            iconSize = 24.dp,
                            corner = 14
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spark",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Your Tailscale mesh",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshAllNodeStatuses() },
                            modifier = Modifier.testTag("refresh_nodes_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Ping Nodes",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Mesh stat strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface.copy(alpha = 0.72f))
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MeshStatItem(
                            label = "Online",
                            value = "$onlineCount / ${nodes.size}",
                            dotColor = if (onlineCount > 0) TerminalGreen else TextMuted
                        )
                        MeshStatItem(label = "Nodes", value = "${nodes.size}")
                        MeshStatItem(label = "Link", value = "WireGuard", accent = TerminalGreen)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Filter pills ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All",
                    "FAVORITES" to "Favorites",
                    "LINUX" to "Linux",
                    "MACOS" to "macOS",
                    "WINDOWS" to "Windows"
                ).forEach { (type, label) ->
                    val isSelected = filterType == type
                    val count = if (type == "ALL") nodes.size else null
                    FilterPill(
                        label = if (count != null) "$label  $count" else label,
                        selected = isSelected,
                        onClick = { filterType = type },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Nodes list / empty state ─────────────────────────────
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SparkEmptyState(
                        title = "No computers yet",
                        subtitle = "Add a machine from your Tailscale network to get terminal, files, LLM chat, Hermes and remote desktop in one place.",
                        icon = Icons.Outlined.Hub,
                        action = {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant.copy(alpha = 0.8f))
                                    .border(1.dp, DarkBorder, CircleShape)
                                    .clickable { viewModel.openAddNodeDialog() }
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SparkBlue,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Add your first computer",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SparkBlue,
                                )
                            }
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNodes) { node ->
                        val status = nodeStatuses[node.id]
                        NodeCard(
                            node = node,
                            status = status,
                            isSelected = false,
                            onSelect = {
                                viewModel.selectNode(node)
                                onNavigateToNode(node, 0)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(node.id) },
                            onOpenFeature = { featureIndex ->
                                viewModel.selectNode(node)
                                viewModel.setSelectedTab(featureIndex)
                                onNavigateToNode(node, featureIndex)
                            }
                        )
                    }

                    item {
                        if (filteredNodes.isEmpty() && filterType != "ALL") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                SparkEmptyState(
                                    title = "Nothing here",
                                    subtitle = "No computers match this filter yet.",
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
        }
    }

    if (isAddDialogOpen) {
        AddNodeDialog(
            onDismiss = { viewModel.closeAddNodeDialog() },
            onSave = { name, ip, os, sshP, sshU, llmP, llmT, llmBase, llmKey, deskP, deskT, deskUrl, tags ->
                viewModel.saveNode(name, ip, os, sshP, sshU, llmP, llmT, llmBase, llmKey, deskP, deskT, deskUrl, tags)
            }
        )
    }
}

@Composable
private fun MeshStatItem(
    label: String,
    value: String,
    dotColor: Color? = null,
    accent: Color? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = accent ?: TextPrimary
        )
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) SparkBlue.copy(alpha = 0.16f)
                else DarkSurfaceVariant.copy(alpha = 0.65f)
            )
            .border(
                1.dp,
                if (selected) SparkBlue.copy(alpha = 0.45f) else DarkBorder,
                CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) SparkBlue else TextSecondary
        )
    }
}
