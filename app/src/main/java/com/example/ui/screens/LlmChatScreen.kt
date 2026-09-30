package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatConversation
import com.example.data.model.ChatMessage
import com.example.data.model.DiscoveredLlmModel
import com.example.data.model.TailNode
import com.example.network.SearchResult
import com.example.network.SearchSnippet
import com.example.ui.components.VoiceModeDialog
import com.example.ui.components.VoiceModelsSheet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.SparkBlueBright
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TailNodeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LlmChatScreen(
    node: TailNode,
    viewModel: TailNodeViewModel,
    modifier: Modifier = Modifier
) {
    val allConversations by viewModel.allConversations.collectAsStateWithLifecycle()
    val currentConversationId by viewModel.currentConversationId.collectAsStateWithLifecycle()
    val currentConversationTitle by viewModel.currentConversationTitle.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatInput by viewModel.chatInput.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isLlmGenerating.collectAsStateWithLifecycle()
    val allDiscoveredModels by viewModel.allDiscoveredModels.collectAsStateWithLifecycle()
    val selectedDiscoveredModel by viewModel.selectedDiscoveredModel.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val allNodes by viewModel.allNodes.collectAsStateWithLifecycle()

    val isWebSearchEnabled by viewModel.isWebSearchEnabled.collectAsStateWithLifecycle()
    val isSearchingWeb by viewModel.isSearchingWeb.collectAsStateWithLifecycle()
    val lastSearchQuery by viewModel.lastSearchQuery.collectAsStateWithLifecycle()
    val lastSearchResults by viewModel.lastSearchResults.collectAsStateWithLifecycle()

    val isVoiceModeOpen by viewModel.isVoiceModeOpen.collectAsStateWithLifecycle()
    val isVoiceModelsSheetOpen by viewModel.isVoiceModelsSheetOpen.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceModels by viewModel.voiceModels.collectAsStateWithLifecycle()
    val voiceRmsDecibels by viewModel.voiceRmsDecibels.collectAsStateWithLifecycle()
    val voiceLiveSpokenText by viewModel.voiceLiveSpokenText.collectAsStateWithLifecycle()
    val voiceLiveAiSpeechText by viewModel.voiceLiveAiSpeechText.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val isImeVisible = WindowInsets.isImeVisible

    var isHistorySheetOpen by remember { mutableStateOf(false) }
    var isModelMenuOpen by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<ChatConversation?>(null) }
    var showWebSourcesCard by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.openVoiceMode()
        } else {
            Toast.makeText(context, "Microphone permission required for Voice Mode", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(chatMessages.size - 1)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isImeVisible) {
        if (chatMessages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(chatMessages.size - 1)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .imePadding()
    ) {
        // Gemini-Style Header Bar (36dp): Drawer Trigger + Multi-Node Model Picker + New Chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(DarkSurface)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: History & Threads Drawer Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isHistorySheetOpen = true },
                    modifier = Modifier.size(32.dp).testTag("chat_history_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Chat History",
                        tint = CyberCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Text(
                    text = currentConversationTitle.ifBlank { "Chat" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(max = 120.dp)
                        .clickable { isHistorySheetOpen = true }
                )
            }

            // Center/Right: Cross-Machine Model Selector Pill
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            isModelMenuOpen = true
                            if (allDiscoveredModels.isEmpty()) {
                                viewModel.refreshAllDiscoveredModels()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    val activeModelDisplayName = selectedDiscoveredModel?.displayName
                        ?: "$selectedModel • ${node.name}"

                    Text(
                        text = activeModelDisplayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Model",
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Cross-Machine Model Dropdown Menu
                DropdownMenu(
                    expanded = isModelMenuOpen,
                    onDismissRequest = { isModelMenuOpen = false },
                    modifier = Modifier
                        .background(DarkSurfaceElevated)
                        .widthIn(min = 280.dp)
                ) {
                    Text(
                        text = "Models Across All Machines",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    if (allDiscoveredModels.isNotEmpty()) {
                        allDiscoveredModels.forEach { item ->
                            val isSelected = (selectedDiscoveredModel?.modelName == item.modelName &&
                                    selectedDiscoveredModel?.nodeId == item.nodeId) ||
                                    (selectedDiscoveredModel == null && selectedModel == item.modelName)

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.modelName,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isSelected) CyberCyan else TextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "${item.nodeName} (${item.nodeOs}) • ${item.llmType}",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = CyberCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectDiscoveredModel(item)
                                    isModelMenuOpen = false
                                }
                            )
                        }
                    } else {
                        // Fallback to current node's model
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = selectedModel.ifBlank { node.llmDefaultModel },
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${node.name} (${node.osType})",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            },
                            onClick = { isModelMenuOpen = false }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan models across all nodes", fontSize = 11.sp, color = CyberCyan)
                            }
                        },
                        onClick = {
                            viewModel.refreshAllDiscoveredModels()
                            isModelMenuOpen = false
                        }
                    )
                }
            }

            // Right: New Chat 1-tap Button & Clear
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.createNewChat() },
                    modifier = Modifier.size(32.dp).testTag("new_chat_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.clearCurrentChat() },
                    modifier = Modifier.size(32.dp).testTag("clear_chat_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Chat",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Active Chat Messages List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isEmpty()) {
                // Empty state greeting
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Unified Multi-Node Chat",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You can switch models across all your Tailscale machines (Linux, Windows, Mac) right inside this single conversation.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Quick suggestion prompts
                    listOf(
                        "Check disk space and running Docker containers",
                        "Write a bash script to monitor GPU temperature",
                        "Show system memory usage and top processes",
                        "Explain my Tailscale mesh network configuration"
                    ).forEach { suggestion ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.setChatInput(suggestion)
                                    viewModel.sendChatMessage()
                                },
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(suggestion, fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    items(chatMessages, key = { it.id }) { message ->
                        ChatMessageBubble(
                            message = message,
                            allNodes = allNodes,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "Copied message to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // DuckDuckGo Search Sources Card
                    lastSearchResults?.let { results ->
                        if (results.snippets.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showWebSourcesCard = !showWebSourcesCard },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = CyberCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "DuckDuckGo Sources (${results.snippets.size} results)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyberCyan
                                                )
                                            }
                                            Text(
                                                text = if (showWebSourcesCard) "Hide" else "Show",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }

                                        if (showWebSourcesCard) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            results.snippets.forEachIndexed { idx, snippet ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${idx + 1}. ${snippet.title}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            maxLines = 1,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                try {
                                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(snippet.url))
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) {
                                                                    Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.OpenInBrowser,
                                                                contentDescription = "Open Link",
                                                                tint = CyberCyan,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = snippet.snippet,
                                                        fontSize = 10.sp,
                                                        color = TextSecondary,
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        item {
                            val activeTargetName = selectedDiscoveredModel?.nodeName ?: node.name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = CyberCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isSearchingWeb) "Searching DuckDuckGo for \"$lastSearchQuery\"..."
                                               else "Generating via $selectedModel on $activeTargetName...",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberCyan,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.stopLlmGeneration() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalRed),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TerminalRed.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("stop_generation_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Speech Monitor HUD (Displays live STT transcription and TTS output directly in chat)
        if (voiceLiveSpokenText.isNotBlank() || voiceLiveAiSpeechText.isNotBlank() || voiceState != com.example.voice.VoiceState.IDLE) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (voiceState == com.example.voice.VoiceState.LISTENING) CyberCyan else DarkBorder
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (voiceState == com.example.voice.VoiceState.LISTENING) CyberCyan else TerminalGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Live Speech Monitor",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (voiceState) {
                                    com.example.voice.VoiceState.LISTENING -> CyberCyan.copy(alpha = 0.2f)
                                    com.example.voice.VoiceState.SPEAKING -> TerminalGreen.copy(alpha = 0.2f)
                                    else -> DarkSurfaceVariant
                                }
                            ) {
                                Text(
                                    text = when (voiceState) {
                                        com.example.voice.VoiceState.LISTENING -> "STT: Listening..."
                                        com.example.voice.VoiceState.SPEAKING -> "TTS: Speaking..."
                                        else -> "STT & TTS Active"
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (voiceState) {
                                        com.example.voice.VoiceState.LISTENING -> CyberCyan
                                        com.example.voice.VoiceState.SPEAKING -> TerminalGreen
                                        else -> TextMuted
                                    },
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Open Orb",
                                fontSize = 10.sp,
                                color = CyberCyan,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { viewModel.openVoiceMode() }
                                    .padding(horizontal = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Clear",
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier
                                    .clickable { viewModel.clearLiveTranscripts() }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }

                    if (voiceLiveSpokenText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "STT Transcript: ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Text(
                                text = "\"$voiceLiveSpokenText\"",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (voiceLiveAiSpeechText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "TTS Spoken: ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerminalGreen
                            )
                            Text(
                                text = "\"$voiceLiveAiSpeechText\"",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Tools Strip: DuckDuckGo Web Search Tool toggle + Voice Mode shortcut
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // DDG Web Search Tool Toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isWebSearchEnabled) CyberCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                    .clickable { viewModel.toggleWebSearch() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "DuckDuckGo Web Search Tool",
                    tint = if (isWebSearchEnabled) CyberCyan else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isWebSearchEnabled) "DDG Web Search ON" else "DDG Search OFF",
                    fontSize = 11.sp,
                    fontWeight = if (isWebSearchEnabled) FontWeight.Bold else FontWeight.Normal,
                    color = if (isWebSearchEnabled) CyberCyan else TextMuted
                )
            }

            // Ambient Voice Mode Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .clickable {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.openVoiceMode()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Voice Mode",
                    tint = TerminalAmber,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Voice Mode",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalAmber
                )
            }
        }

        // Chat Input Row (Anchored right above keyboard)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Mic Button
            IconButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.openVoiceMode()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(36.dp).testTag("chat_mic_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Mode",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = chatInput,
                onValueChange = { viewModel.setChatInput(it) },
                placeholder = {
                    Text(
                        if (isWebSearchEnabled) "Ask $selectedModel (with DDG search)..." else "Ask $selectedModel...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("chat_input_field"),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    color = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.sendChatMessage() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkCanvas,
                    unfocusedContainerColor = DarkCanvas
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (isGenerating) {
                // STOP BUTTON WHEN GENERATING
                IconButton(
                    onClick = { viewModel.stopLlmGeneration() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TerminalRed)
                        .testTag("chat_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Generating",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                // SEND BUTTON
                IconButton(
                    onClick = { viewModel.sendChatMessage() },
                    enabled = chatInput.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (chatInput.isBlank()) DarkSurfaceVariant else CyberCyan)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (chatInput.isBlank()) TextMuted else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Gemini-Style Chat History Bottom Sheet / Drawer
    if (isHistorySheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { isHistorySheetOpen = false },
            sheetState = sheetState,
            containerColor = DarkSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Sheet Header: Title & New Chat Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chat History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Button(
                        onClick = {
                            viewModel.createNewChat()
                            isHistorySheetOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("sheet_new_chat_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (allConversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text("No conversations yet. Tap '+ New Chat' to begin.", fontSize = 12.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(allConversations) { conv ->
                            val isCurrent = conv.id == currentConversationId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.selectConversation(conv.id)
                                        isHistorySheetOpen = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) DarkSurfaceVariant else DarkSurface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = if (isCurrent) CyberCyan else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conv.title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) CyberCyan else TextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${formatRelativeDate(conv.updatedAt)} • ${conv.lastModelUsed}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted
                                        )
                                    }

                                    IconButton(
                                        onClick = { conversationToDelete = conv },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Thread",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Delete Conversation Confirmation Dialog
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Chat Thread?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    "Are you sure you want to delete '${conv.title}'? All message history in this thread will be removed.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversation(conv.id)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Ambient Voice Mode (On-Device STT/TTS & Cross-Machine LLM)
    if (isVoiceModeOpen) {
        VoiceModeDialog(
            voiceState = voiceState,
            rmsDecibels = voiceRmsDecibels,
            spokenText = voiceLiveSpokenText,
            aiText = voiceLiveAiSpeechText,
            selectedModel = selectedDiscoveredModel?.modelName ?: selectedModel,
            allDiscoveredModels = allDiscoveredModels,
            currentNode = node,
            selectedSttModel = viewModel.selectedSttModel,
            selectedTtsModel = viewModel.selectedTtsModel,
            onSelectLlmModel = { viewModel.selectDiscoveredModel(it) },
            onStartListening = { viewModel.startVoiceListening() },
            onStopListening = { viewModel.stopVoiceListening() },
            onStopSpeaking = { viewModel.stopVoiceSpeaking() },
            onReplaySpeech = { viewModel.replayLastSpeech() },
            onClearTranscripts = { viewModel.clearLiveTranscripts() },
            onOpenModelManager = { viewModel.openVoiceModelsSheet() },
            onDismiss = { viewModel.closeVoiceMode() }
        )
    }

    // On-Device STT & TTS Models Management Bottom Sheet (<300MB)
    if (isVoiceModelsSheetOpen) {
        VoiceModelsSheet(
            models = voiceModels,
            onDownloadModel = { viewModel.downloadVoiceModel(it) },
            onDeleteModel = { viewModel.deleteVoiceModel(it) },
            onSelectModel = { viewModel.selectVoiceModel(it) },
            lanServerUrl = viewModel.getLanServerUrl(),
            onSetLanServerUrl = { viewModel.setLanServerUrl(it) },
            onDismiss = { viewModel.closeVoiceModelsSheet() }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    allNodes: List<TailNode>,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    val replyingNode = allNodes.find { it.id == message.nodeId }
    val nodeLabel = replyingNode?.let { "${it.name} (${it.osType})" } ?: "Remote Node"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Model and Machine provenance badge for assistant responses
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${message.modelUsed} • $nodeLabel",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = SparkBlueBright
                )
                message.inferenceStats?.let { stats ->
                    Text(
                        text = " • $stats",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
            }
        }

        val bubbleShape = RoundedCornerShape(
            topStart = if (isUser) 18.dp else 4.dp,
            topEnd = if (isUser) 4.dp else 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        )
        Surface(
            shape = bubbleShape,
            color = if (isUser) SparkBlue.copy(alpha = 0.14f) else DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) SparkBlue.copy(alpha = 0.30f) else DarkBorder
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 21.sp
                )

                // Copy button bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatRelativeDate(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}
