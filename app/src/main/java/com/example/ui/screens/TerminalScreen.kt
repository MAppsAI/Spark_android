package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TailNode
import com.example.network.TermuxLaunchResult
import com.example.ui.components.QuickKeyBar
import com.example.ui.theme.CyberCyan
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    node: TailNode,
    viewModel: TailNodeViewModel,
    modifier: Modifier = Modifier
) {
    val terminalLines by viewModel.terminalLines.collectAsStateWithLifecycle()
    val terminalInput by viewModel.terminalInput.collectAsStateWithLifecycle()
    val isRunning by viewModel.isTerminalRunning.collectAsStateWithLifecycle()
    val isSshAuthDialogOpen by viewModel.isSshAuthDialogOpen.collectAsStateWithLifecycle()
    val presets = viewModel.terminalPresets
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val isImeVisible = WindowInsets.isImeVisible

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isInputFocused by remember { mutableStateOf(false) }

    var isPresetsMenuOpen by remember { mutableStateOf(false) }
    var termuxPromptResult by remember { mutableStateOf<TermuxLaunchResult?>(null) }

    // Auto-focus terminal input on initial entry
    LaunchedEffect(Unit) {
        delay(200)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    // Inform ViewModel of terminal focus state and scroll to bottom
    LaunchedEffect(isInputFocused) {
        viewModel.setTerminalFocused(isInputFocused)
        if (isInputFocused && terminalLines.isNotEmpty()) {
            try {
                listState.animateScrollToItem(terminalLines.size - 1)
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setTerminalFocused(false)
        }
    }

    // Auto-scroll on new log entries
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    LaunchedEffect(isImeVisible) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .imePadding()
    ) {
        // Minimal Terminal Header Bar (34dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(DarkSurface)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Bash prompt indicator & Open in Termux
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TerminalGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "bash",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )

                Spacer(modifier = Modifier.width(8.dp))

                // OPEN IN TERMUX BUTTON (Prominent & 1-tap)
                Button(
                    onClick = {
                        val result = viewModel.openInTermux(context)
                        if (result != null) {
                            if (result.isTermuxInstalled) {
                                Toast.makeText(context, "Copied SSH command! Opening Termux...", Toast.LENGTH_SHORT).show()
                                context.startActivity(result.intent)
                            } else {
                                termuxPromptResult = result
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = CyberCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp).testTag("open_in_termux_btn")
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Termux", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(10.dp))
                }
            }

            // Right: Keyboard toggle, Presets dropdown, SSH key, Copy, Clear
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show / Focus Keyboard Button
                IconButton(
                    onClick = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    modifier = Modifier.size(28.dp).testTag("terminal_header_keyboard_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Show Keyboard",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Quick Presets dropdown menu button
                Box {
                    IconButton(
                        onClick = { isPresetsMenuOpen = true },
                        modifier = Modifier.size(28.dp).testTag("terminal_presets_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Presets",
                            tint = CyberCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isPresetsMenuOpen,
                        onDismissRequest = { isPresetsMenuOpen = false },
                        modifier = Modifier.background(DarkSurfaceElevated)
                    ) {
                        Text(
                            text = "Quick Presets",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(preset.title, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                        Text(preset.command, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CyberCyan)
                                    }
                                },
                                onClick = {
                                    isPresetsMenuOpen = false
                                    viewModel.executeTerminalCommand(preset.command)
                                }
                            )
                        }
                    }
                }

                // SSH Password / Auth dialog
                IconButton(
                    onClick = { viewModel.openSshAuthDialog() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "SSH Password",
                        tint = if (node.sshPassword.isNotBlank()) TerminalGreen else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Copy entire terminal log
                IconButton(
                    onClick = {
                        val fullOutput = terminalLines.joinToString("\n") { it.text }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Log", fullOutput))
                        Toast.makeText(context, "Terminal log copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp).testTag("copy_terminal_log_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Log",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Clear terminal
                IconButton(
                    onClick = { viewModel.appendTerminalKey("CLEAR") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 2. Terminal Input Field Row (Always fully visible at top, never covered by keyboard)
        Surface(
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bash prompt indicator "$ "
                Text(
                    text = "$ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                        .padding(start = 2.dp, end = 4.dp)
                )

                // Input box container (Click anywhere to focus & pop keyboard)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCanvas)
                        .border(
                            width = 1.dp,
                            color = if (isInputFocused) CyberCyan else DarkBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = terminalInput,
                        onValueChange = { viewModel.setTerminalInput(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .testTag("terminal_input_field"),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(CyberCyan),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            autoCorrect = false
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { viewModel.executeTerminalCommand() }
                        ),
                        decorationBox = { innerTextField ->
                            if (terminalInput.isEmpty()) {
                                Text(
                                    text = "Type command (e.g. ls, top, ping)...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Dedicated Keyboard Toggle Button (Guaranteed to pop soft keyboard)
                IconButton(
                    onClick = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("terminal_keyboard_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Show Keyboard",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Run / Send Command Button
                IconButton(
                    onClick = { viewModel.executeTerminalCommand() },
                    enabled = !isRunning && terminalInput.isNotBlank(),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isRunning && terminalInput.isNotBlank()) CyberCyan
                            else DarkSurfaceVariant
                        )
                        .testTag("terminal_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = if (isRunning || terminalInput.isBlank()) TextMuted else Color(0xFF00363D),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 3. Quick Key Accessory Bar (With 1-tap Keyboard Toggle key)
        QuickKeyBar(
            onKeyClick = { key ->
                viewModel.appendTerminalKey(key)
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            onShowKeyboard = {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        )

        // 4. Terminal Console Output (WITH FULL TEXT SELECTION & TAP ANYWHERE TO TYPE)
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF030712))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(terminalLines) { line ->
                    if (line.isCommand) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            text = line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (line.isError) TerminalRed else Color(0xFFD1D5DB),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                // Live preview of command being typed in input bar
                if (terminalInput.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$ $terminalInput",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                if (isRunning) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 2.dp,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Running live on ${node.name}...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Termux Launch / Install dialog if Termux not installed
    termuxPromptResult?.let { result ->
        AlertDialog(
            onDismissRequest = { termuxPromptResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in Termux", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        "Termux is not installed on your device. We copied the SSH connection command to your clipboard:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = result.sshCommand,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF030712))
                                .padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "You can download Termux from F-Droid or GitHub Releases to get a full interactive Linux terminal with htop, nano, and tab-completion.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.startActivity(result.intent)
                        termuxPromptResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                ) {
                    Text("Download Termux")
                }
            },
            dismissButton = {
                TextButton(onClick = { termuxPromptResult = null }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // SSH Password Dialog
    if (isSshAuthDialogOpen) {
        var passwordInput by remember { mutableStateOf(node.sshPassword) }
        Dialog(onDismissRequest = { viewModel.closeSshAuthDialog() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("SSH Authentication", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Host: ${node.sshUser}@${node.tailscaleIp}:${node.sshPort}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("SSH Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.closeSshAuthDialog() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.saveSshPassword(passwordInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Text("Save & Connect")
                        }
                    }
                }
            }
        }
    }
}
