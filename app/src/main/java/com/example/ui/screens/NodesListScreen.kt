package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TailNode
import com.example.ui.components.AddNodeDialog
import com.example.ui.components.NodeCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddNodeDialog() },
                containerColor = CyberCyan,
                contentColor = Color(0xFF00363D),
                modifier = Modifier.testTag("add_node_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Tailscale Computer")
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
            // Mesh Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Tailscale Mesh Network",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(TerminalGreen)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Direct WireGuard Encrypted",
                                        fontSize = 11.sp,
                                        color = TerminalGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshAllNodeStatuses() },
                            modifier = Modifier.testTag("refresh_nodes_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Ping Nodes",
                                tint = CyberCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MeshStatItem(label = "Connected", value = "${nodes.size} Nodes")
                        MeshStatItem(label = "Device IP", value = "100.64.0.1")
                        MeshStatItem(label = "Security", value = "Peer-to-Peer")
                    }
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${nodes.size})",
                    "FAVORITES" to "Favorites",
                    "LINUX" to "Linux",
                    "MACOS" to "macOS",
                    "WINDOWS" to "Windows"
                ).forEach { (type, label) ->
                    val isSelected = filterType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterType = type },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else DarkSurface,
                            labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nodes List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
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
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
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
private fun MeshStatItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
