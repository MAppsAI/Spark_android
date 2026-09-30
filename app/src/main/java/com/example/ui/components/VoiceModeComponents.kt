package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Replay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DiscoveredLlmModel
import com.example.data.model.TailNode
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.SparkBlueBright
import com.example.ui.theme.SparkBlueDeep
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
import com.example.voice.VoiceModelInfo
import com.example.voice.VoiceModelType
import com.example.voice.VoiceState

/**
 * Fullscreen ambient Voice Mode (Gemini / ChatGPT Live style).
 */
@Composable
fun VoiceModeDialog(
    voiceState: VoiceState,
    rmsDecibels: Float,
    spokenText: String,
    aiText: String,
    selectedModel: String,
    allDiscoveredModels: List<DiscoveredLlmModel>,
    currentNode: TailNode?,
    selectedSttModel: VoiceModelInfo? = null,
    selectedTtsModel: VoiceModelInfo? = null,
    onSelectLlmModel: (DiscoveredLlmModel) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onReplaySpeech: () -> Unit = {},
    onClearTranscripts: () -> Unit = {},
    onOpenModelManager: () -> Unit,
    onDismiss: () -> Unit
) {
    var isLlmDropdownOpen by remember { mutableStateOf(false) }

    // Pulsing orb animation
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val reactiveScale = when (voiceState) {
        VoiceState.LISTENING -> (1f + (rmsDecibels / 15f)).coerceIn(1f, 1.4f)
        VoiceState.SPEAKING -> pulseScale * 1.15f
        VoiceState.THINKING -> pulseScale
        VoiceState.IDLE -> 1f
    }

    Dialog(
        onDismissRequest = {
            onStopSpeaking()
            onStopListening()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060B13))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar: Active Machine/Model Pill + Models Manager + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cross-Machine Model Selector Pill
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .clickable { isLlmDropdownOpen = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedModel,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }

                        DropdownMenu(
                            expanded = isLlmDropdownOpen,
                            onDismissRequest = { isLlmDropdownOpen = false },
                            modifier = Modifier
                                .background(DarkSurfaceElevated)
                                .widthIn(min = 260.dp)
                        ) {
                            Text(
                                text = "Switch LLM (All Machines)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            allDiscoveredModels.forEach { item ->
                                val isSelected = item.modelName == selectedModel
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = item.modelName,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isSelected) CyberCyan else TextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "${item.nodeName} (${item.nodeOs})",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectLlmModel(item)
                                        isLlmDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // On-Device STT / TTS Models Manager Button
                        IconButton(
                            onClick = onOpenModelManager,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                                .testTag("voice_model_manager_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsVoice,
                                contentDescription = "Voice Models",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Close Voice Mode
                        IconButton(
                            onClick = {
                                onStopSpeaking()
                                onStopListening()
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Center: Dynamic Audio Orb & Live Transcripts Monitor (STT & TTS)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Compact Pulsating Ambient Sound Orb
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(reactiveScale),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer glowing aura
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = when (voiceState) {
                                            VoiceState.LISTENING -> listOf(CyberCyan.copy(alpha = 0.5f), Color.Transparent)
                                            VoiceState.SPEAKING -> listOf(TerminalGreen.copy(alpha = 0.5f), Color.Transparent)
                                            VoiceState.THINKING -> listOf(TerminalAmber.copy(alpha = 0.5f), Color.Transparent)
                                            VoiceState.IDLE -> listOf(CyberCyan.copy(alpha = 0.2f), Color.Transparent)
                                        }
                                    )
                                )
                        )

                        // Core Orb
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = when (voiceState) {
                                            VoiceState.LISTENING -> listOf(CyberCyan, Color(0xFF0284C7))
                                            VoiceState.SPEAKING -> listOf(TerminalGreen, Color(0xFF059669))
                                            VoiceState.THINKING -> listOf(TerminalAmber, Color(0xFFD97706))
                                            VoiceState.IDLE -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (voiceState) {
                                    VoiceState.LISTENING -> Icons.Default.GraphicEq
                                    VoiceState.SPEAKING -> Icons.Default.VolumeUp
                                    VoiceState.THINKING -> Icons.Default.Psychology
                                    VoiceState.IDLE -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Indicator
                    Text(
                        text = when (voiceState) {
                            VoiceState.LISTENING -> "Listening to your voice..."
                            VoiceState.THINKING -> "Thinking with $selectedModel..."
                            VoiceState.SPEAKING -> "Speaking aloud with TTS..."
                            VoiceState.IDLE -> "Tap mic below to speak"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (voiceState) {
                            VoiceState.LISTENING -> CyberCyan
                            VoiceState.SPEAKING -> TerminalGreen
                            VoiceState.THINKING -> TerminalAmber
                            VoiceState.IDLE -> TextMuted
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // ==========================================
                    // PANEL 1: LIVE STT MODEL TRANSCRIPTION CARD
                    // ==========================================
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (voiceState == VoiceState.LISTENING) CyberCyan.copy(alpha = 0.8f) else DarkBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "STT Model",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "STT Model: ${selectedSttModel?.name ?: "On-Device Recognizer"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (voiceState == VoiceState.LISTENING) CyberCyan.copy(alpha = 0.2f) else DarkSurfaceVariant
                                ) {
                                    Text(
                                        text = when (voiceState) {
                                            VoiceState.LISTENING -> "● LISTENING"
                                            VoiceState.THINKING -> "TRANSCRIBED"
                                            else -> if (spokenText.isNotBlank()) "CAPTURED" else "READY"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (voiceState == VoiceState.LISTENING) CyberCyan else TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Transcript text container
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkCanvas,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp, max = 110.dp)
                            ) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    if (spokenText.isNotBlank()) {
                                        Text(
                                            text = spokenText,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            lineHeight = 16.sp
                                        )
                                    } else {
                                        Text(
                                            text = if (voiceState == VoiceState.LISTENING) "Listening in real time... your speech transcript will appear here."
                                                   else "Say something like 'Check disk space' or 'Show running processes' to test STT transcription.",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }

                            // STT Metrics bar
                            if (spokenText.isNotBlank()) {
                                val wordCount = spokenText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$wordCount words • ${spokenText.length} chars (${selectedSttModel?.tier ?: "On-Device"})",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "Clear",
                                        fontSize = 10.sp,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { onClearTranscripts() }
                                    )
                                }
                            }
                        }
                    }

                    // ==========================================
                    // PANEL 2: LIVE TTS MODEL SPEECH OUTPUT CARD
                    // ==========================================
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (voiceState == VoiceState.SPEAKING) TerminalGreen.copy(alpha = 0.8f) else DarkBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "TTS Model",
                                        tint = TerminalGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TTS Model: ${selectedTtsModel?.name ?: "System Neural TTS Voice"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TerminalGreen
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (voiceState == VoiceState.SPEAKING) TerminalGreen.copy(alpha = 0.2f) else DarkSurfaceVariant
                                ) {
                                    Text(
                                        text = if (voiceState == VoiceState.SPEAKING) "● SPEAKING" else if (aiText.isNotBlank()) "SPOKEN" else "READY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (voiceState == VoiceState.SPEAKING) TerminalGreen else TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // TTS Speech Script container
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkCanvas,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp, max = 110.dp)
                            ) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    if (aiText.isNotBlank()) {
                                        Text(
                                            text = aiText,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            lineHeight = 16.sp
                                        )
                                    } else {
                                        Text(
                                            text = if (voiceState == VoiceState.THINKING) "Generating response from $selectedModel to speak..."
                                                   else "The exact text the TTS model is vocalizing / trying to say will appear here.",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }

                            // TTS Controls & Metrics
                            if (aiText.isNotBlank()) {
                                val wordCount = aiText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$wordCount words spoken (${selectedTtsModel?.tier ?: "Built-in"})",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (voiceState == VoiceState.SPEAKING) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TerminalRed.copy(alpha = 0.2f))
                                                    .clickable { onStopSpeaking() }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Stop, contentDescription = null, tint = TerminalRed, modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Stop", fontSize = 10.sp, color = TerminalRed, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TerminalGreen.copy(alpha = 0.2f))
                                                .clickable { onReplaySpeech() }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Replay, contentDescription = null, tint = TerminalGreen, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Replay", fontSize = 10.sp, color = TerminalGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Controls: Mic Toggle, Stop Audio, Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interrupt / Stop speaking button
                    if (voiceState == VoiceState.SPEAKING) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TerminalRed, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(50.dp))
                    }

                    // Main Push-to-Talk / Listening Toggle
                    IconButton(
                        onClick = {
                            if (voiceState == VoiceState.LISTENING) {
                                onStopListening()
                            } else {
                                onStartListening()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (voiceState == VoiceState.LISTENING)
                                    SolidColor(TerminalRed)
                                else androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(SparkBlueBright, SparkBlueDeep)
                                )
                            )
                            .testTag("voice_mic_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Done / Exit button
                    TextButton(
                        onClick = {
                            onStopSpeaking()
                            onStopListening()
                            onDismiss()
                        }
                    ) {
                        Text("Done", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Bottom Sheet for downloading and managing on-device STT and TTS models (<300MB).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceModelsSheet(
    models: List<VoiceModelInfo>,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(VoiceModelType.STT) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("On-Device Voice Models", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Download Tiny/Medium STT & TTS models (<300MB)", fontSize = 11.sp, color = TextMuted)
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab row: STT (Speech-to-Text) vs TTS (Text-to-Speech)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == VoiceModelType.STT,
                    onClick = { selectedTab = VoiceModelType.STT },
                    label = { Text("Speech-to-Text (STT)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == VoiceModelType.TTS,
                    onClick = { selectedTab = VoiceModelType.TTS },
                    label = { Text("Text-to-Speech (TTS)") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val filteredModels = models.filter { it.type == selectedTab }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(filteredModels) { model ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                if (model.isDownloaded) {
                                    onSelectModel(model.id)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (model.isSelected) DarkSurfaceVariant else DarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = model.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (model.isSelected) CyberCyan else TextPrimary
                                        )
                                        if (model.isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(CyberCyan.copy(alpha = 0.2f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${model.tier} • ${model.sizeFormatted}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                    if (model.loadError.isNotBlank()) {
                                        Text(
                                            text = model.loadError,
                                            fontSize = 10.sp,
                                            color = TerminalRed,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (model.id.endsWith("_system")) {
                                    // Built-in zero download
                                    if (!model.isSelected) {
                                        Button(
                                            onClick = { onSelectModel(model.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = CyberCyan),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Use", fontSize = 11.sp)
                                        }
                                    }
                                } else if (model.isDownloading) {
                                    CircularProgressIndicator(
                                        progress = { model.downloadProgress },
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = CyberCyan
                                    )
                                } else if (model.isDownloaded) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!model.isSelected) {
                                            Button(
                                                onClick = { onSelectModel(model.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = CyberCyan),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Activate", fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { onDeleteModel(model.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onDownloadModel(model.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp).testTag("download_${model.id}")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = model.description, fontSize = 11.sp, color = TextSecondary)

                            if (model.isDownloading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { model.downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = CyberCyan
                                )
                                Text(
                                    text = "Downloading: ${(model.downloadProgress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberCyan,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
