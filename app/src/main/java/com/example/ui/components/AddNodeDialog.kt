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
import androidx.compose.material.icons.filled.Computer
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
import com.example.ui.theme.CyberCyan
import androidx.compose.foundation.border
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AddNodeDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        tailscaleIp: String,
        osType: String,
        sshPort: Int,
        sshUser: String,
        llmPort: Int,
        llmType: String,
        llmBaseUrl: String,
        llmApiKey: String,
        remoteDesktopPort: Int,
        remoteDesktopType: String,
        remoteDesktopUrl: String,
        tags: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("100.") }
    var selectedOs by remember { mutableStateOf(OsType.LINUX) }
    var sshPort by remember { mutableIntStateOf(22) }
    var sshUser by remember { mutableStateOf("ubuntu") }
    var llmPort by remember { mutableIntStateOf(11434) }
    var llmType by remember { mutableStateOf("OLLAMA") }
    var llmBaseUrl by remember { mutableStateOf("") }
    var llmApiKey by remember { mutableStateOf("") }
    var desktopPort by remember { mutableIntStateOf(6080) }
    var desktopType by remember { mutableStateOf("WEB_DESKTOP") }
    var desktopUrl by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("Workstation") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Default.Computer,
                            tint = SparkBlue,
                            size = 36.dp,
                            iconSize = 19.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Add Tailscale Computer",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_add_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Computer Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Computer Name") },
                    placeholder = { Text("e.g. Ubuntu AI Rig, MacBook Pro") },
                    modifier = Modifier.fillMaxWidth().testTag("input_node_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tailscale IP or MagicDNS
                OutlinedTextField(
                    value = ip,
                    onValueChange = {
                        ip = it
                        if (llmBaseUrl.isBlank() || llmBaseUrl.startsWith("http://100.")) {
                            llmBaseUrl = "http://${it.trim()}:$llmPort"
                        }
                    },
                    label = { Text("Tailscale IP or MagicDNS") },
                    placeholder = { Text("100.x.y.z or host.tailnet.ts.net") },
                    modifier = Modifier.fillMaxWidth().testTag("input_node_ip"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // OS Selector
                Text("Operating System", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OsType.entries.forEach { os ->
                        val isSelected = selectedOs == os
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) SparkBlue.copy(alpha = 0.16f)
                                    else DarkSurfaceVariant.copy(alpha = 0.6f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) SparkBlue.copy(alpha = 0.45f) else DarkBorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedOs = os
                                    when (os) {
                                        OsType.LINUX -> {
                                            sshUser = "ubuntu"
                                            llmPort = 11434
                                            llmType = "OLLAMA"
                                            desktopPort = 6080
                                            desktopType = "WEB_DESKTOP"
                                        }
                                        OsType.MACOS -> {
                                            sshUser = "developer"
                                            llmPort = 1234
                                            llmType = "OPENAI_COMPATIBLE"
                                            desktopPort = 5900
                                            desktopType = "VNC"
                                        }
                                        OsType.WINDOWS -> {
                                            sshUser = "admin"
                                            llmPort = 11434
                                            llmType = "OLLAMA"
                                            desktopPort = 3389
                                            desktopType = "RDP"
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = os.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) SparkBlue else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SSH Configuration
                Text("SSH Credentials", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sshUser,
                        onValueChange = { sshUser = it },
                        label = { Text("SSH User") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sshPort.toString(),
                        onValueChange = { sshPort = it.toIntOrNull() ?: 22 },
                        label = { Text("SSH Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // LLM Configuration
                Text("LLM API Configuration", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = llmType == "OLLAMA",
                        onClick = {
                            llmType = "OLLAMA"
                            llmPort = 11434
                            llmBaseUrl = "http://${ip.trim()}:11434"
                        },
                        label = { Text("Ollama (:11434)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = llmType == "OPENAI_COMPATIBLE",
                        onClick = {
                            llmType = "OPENAI_COMPATIBLE"
                            llmPort = 1234
                            llmBaseUrl = "http://${ip.trim()}:1234/v1"
                        },
                        label = { Text("OpenAI / LM Studio") },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = llmBaseUrl,
                    onValueChange = { llmBaseUrl = it },
                    label = { Text("LLM Base URL") },
                    placeholder = { Text("http://100.x.y.z:11434 or /v1") },
                    modifier = Modifier.fillMaxWidth().testTag("input_node_llm_base_url"),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = llmApiKey,
                    onValueChange = { llmApiKey = it },
                    label = { Text("API Key / Bearer Token (Optional)") },
                    placeholder = { Text("Token if protected") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Remote Desktop Configuration
                Text("Remote Desktop", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = desktopType,
                        onValueChange = { desktopType = it },
                        label = { Text("Type (WEB/RDP/VNC)") },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desktopPort.toString(),
                        onValueChange = { desktopPort = it.toIntOrNull() ?: 6080 },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags / Notes") },
                    placeholder = { Text("e.g. RTX 4090, Home Lab") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkBorder,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val effectiveBase = if (llmBaseUrl.isNotBlank()) llmBaseUrl else "http://${ip.trim()}:$llmPort"
                            onSave(
                                name.ifBlank { "Tailscale Computer" },
                                ip.ifBlank { "100.64.0.2" },
                                selectedOs.name,
                                sshPort,
                                sshUser,
                                llmPort,
                                llmType,
                                effectiveBase,
                                llmApiKey,
                                desktopPort,
                                desktopType,
                                desktopUrl,
                                tags
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                        modifier = Modifier.testTag("save_node_btn")
                    ) {
                        Text("Connect Computer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
