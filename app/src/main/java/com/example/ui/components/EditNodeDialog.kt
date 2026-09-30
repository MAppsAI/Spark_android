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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.OsType
import com.example.data.model.TailNode
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.HermesGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditNodeDialog(
    node: TailNode,
    onDismiss: () -> Unit,
    onSave: (TailNode) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var ip by remember { mutableStateOf(node.tailscaleIp) }
    var selectedOs by remember { mutableStateOf(try { OsType.valueOf(node.osType) } catch (_: Exception) { OsType.LINUX }) }
    var sshPort by remember { mutableIntStateOf(node.sshPort) }
    var sshUser by remember { mutableStateOf(node.sshUser) }
    var llmPort by remember { mutableIntStateOf(node.llmPort) }
    var llmType by remember { mutableStateOf(node.llmType) }
    var llmBaseUrl by remember { mutableStateOf(node.llmBaseUrl) }
    var llmApiKey by remember { mutableStateOf(node.llmApiKey) }
    var llmModel by remember { mutableStateOf(node.llmDefaultModel) }
    var hermesPort by remember { mutableIntStateOf(node.hermesPort) }
    var hermesDashboardPort by remember { mutableIntStateOf(node.hermesDashboardPort) }
    var hermesApiKey by remember { mutableStateOf(node.hermesApiKey) }
    var hermesBaseUrl by remember { mutableStateOf(node.hermesBaseUrl) }
    var desktopPort by remember { mutableIntStateOf(node.remoteDesktopPort) }
    var desktopType by remember { mutableStateOf(node.remoteDesktopType) }
    var desktopUrl by remember { mutableStateOf(node.remoteDesktopUrl) }
    var filePort by remember { mutableIntStateOf(node.fileServerPort) }
    var tags by remember { mutableStateOf(node.tags) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Computer Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_edit_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Computer Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Computer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tailscale IP
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Tailscale IP or MagicDNS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // OS Selector
                Text("Operating System", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OsType.entries.forEach { os ->
                        val isSelected = selectedOs == os
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { selectedOs = os }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = os.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // LLM Settings
                Text("LLM Engine & Base URL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = llmType == "OLLAMA",
                        onClick = { llmType = "OLLAMA" },
                        label = { Text("Ollama") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = llmType == "OPENAI_COMPATIBLE",
                        onClick = { llmType = "OPENAI_COMPATIBLE" },
                        label = { Text("OpenAI / LM Studio") },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                OutlinedTextField(
                    value = llmBaseUrl,
                    onValueChange = { llmBaseUrl = it },
                    label = { Text("Custom LLM Base URL") },
                    placeholder = { Text("http://${ip.trim()}:$llmPort") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = llmPort.toString(),
                        onValueChange = { llmPort = it.toIntOrNull() ?: 11434 },
                        label = { Text("LLM Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = llmModel,
                        onValueChange = { llmModel = it },
                        label = { Text("Default Model") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = llmApiKey,
                    onValueChange = { llmApiKey = it },
                    label = { Text("API Key / Bearer Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SSH & Desktop Settings
                Text("SSH & Desktop Ports", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sshUser,
                        onValueChange = { sshUser = it },
                        label = { Text("SSH User") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sshPort.toString(),
                        onValueChange = { sshPort = it.toIntOrNull() ?: 22 },
                        label = { Text("SSH Port") },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = desktopPort.toString(),
                        onValueChange = { desktopPort = it.toIntOrNull() ?: 6080 },
                        label = { Text("Desktop Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hermes Gateway Section
                Text(
                    text = "Hermes Agent Gateway (Desktop)",
                    style = MaterialTheme.typography.labelLarge,
                    color = HermesGold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = hermesPort.toString(),
                        onValueChange = { hermesPort = it.toIntOrNull() ?: 8642 },
                        label = { Text("Gateway Port (8642)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = hermesDashboardPort.toString(),
                        onValueChange = { hermesDashboardPort = it.toIntOrNull() ?: 9119 },
                        label = { Text("Dashboard (9119)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = hermesApiKey,
                    onValueChange = { hermesApiKey = it },
                    label = { Text("API_SERVER_KEY (from ~/.hermes/.env)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = TextPrimary)) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = node.copy(
                                name = name.ifBlank { "Tailscale Computer" },
                                tailscaleIp = ip.trim(),
                                osType = selectedOs.name,
                                sshPort = sshPort,
                                sshUser = sshUser.ifBlank { "admin" },
                                llmPort = llmPort,
                                llmType = llmType,
                                llmBaseUrl = llmBaseUrl.trim(),
                                llmApiKey = llmApiKey.trim(),
                                llmDefaultModel = llmModel.ifBlank { node.llmDefaultModel },
                                hermesPort = hermesPort,
                                hermesDashboardPort = hermesDashboardPort,
                                hermesApiKey = hermesApiKey.trim(),
                                hermesBaseUrl = hermesBaseUrl.trim(),
                                remoteDesktopPort = desktopPort,
                                remoteDesktopType = desktopType,
                                remoteDesktopUrl = desktopUrl.trim(),
                                fileServerPort = filePort,
                                tags = tags
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier.testTag("save_edit_node_btn")
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
