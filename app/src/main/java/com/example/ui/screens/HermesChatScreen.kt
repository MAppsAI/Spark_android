package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HermesMcpTool
import com.example.data.model.HermesMessage
import com.example.data.model.HermesToolCall
import com.example.data.model.HermesToolStatus
import com.example.data.model.TailNode
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.HermesBorderGold
import com.example.ui.theme.HermesGold
import com.example.ui.theme.HermesGoldBright
import com.example.ui.theme.HermesGoldDark
import com.example.ui.theme.HermesSurfaceGold
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TailNodeViewModel
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun HermesChatScreen(
    node: TailNode,
    viewModel: TailNodeViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.hermesMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isHermesGenerating.collectAsStateWithLifecycle()
    val health by viewModel.hermesHealth.collectAsStateWithLifecycle()
    val isCheckingHealth by viewModel.isCheckingHermesHealth.collectAsStateWithLifecycle()
    val mcpTools by viewModel.hermesMcpTools.collectAsStateWithLifecycle()
    val liveThinking by viewModel.hermesLiveThinking.collectAsStateWithLifecycle()
    val liveTools by viewModel.hermesLiveTools.collectAsStateWithLifecycle()
    val input by viewModel.hermesInput.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val listState = rememberLazyListState()

    var isConfigDialogOpen by remember { mutableStateOf(false) }
    var isToolsDialogOpen by remember { mutableStateOf(false) }

    // Auto-scroll on new messages or generation updates
    LaunchedEffect(messages.size, liveThinking, liveTools.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .imePadding() // Critical: Keeps input visible when software keyboard pops up!
    ) {
        // --- Minimal Hermes Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status and Gateway Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(HermesSurfaceGold)
                    .border(1.dp, HermesBorderGold, RoundedCornerShape(16.dp))
                    .clickable { isConfigDialogOpen = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = HermesGoldBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hermes Gateway",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HermesGold
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Online/Latency Indicator
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (health?.isReachable == true) TerminalGreen else TerminalRed)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (health?.isReachable == true) "${health?.latencyMs ?: 0}ms" else "Offline",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (health?.isReachable == true) TerminalGreen else TextMuted
                )
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // MCP / Host Tools Explorer Button
                IconButton(
                    onClick = { isToolsDialogOpen = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "View MCP & Host Tools",
                        tint = HermesGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // New Session Button
                IconButton(
                    onClick = {
                        viewModel.createNewHermesSession()
                        Toast.makeText(context, "Started new Hermes session", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Gateway Settings Button
                IconButton(
                    onClick = { isConfigDialogOpen = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Hermes Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- Active Tool Execution Live Bar (when agent is actively executing on computer) ---
        if (liveTools.any { it.status == HermesToolStatus.RUNNING }) {
            val activeTool = liveTools.last { it.status == HermesToolStatus.RUNNING }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HermesSurfaceGold)
                    .border(1.dp, HermesGold.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = HermesGoldBright,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Executing tool on ${node.name}: ",
                    fontSize = 11.sp,
                    color = HermesGold
                )
                Text(
                    text = activeTool.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }

        // --- Main Chat Stream / Empty State ---
        if (messages.isEmpty() && !isGenerating) {
            HermesEmptyState(
                node = node,
                health = health,
                isCheckingHealth = isCheckingHealth,
                onPromptClick = { prompt -> viewModel.sendHermesMessage(prompt) },
                onOpenSettings = { isConfigDialogOpen = true },
                onRefreshHealth = { viewModel.checkHermesHealth(node) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                items(messages, key = { it.id }) { msg ->
                    HermesMessageBubble(
                        message = msg,
                        nodeName = node.name
                    )
                }

                // If agent is streaming live reasoning/thinking
                if (isGenerating && !liveThinking.isNullOrBlank()) {
                    item {
                        HermesThinkingCard(
                            thinking = liveThinking ?: "",
                            isLive = true
                        )
                    }
                }

                // Live tool calls executing in real-time
                if (liveTools.isNotEmpty()) {
                    items(liveTools, key = { "live_${it.id}" }) { toolCall ->
                        HermesToolExecutionCard(
                            toolCall = toolCall,
                            nodeName = node.name
                        )
                    }
                }

                if (isGenerating) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = HermesGold,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hermes Agent reasoning & executing on ${node.name}...",
                                fontSize = 11.sp,
                                color = HermesGold
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(6.dp)) }
            }
        }

        // --- Input Row (Always accessible, imePadding aware) ---
        HermesInputBar(
            input = input,
            isGenerating = isGenerating,
            onInputChange = { viewModel.setHermesInput(it) },
            onSend = { viewModel.sendHermesMessage() },
            modifier = Modifier.fillMaxWidth()
        )
    }

    // --- Dialogs ---
    if (isConfigDialogOpen) {
        HermesConfigDialog(
            node = node,
            health = health,
            isCheckingHealth = isCheckingHealth,
            onDismiss = { isConfigDialogOpen = false },
            onSave = { port, apiKey, dashPort, customUrl ->
                viewModel.updateHermesConfig(port, apiKey, dashPort, customUrl)
                isConfigDialogOpen = false
            },
            onTest = { viewModel.checkHermesHealth(node) }
        )
    }

    if (isToolsDialogOpen) {
        HermesToolsDialog(
            node = node,
            tools = mcpTools,
            onDismiss = { isToolsDialogOpen = false },
            onRunExample = { prompt ->
                viewModel.sendHermesMessage(prompt)
                isToolsDialogOpen = false
            }
        )
    }
}

/**
 * Empty state providing fast starter prompts and architecture explanation.
 */
@Composable
private fun HermesEmptyState(
    node: TailNode,
    health: com.example.data.model.HermesHealthStatus?,
    isCheckingHealth: Boolean,
    onPromptClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(HermesSurfaceGold)
                .border(2.dp, HermesGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = HermesGoldBright,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Hermes Agent Gateway",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = HermesGold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Host Rig: ${node.name} (${node.tailscaleIp}:${node.hermesPort})",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Chat remotely from your phone while Hermes executes bash commands, files, and configured MCP tools directly on your computer.",
            fontSize = 12.sp,
            color = TextMuted,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Gateway Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (health?.isReachable == true) DarkSurfaceElevated else DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (health?.isReachable == true) TerminalGreen.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (health?.isReachable == true) TerminalGreen else TerminalAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (health?.isReachable == true) "Gateway Connected (${health.latencyMs} ms)" else "Gateway Unchecked / Offline",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (health?.isReachable == true) TerminalGreen else TerminalAmber
                        )
                        Text(
                            text = if (health?.isReachable == true) "${health.serverVersion ?: "v2.1"} • ${health.toolsCount} Tools & MCPs Active" else "Tap ⚙️ to configure API key from ~/.hermes/.env",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Row {
                    TextButton(onClick = onRefreshHealth) {
                        Text(if (isCheckingHealth) "Pinging..." else "Ping", fontSize = 11.sp, color = HermesGold)
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("Config", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "⚡ Quick Agent Tasks (runs on ${node.name})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HermesGold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val presetTasks = listOf(
            "Check system resources, CPU, RAM, and disk space on this rig",
            "List active Docker containers and check if services are healthy",
            "Inspect git status in my main projects and report changes",
            "What tools and MCP servers are configured in my Hermes environment?"
        )

        presetTasks.forEach { task ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onPromptClick(task) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = HermesGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Message Bubble representing either User prompt or Hermes Agent output.
 */
@Composable
private fun HermesMessageBubble(
    message: HermesMessage,
    nodeName: String
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Parse any tools embedded in toolCallsJson
    val toolsList = remember(message.toolCallsJson) {
        val list = mutableListOf<HermesToolCall>()
        if (message.toolCallsJson.isNotBlank()) {
            try {
                val array = JSONArray(message.toolCallsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HermesToolCall(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            arguments = obj.optString("arguments"),
                            result = obj.optString("result"),
                            status = try { HermesToolStatus.valueOf(obj.optString("status")) } catch (_: Exception) { HermesToolStatus.SUCCESS }
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        list
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!isUser) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = HermesGold,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Hermes Agent • $nodeName",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = HermesGold
                )
            } else {
                Text(
                    text = "You",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }

        // Reasoning/Thinking card if present in message
        if (!message.thinkingContent.isNullOrBlank()) {
            HermesThinkingCard(thinking = message.thinkingContent)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Tool execution cards if this step performed computer actions
        toolsList.forEach { tool ->
            HermesToolExecutionCard(toolCall = tool, nodeName = nodeName)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Message Content Box
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            color = if (isUser) DarkSurfaceElevated else HermesSurfaceGold,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = if (isUser) DarkBorder else HermesBorderGold,
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 12.dp
                    )
                )
                .clickable {
                    clipboard.setText(AnnotatedString(message.content))
                    Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Collapsible Thinking card showing Hermes Agent internal chain-of-thought.
 */
@Composable
private fun HermesThinkingCard(
    thinking: String,
    isLive: Boolean = false
) {
    var expanded by remember { mutableStateOf(isLive) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HermesGoldDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = HermesGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLive) "Agent Reasoning..." else "Agent Reasoning",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HermesGold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        text = thinking,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .background(DarkCanvas, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Tool / MCP execution card showing action executed on the computer.
 */
@Composable
private fun HermesToolExecutionCard(
    toolCall: HermesToolCall,
    nodeName: String
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when (toolCall.status) {
                    HermesToolStatus.RUNNING -> HermesGold
                    HermesToolStatus.SUCCESS -> TerminalGreen.copy(alpha = 0.6f)
                    HermesToolStatus.ERROR -> TerminalRed.copy(alpha = 0.6f)
                    else -> DarkBorder
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            toolCall.name.contains("bash") || toolCall.name.contains("command") -> Icons.Default.Terminal
                            else -> Icons.Default.Build
                        },
                        contentDescription = null,
                        tint = if (toolCall.status == HermesToolStatus.RUNNING) HermesGold else TerminalGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = toolCall.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "on $nodeName",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (toolCall.status) {
                            HermesToolStatus.RUNNING -> "RUNNING"
                            HermesToolStatus.SUCCESS -> "SUCCESS"
                            HermesToolStatus.ERROR -> "ERROR"
                            HermesToolStatus.PENDING -> "PENDING"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (toolCall.status) {
                            HermesToolStatus.RUNNING -> HermesGold
                            HermesToolStatus.SUCCESS -> TerminalGreen
                            HermesToolStatus.ERROR -> TerminalRed
                            HermesToolStatus.PENDING -> TextMuted
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    if (toolCall.arguments.isNotBlank() && toolCall.arguments != "{}") {
                        Text(
                            text = "Parameters:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = toolCall.arguments,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HermesGoldBright,
                            modifier = Modifier
                                .background(DarkCanvas, RoundedCornerShape(4.dp))
                                .padding(6.dp)
                                .fillMaxWidth()
                        )
                    }

                    if (!toolCall.result.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Computer Output:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = toolCall.result ?: "",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            modifier = Modifier
                                .background(DarkCanvas, RoundedCornerShape(4.dp))
                                .padding(6.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Input Bar for entering prompts to Hermes Agent.
 */
@Composable
private fun HermesInputBar(
    input: String,
    isGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text("Command Hermes Agent (e.g. run git status or execute MCP)", fontSize = 12.sp, color = TextMuted)
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HermesGold,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = HermesGold
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("hermes_input_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .size(42.dp)
                    .background(if (input.isNotBlank() && !isGenerating) HermesGold else DarkSurfaceVariant, CircleShape)
                    .testTag("hermes_send_btn")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = HermesGold, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = if (input.isNotBlank()) Color.Black else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dialog to configure Hermes Gateway Port, Dashboard Port, and API_SERVER_KEY.
 */
@Composable
private fun HermesConfigDialog(
    node: TailNode,
    health: com.example.data.model.HermesHealthStatus?,
    isCheckingHealth: Boolean,
    onDismiss: () -> Unit,
    onSave: (port: Int, apiKey: String, dashPort: Int, customUrl: String) -> Unit,
    onTest: () -> Unit
) {
    var portText by remember { mutableStateOf(node.hermesPort.toString()) }
    var apiKeyText by remember { mutableStateOf(node.hermesApiKey) }
    var dashPortText by remember { mutableStateOf(node.hermesDashboardPort.toString()) }
    var customUrlText by remember { mutableStateOf(node.hermesBaseUrl) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = HermesGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hermes Gateway Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Information banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HermesSurfaceGold,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Host Machine: ${node.name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HermesGold
                        )
                        Text(
                            text = "Hermes Agent must be installed and running on ${node.name}.\nCommand: `hermes gateway start`\nKey location: `~/.hermes/.env`",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gateway Port (Default 8642)
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Gateway API Port (Default: 8642)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HermesGold),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // API_SERVER_KEY
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("API_SERVER_KEY (from ~/.hermes/.env)") },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Text(if (isApiKeyVisible) "Hide" else "Show", fontSize = 11.sp, color = HermesGold)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HermesGold),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dashboard Port (Default 9119)
                OutlinedTextField(
                    value = dashPortText,
                    onValueChange = { dashPortText = it },
                    label = { Text("Hermes Dashboard WebUI Port (Default: 9119)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HermesGold),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Gateway URL (Optional override)
                OutlinedTextField(
                    value = customUrlText,
                    onValueChange = { customUrlText = it },
                    label = { Text("Custom Gateway URL (Optional override)") },
                    placeholder = { Text("http://${node.tailscaleIp}:8642") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HermesGold),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Test Connection Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isCheckingHealth,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesGold)
                    ) {
                        if (isCheckingHealth) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = HermesGold, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Text("Test Connection")
                        }
                    }

                    // Open Web Dashboard Button
                    TextButton(
                        onClick = {
                            val dashUrl = node.getEffectiveHermesDashboardUrl()
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dashUrl))
                            try {
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Dashboard", fontSize = 11.sp, color = HermesGold)
                    }
                }

                if (health != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (health.isReachable) "✓ Gateway reachable (${health.latencyMs} ms, ${health.serverVersion})" else "✗ ${health.errorMessage}",
                        fontSize = 11.sp,
                        color = if (health.isReachable) TerminalGreen else TerminalRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: 8642
                        val dashPort = dashPortText.toIntOrNull() ?: 9119
                        onSave(port, apiKeyText, dashPort, customUrlText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HermesGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Configuration", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog displaying available Host Tools and MCP servers configured on Hermes.
 */
@Composable
private fun HermesToolsDialog(
    node: TailNode,
    tools: List<HermesMcpTool>,
    onDismiss: () -> Unit,
    onRunExample: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = HermesGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hermes Host Tools & MCPs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "These capabilities execute directly on ${node.name}. When you prompt Hermes, it decides which tools to invoke.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                tools.forEach { tool ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tool.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = HermesGold
                                )
                                Text(
                                    text = tool.category,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    modifier = Modifier
                                        .background(DarkCanvas, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tool.description,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onRunExample("Inspect my host system and list all active MCP servers and installed tools")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HermesSurfaceGold, contentColor = HermesGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ask Hermes to Audit MCP Tools", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
