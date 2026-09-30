package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TailNode
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SparkBlue
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.TerminalAmber
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.TailNodeViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RemoteDesktopScreen(
    node: TailNode,
    viewModel: TailNodeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConnected by viewModel.desktopConnected.collectAsStateWithLifecycle()

    var activeTab by remember {
        mutableIntStateOf(if (node.remoteDesktopType == "WEB_DESKTOP" && node.remoteDesktopPort == 6080) 0 else 0)
    } // 0 = Direct RDP/VNC Client, 1 = Web Stream (noVNC/Guacamole)

    var webError by remember { mutableStateOf<String?>(null) }
    var isLoadingWeb by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val isWindows = node.osType.uppercase() == "WINDOWS"
    val isMac = node.osType.uppercase() == "MACOS"
    val defaultClientPort = if (isWindows) 3389 else if (isMac) 5900 else node.remoteDesktopPort
    val effectiveDesktopUrl = node.getEffectiveDesktopUrl()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas)
    ) {
        // Desktop Header Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Remote Desktop • ${node.name}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isWindows) "Windows RDP (:3389)" else if (isMac) "macOS VNC (:5900)" else "${node.remoteDesktopType} (:$defaultClientPort)",
                        fontSize = 10.sp,
                        color = TerminalGreen
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isSettingsOpen = true },
                    modifier = Modifier.size(34.dp).testTag("desktop_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Desktop Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Mode Selector: Direct App vs In-App Web
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = DarkSurfaceVariant,
            contentColor = CyberCyan
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("1-Tap RDP / VNC (Direct)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Web Stream in App", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        if (activeTab == 0) {
            // TAB 1: DIRECT RDP / VNC (THE REAL, RECOMMENDED WAY ON ANDROID)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Host Address & Connection Hero Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isWindows) "Microsoft Remote Desktop (RDP)" else if (isMac) "macOS Screen Sharing (VNC)" else "Direct Remote Desktop (${node.remoteDesktopType})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Direct point-to-point WireGuard stream over Tailscale",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TerminalGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Ready", fontSize = 10.sp, color = TerminalGreen, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Connection Info Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkCanvas)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("PC Host Address", fontSize = 10.sp, color = TextMuted)
                                Text(
                                    text = "${node.tailscaleIp}:$defaultClientPort",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("PC Host", "${node.tailscaleIp}:$defaultClientPort"))
                                    Toast.makeText(context, "Copied ${node.tailscaleIp}:$defaultClientPort", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = TextPrimary),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Big Primary Action: Launch Remote Desktop Session
                        Button(
                            onClick = {
                                launchDirectDesktopClient(context, node, defaultClientPort)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("launch_desktop_session_btn")
                        ) {
                            Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Launch Remote Desktop Session",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step-by-Step OS Guide
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("How to Connect in 2 Steps:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isWindows) {
                            GuideStep(
                                number = "1",
                                title = "Enable Remote Desktop on Windows",
                                desc = "On your PC, open Settings > System > Remote Desktop and switch it ON."
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GuideStep(
                                number = "2",
                                title = "Tap Launch Remote Desktop Session",
                                desc = "It opens Microsoft Remote Desktop directly to ${node.tailscaleIp}. Enter your Windows password to view your full desktop!"
                            )
                        } else if (isMac) {
                            GuideStep(
                                number = "1",
                                title = "Enable Screen Sharing on Mac",
                                desc = "On your Mac, open System Settings > General > Sharing and switch Screen Sharing ON."
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GuideStep(
                                number = "2",
                                title = "Tap Launch Remote Desktop Session",
                                desc = "Opens in bVNC or RealVNC with address ${node.tailscaleIp}:5900. Log in with your Mac user."
                            )
                        } else {
                            GuideStep(
                                number = "1",
                                title = "Enable RDP/VNC on Linux",
                                desc = "Run: sudo apt install xrdp && sudo systemctl enable --now xrdp"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GuideStep(
                                number = "2",
                                title = "Tap Launch Remote Desktop Session",
                                desc = "Connects directly via port $defaultClientPort with GPU acceleration."
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recommended Android Client Apps Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Recommended Free Client Apps:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.microsoft.rdc.androidx"))
                                    try { context.startActivity(intent) } catch (_: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.microsoft.rdc.androidx")))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Microsoft RD (RDP)", fontSize = 11.sp, color = CyberCyan)
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.iiordanov.freebVNC"))
                                    try { context.startActivity(intent) } catch (_: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.iiordanov.freebVNC")))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("bVNC (Free VNC)", fontSize = 11.sp, color = CyberCyan)
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 2: IN-APP WEB BROWSER STREAMING (noVNC / Guacamole)
            Column(modifier = Modifier.fillMaxSize()) {
                // Web Stream Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Web Stream URL", fontSize = 10.sp, color = TextMuted)
                        Text(effectiveDesktopUrl, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary, maxLines = 1)
                    }

                    Row {
                        Button(
                            onClick = {
                                webError = null
                                viewModel.toggleDesktopConnection()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) Color(0xFF991B1B) else CyberCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Stop Stream" else "Start In-App Stream",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (isConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { webViewInstance?.reload() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextSecondary)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF020617))
                ) {
                    if (!isConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Web-Based Desktop Streaming", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                text = "Use this mode if you have a web desktop service running on ${node.name} (e.g. noVNC container on port 6080, Apache Guacamole, or Kasm).",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { viewModel.toggleDesktopConnection() },
                                colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White)
                            ) {
                                Text("Connect to $effectiveDesktopUrl", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Don't have noVNC running? Switch to '1-Tap RDP / VNC' above to use native Windows RDP or Mac VNC directly!",
                                fontSize = 11.sp,
                                color = TerminalAmber,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        // Live WebView
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewInstance = this
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                isLoadingWeb = false
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?
                                            ) {
                                                super.onReceivedError(view, request, error)
                                                if (request?.isForMainFrame == true) {
                                                    isLoadingWeb = false
                                                    webError = error?.description?.toString() ?: "Cannot connect to $effectiveDesktopUrl"
                                                }
                                            }
                                        }
                                        isLoadingWeb = true
                                        loadUrl(effectiveDesktopUrl)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isLoadingWeb) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = CyberCyan)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Streaming from $effectiveDesktopUrl...", fontSize = 12.sp, color = TextPrimary)
                                    }
                                }
                            }

                            if (webError != null) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = TerminalAmber, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Web Desktop not responding on port ${node.remoteDesktopPort}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                        Text("Detail: $webError", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TerminalAmber, modifier = Modifier.padding(vertical = 6.dp))
                                        Text(
                                            "Unless you specifically set up noVNC or Guacamole, Windows uses native RDP (:3389) and macOS uses native VNC (:5900).",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        Button(
                                            onClick = { activeTab = 0 },
                                            colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White)
                                        ) {
                                            Text("Switch to 1-Tap RDP/VNC Client", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Desktop Settings Dialog
    if (isSettingsOpen) {
        DesktopSettingsDialog(
            node = node,
            onDismiss = { isSettingsOpen = false },
            onSave = { port, type, url ->
                val updated = node.copy(
                    remoteDesktopPort = port,
                    remoteDesktopType = type,
                    remoteDesktopUrl = url
                )
                viewModel.updateCurrentNode(updated)
                isSettingsOpen = false
            }
        )
    }
}

private fun launchDirectDesktopClient(context: Context, node: TailNode, port: Int) {
    val isWindows = node.osType.uppercase() == "WINDOWS"
    val isMac = node.osType.uppercase() == "MACOS"

    val uriString = if (isWindows) {
        "rdp://${node.tailscaleIp}:$port"
    } else {
        "vnc://${node.sshUser}@${node.tailscaleIp}:$port"
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))

    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // App not found, copy connection host and offer Google Play download
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PC Host", "${node.tailscaleIp}:$port"))

        if (isWindows) {
            Toast.makeText(context, "Copied ${node.tailscaleIp}:$port. Opening Microsoft Remote Desktop on Google Play...", Toast.LENGTH_LONG).show()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.microsoft.rdc.androidx")))
            } catch (_: Exception) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.microsoft.rdc.androidx")))
            }
        } else {
            Toast.makeText(context, "Copied ${node.tailscaleIp}:$port. Opening bVNC on Google Play...", Toast.LENGTH_LONG).show()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.iiordanov.freebVNC")))
            } catch (_: Exception) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.iiordanov.freebVNC")))
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(CyberCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun DesktopSettingsDialog(
    node: TailNode,
    onDismiss: () -> Unit,
    onSave: (port: Int, type: String, url: String) -> Unit
) {
    var portText by remember { mutableStateOf(node.remoteDesktopPort.toString()) }
    var selectedType by remember { mutableStateOf(node.remoteDesktopType) }
    var customUrl by remember { mutableStateOf(node.remoteDesktopUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Remote Desktop Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Protocol / Type", fontSize = 12.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("RDP", "VNC", "WEB_DESKTOP").forEach { type ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedType = type
                                    when (type) {
                                        "RDP" -> portText = "3389"
                                        "VNC" -> portText = "5900"
                                        "WEB_DESKTOP" -> portText = "6080"
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedType == type) MaterialTheme.colorScheme.primaryContainer else DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = type,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType == type) MaterialTheme.colorScheme.onPrimaryContainer else TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp).align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Port", fontSize = 12.sp, color = TextSecondary)
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Custom Web Stream URL (Optional)", fontSize = 12.sp, color = TextSecondary)
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    placeholder = { Text("e.g. http://${node.tailscaleIp}:6080/vnc.html") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = TextPrimary)) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 3389
                            onSave(port, selectedType, customUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
