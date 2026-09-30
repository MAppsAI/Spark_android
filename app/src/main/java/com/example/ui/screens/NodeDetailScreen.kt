package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SystemTelemetry
import com.example.data.model.TailNode
import com.example.ui.components.DiagnosticsDialog
import com.example.ui.components.EditNodeDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.HermesGold
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TailNodeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NodeDetailScreen(
    node: TailNode,
    viewModel: TailNodeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allNodes by viewModel.allNodes.collectAsStateWithLifecycle()
    val nodeStatuses by viewModel.nodeStatuses.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val status = nodeStatuses[node.id]

    val isTerminalFocused by viewModel.isTerminalFocused.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardVisible = imeBottomPx > 0 || WindowInsets.isImeVisible || (selectedTab == 0 && isTerminalFocused)

    var isNodeDropdownOpen by remember { mutableStateOf(false) }
    var isEditDialogOpen by remember { mutableStateOf(false) }
    var isDiagnosticsOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isNodeDropdownOpen = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = node.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch Computer",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Sleek clickable status pill
                            StatusBadge(
                                isOnline = status?.isOnline ?: false,
                                latencyMs = status?.latencyMs ?: -1,
                                modifier = Modifier.clickable { isDiagnosticsOpen = true }
                            )
                        }

                        // Switch computer dropdown
                        DropdownMenu(
                            expanded = isNodeDropdownOpen,
                            onDismissRequest = { isNodeDropdownOpen = false },
                            modifier = Modifier.background(DarkSurfaceElevated)
                        ) {
                            allNodes.forEach { otherNode ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = otherNode.name,
                                                fontWeight = if (otherNode.id == node.id) FontWeight.Bold else FontWeight.Normal,
                                                color = if (otherNode.id == node.id) CyberCyan else TextPrimary
                                            )
                                            Text(
                                                text = "${otherNode.tailscaleIp} (${otherNode.osType})",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectNode(otherNode)
                                        isNodeDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_nodes_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isEditDialogOpen = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Computer", tint = CyberCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { isDeleteConfirmOpen = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Computer", tint = TerminalRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            // Auto-hide bottom NavigationBar when software keyboard is visible so typing has full screen!
            if (!isKeyboardVisible) {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 2.dp
                ) {
                    val navItems = listOf(
                        Triple(0, "Terminal", Icons.Default.Terminal),
                        Triple(1, "Files", Icons.Default.Folder),
                        Triple(2, "LLM Chat", Icons.Default.Chat),
                        Triple(3, "Hermes", Icons.Default.SmartToy),
                        Triple(4, "Desktop", Icons.Default.DesktopWindows)
                    )

                    navItems.forEach { (index, label, icon) ->
                        val isSelected = selectedTab == index
                        val activeColor = if (label == "Hermes") HermesGold else CyberCyan
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.setSelectedTab(index) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = activeColor,
                                selectedTextColor = activeColor,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextMuted,
                                indicatorColor = activeColor.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_tab_$label")
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        containerColor = DarkCanvas
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> TerminalScreen(node = node, viewModel = viewModel)
                1 -> FileTransferScreen(node = node, viewModel = viewModel)
                2 -> LlmChatScreen(node = node, viewModel = viewModel)
                3 -> HermesChatScreen(node = node, viewModel = viewModel)
                4 -> RemoteDesktopScreen(node = node, viewModel = viewModel)
            }
        }
    }

    // Diagnostics Dialog
    if (isDiagnosticsOpen) {
        DiagnosticsDialog(
            telemetry = telemetry ?: SystemTelemetry(nodeName = node.name, tailscaleIp = node.tailscaleIp),
            nodeName = node.name,
            onRefresh = { viewModel.checkNodeStatus(node) },
            onDismiss = { isDiagnosticsOpen = false }
        )
    }

    // Edit Computer Dialog
    if (isEditDialogOpen) {
        EditNodeDialog(
            node = node,
            onDismiss = { isEditDialogOpen = false },
            onSave = { updated ->
                viewModel.updateCurrentNode(updated)
                isEditDialogOpen = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (isDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmOpen = false },
            title = { Text("Delete ${node.name}?", color = TextPrimary) },
            text = { Text("This will remove this computer from your TailNode app list.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmOpen = false
                        viewModel.deleteCurrentNode()
                        onBack()
                    }
                ) {
                    Text("Delete", color = TerminalRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
