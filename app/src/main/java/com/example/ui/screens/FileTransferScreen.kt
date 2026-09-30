package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RemoteFileItem
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
import java.util.Locale

@Composable
fun FileTransferScreen(
    node: TailNode,
    viewModel: TailNodeViewModel,
    modifier: Modifier = Modifier
) {
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val serverProtocol by viewModel.serverProtocol.collectAsStateWithLifecycle()
    val fileList by viewModel.fileList.collectAsStateWithLifecycle()
    val isFileLoading by viewModel.isFileLoading.collectAsStateWithLifecycle()
    val fileListingError by viewModel.fileListingError.collectAsStateWithLifecycle()
    val selectedFilePreview by viewModel.selectedFilePreview.collectAsStateWithLifecycle()
    val recentTransfers by viewModel.recentTransfers.collectAsStateWithLifecycle()
    val downloadingFile by viewModel.isDownloadingFile.collectAsStateWithLifecycle()
    val uploadingFile by viewModel.isUploadingFile.collectAsStateWithLifecycle()
    val lastDownloadedFile by viewModel.lastDownloadedFile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Remote Files (SFTP), 1 = Transfers Queue
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<RemoteFileItem?>(null) }

    // Native SFTP File Upload Picker (Direct to remote directory without separate server)
    val sftpUploadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Uploading file via SFTP...", Toast.LENGTH_SHORT).show()
            viewModel.uploadFileDirectly(uri, context) { success, msg ->
                Toast.makeText(context, msg, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
        }
    }

    // Optional Taildrop share intent
    val taildropPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.tailscale.ipn")
            }
            try {
                context.startActivity(shareIntent)
                viewModel.logFileTransfer("Taildrop Document", 0L, "UPLOAD (Taildrop)", "Taildrop")
                Toast.makeText(context, "Sending via Taildrop to ${node.name}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                val generalIntent = Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(generalIntent, "Send via Taildrop to ${node.name}"))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tab row: SFTP Remote Files vs Transfer Queue
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = DarkSurface,
            contentColor = CyberCyan
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("SFTP Files (${fileList.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Transfers History (${recentTransfers.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        if (activeSubTab == 0) {
            // Top Navigation & Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateUp() },
                        modifier = Modifier.size(36.dp).testTag("navigate_up_btn")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go Up", tint = CyberCyan, modifier = Modifier.size(20.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentPath,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = serverProtocol.ifBlank { "SFTP (Port ${node.sshPort})" },
                            fontSize = 10.sp,
                            color = TerminalGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // New Folder Button
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.size(36.dp).testTag("new_folder_btn")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = TextSecondary, modifier = Modifier.size(19.dp))
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { viewModel.refreshFiles() },
                        modifier = Modifier.size(36.dp).testTag("refresh_files_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }

                    // Native SFTP Upload Button
                    Button(
                        onClick = { sftpUploadPickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).testTag("sftp_upload_btn")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Active Upload Banner
                uploadingFile?.let { filename ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D2538))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Uploading $filename via SFTP...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan
                        )
                    }
                }
            }

            if (isFileLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyberCyan)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Querying via SFTP (port ${node.sshPort})...",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else if (fileList.isEmpty()) {
                // Connection or Empty state guidance
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (fileListingError != null) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (fileListingError != null) TerminalAmber else CyberCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (fileListingError != null) "SFTP Connection Error" else "Directory is Empty",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (fileListingError != null) {
                        Text(
                            text = fileListingError ?: "",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("SFTP Troubleshooting", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    "SFTP uses your existing SSH credentials. Make sure:\n" +
                                        "1. Node Settings has correct SSH user and password/key.\n" +
                                        "2. SSH daemon (port ${node.sshPort}) is running on ${node.name}.\n" +
                                        "3. Tailscale IP is reachable (${node.tailscaleIp}).",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                Button(
                                    onClick = { viewModel.refreshFiles() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry SFTP Connection", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No files found in $currentPath",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { sftpUploadPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload File to This Directory")
                        }
                    }
                }
            } else {
                // SFTP File Browser List
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(fileList) { item ->
                        RemoteFileRow(
                            item = item,
                            isDownloading = downloadingFile == item.name,
                            onClick = { viewModel.navigateToFolder(item) },
                            onDownload = {
                                Toast.makeText(context, "Downloading ${item.name}...", Toast.LENGTH_SHORT).show()
                                viewModel.downloadFileDirectly(item, context) { success, msg ->
                                    Toast.makeText(context, msg, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                }
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        } else {
            // Transfers History Tab
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Transfer History",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Native SFTP transfers (zero server needed)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Row {
                        // Quick SFTP Upload
                        Button(
                            onClick = { sftpUploadPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SFTP Upload", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Taildrop fallback
                        OutlinedButton(
                            onClick = { taildropPickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Taildrop", fontSize = 11.sp, color = CyberCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (recentTransfers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.example.ui.components.SparkEmptyState(
                            title = "No transfers yet",
                            subtitle = "Upload or download files over SFTP and they'll show up here.",
                            icon = Icons.Default.FolderOpen,
                            accent = com.example.ui.theme.SparkBlue,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(recentTransfers) { transfer ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.DarkBorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (transfer.direction.contains("UPLOAD")) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = if (transfer.direction.contains("UPLOAD")) CyberCyan else TerminalGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = transfer.fileName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(
                                            text = "${transfer.direction} • ${formatFileSize(transfer.fileSize)} • ${transfer.transferSpeed}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted
                                        )
                                    }
                                    if (transfer.direction.contains("DOWNLOAD")) {
                                        IconButton(
                                            onClick = { viewModel.openDownloadsFolder(context) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open in Downloads", tint = TerminalGreen, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = TerminalGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Floating Animated Download Completion Banner
    AnimatedVisibility(
        visible = lastDownloadedFile != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
    ) {
            lastDownloadedFile?.let { dlInfo ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF132F2B)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TerminalGreen.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TerminalGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = dlInfo.fileName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Saved in Downloads folder",
                                    fontSize = 11.sp,
                                    color = TerminalGreen
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    viewModel.openFile(context, dlInfo.savedUri, dlInfo.fileName)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen, contentColor = Color(0xFF003622)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { viewModel.clearLastDownloadedFile() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create Folder via SFTP", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    Text("In: $currentPath", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isNotBlank()) {
                            viewModel.createRemoteFolder(name) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showNewFolderDialog = false
                                    newFolderName = ""
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Remote ${if (item.isDirectory) "Folder" else "File"}?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    "Are you sure you want to permanently delete '${item.name}' from ${node.name} via SFTP?",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRemoteFile(item) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // File Preview Dialog (Enhanced for HTML, Text, and Code)
    selectedFilePreview?.let { (item, content) ->
        val isHtml = item.extension == "html" || item.extension == "htm" || item.name.endsWith(".html", ignoreCase = true)
        var viewMode by remember { mutableStateOf(if (isHtml) "RENDERED" else "SOURCE") }

        Dialog(onDismissRequest = { viewModel.dismissFilePreview() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier.fillMaxWidth().height(540.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                            Text(
                                text = "${formatFileSize(item.size)} • ${item.permissions} • ${item.extension.uppercase().ifBlank { "FILE" }}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy content button
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("File content", content))
                                    Toast.makeText(context, "Copied content to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(19.dp))
                            }

                            // Download file directly
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Downloading ${item.name} via SFTP...", Toast.LENGTH_SHORT).show()
                                    viewModel.downloadFileDirectly(item, context) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Download", tint = CyberCyan, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = { viewModel.dismissFilePreview() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }
                    }

                    // HTML Mode switcher tabs
                    if (isHtml) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = viewMode == "RENDERED",
                                onClick = { viewMode = "RENDERED" },
                                label = { Text("Rendered HTML (Safe)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = viewMode == "SOURCE",
                                onClick = { viewMode = "SOURCE" },
                                label = { Text("Source Code", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isHtml && viewMode == "RENDERED") {
                        // Render HTML directly from in-memory content using local WebView
                        // Zero network request to remote machine — cannot freeze network!
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webViewClient = WebViewClient()
                                        loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Text or Source code viewer with selectable and copyable text
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF030712))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1D5DB)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Download Bar at bottom of preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Path: ${item.path}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )

                        Button(
                            onClick = {
                                Toast.makeText(context, "Downloading ${item.name}...", Toast.LENGTH_SHORT).show()
                                viewModel.downloadFileDirectly(item, context) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SparkBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFileRow(
    item: RemoteFileItem,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (item.isDirectory) CyberCyan else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(
                text = if (item.isDirectory) "Directory • ${item.permissions}" else "${formatFileSize(item.size)} • ${item.permissions} • ${item.modifiedDate}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        if (!item.isDirectory) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).padding(2.dp),
                    strokeWidth = 2.dp,
                    color = CyberCyan
                )
            } else {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(36.dp).testTag("download_file_${item.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Download ${item.name}",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp).testTag("delete_file_${item.name}")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete ${item.name}",
                tint = TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
