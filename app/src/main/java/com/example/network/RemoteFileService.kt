package com.example.network

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.data.model.RemoteFileItem
import com.example.data.model.TailNode
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.Vector
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class RemoteFileService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    /**
     * Lists files from the remote node.
     * Primary mechanism: Native SFTP over SSH (Port 22) - Fast, secure, no separate server needed.
     * Secondary fallback: HTTP/FileBrowser if SFTP is unavailable.
     */
    suspend fun listFiles(node: TailNode, currentPath: String): FileListingResult = withContext(Dispatchers.IO) {
        val targetPath = currentPath.ifBlank { "." }

        // 1. Primary: SFTP via SSH (Port 22 or custom sshPort)
        try {
            val (session, channel) = openSftpChannel(node)
            try {
                // Determine remote working directory
                val pwd = try { channel.pwd() } catch (_: Exception) { "/" }
                val effectivePath = if (targetPath == "." || targetPath.isBlank()) pwd else targetPath

                val vector = channel.ls(effectivePath) as Vector<*>
                val items = mutableListOf<RemoteFileItem>()

                for (obj in vector) {
                    val entry = obj as? ChannelSftp.LsEntry ?: continue
                    val filename = entry.filename
                    if (filename == "." || filename == "..") continue

                    val isDir = entry.attrs.isDir
                    val size = if (isDir) 0L else entry.attrs.size
                    val mTimeSeconds = entry.attrs.mTime.toLong()
                    val dateStr = if (mTimeSeconds > 0) {
                        dateFormat.format(Date(mTimeSeconds * 1000L))
                    } else "Recent"

                    val ext = if (!isDir && filename.contains(".")) {
                        filename.substringAfterLast(".").lowercase()
                    } else ""

                    val fullPath = if (effectivePath.endsWith("/")) {
                        "$effectivePath$filename"
                    } else {
                        "$effectivePath/$filename"
                    }

                    items.add(
                        RemoteFileItem(
                            name = filename,
                            path = fullPath,
                            isDirectory = isDir,
                            size = size,
                            modifiedDate = dateStr,
                            extension = ext,
                            permissions = entry.attrs.permissionsString ?: "rw-r--r--"
                        )
                    )
                }

                // Sort directories first, then alphabetical by name
                val sorted = items.sortedWith(
                    compareBy<RemoteFileItem> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )

                return@withContext FileListingResult(
                    items = sorted,
                    isServerReachable = true,
                    serverType = "SFTP (Port ${node.sshPort})",
                    resolvedPath = effectivePath
                )
            } finally {
                channel.disconnect()
                session.disconnect()
            }
        } catch (sftpEx: Exception) {
            val sftpError = sftpEx.localizedMessage ?: sftpEx.message ?: "SFTP connection error"

            // 2. Fallback: Try FileBrowser REST API (HTTP)
            val baseUrl = node.getEffectiveFileServerUrl()
            val cleanPath = if (targetPath.startsWith("/")) targetPath else "/$targetPath"

            try {
                val fileBrowserUrl = "$baseUrl/api/resources$cleanPath"
                val request = Request.Builder().url(fileBrowserUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val itemsJson = json.optJSONArray("items") ?: JSONArray()
                            val items = mutableListOf<RemoteFileItem>()
                            for (i in 0 until itemsJson.length()) {
                                val obj = itemsJson.getJSONObject(i)
                                val name = obj.optString("name")
                                val isDir = obj.optBoolean("isDir")
                                val size = obj.optLong("size", 0)
                                val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
                                items.add(
                                    RemoteFileItem(
                                        name = name,
                                        path = "$cleanPath/$name".replace("//", "/"),
                                        isDirectory = isDir,
                                        size = size,
                                        modifiedDate = obj.optString("modified", "Recent"),
                                        extension = ext
                                    )
                                )
                            }
                            return@withContext FileListingResult(
                                items = items,
                                isServerReachable = true,
                                serverType = "FileBrowser (HTTP)",
                                resolvedPath = cleanPath
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Fallback: Try HTTP directory listing (e.g., Python / Nginx)
            try {
                val standardUrl = "$baseUrl$cleanPath"
                val request = Request.Builder().url(standardUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val items = parseHtmlDirectoryListing(html, cleanPath)
                        if (items.isNotEmpty()) {
                            return@withContext FileListingResult(
                                items = items,
                                isServerReachable = true,
                                serverType = "HTTP Server",
                                resolvedPath = cleanPath
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            // If all failed, provide helpful SFTP error with node diagnostics
            FileListingResult(
                items = emptyList(),
                isServerReachable = false,
                errorMessage = "SFTP connection to ${node.tailscaleIp}:${node.sshPort} failed ($sftpError). Please verify SSH credentials in Node Settings."
            )
        }
    }

    /**
     * Uploads a file stream directly to the remote node via native SFTP.
     */
    suspend fun uploadFile(
        node: TailNode,
        remoteDirectory: String,
        fileName: String,
        inputStream: InputStream,
        fileSize: Long
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val (session, channel) = openSftpChannel(node)
            try {
                val cleanDir = remoteDirectory.ifBlank { "." }
                val targetPath = if (cleanDir.endsWith("/")) "$cleanDir$fileName" else "$cleanDir/$fileName"

                val startTime = System.currentTimeMillis()
                channel.put(inputStream, targetPath, ChannelSftp.OVERWRITE)
                val durationSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
                val speedMBs = (fileSize / (1024.0 * 1024.0)) / durationSec
                val speedFormatted = String.format(Locale.US, "%.1f MB/s", speedMBs)

                UploadResult(
                    isSuccess = true,
                    remotePath = targetPath,
                    bytesUploaded = fileSize,
                    transferSpeed = speedFormatted
                )
            } finally {
                channel.disconnect()
                session.disconnect()
            }
        } catch (e: Exception) {
            UploadResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.message ?: "SFTP upload failed"
            )
        }
    }

    /**
     * Downloads a file from the remote node via SFTP (or HTTP fallback) and saves it
     * directly into the public Android Downloads collection (MediaStore.Downloads)
     * so it appears immediately in the device's system Downloads folder and file manager.
     */
    suspend fun downloadFileToDevice(
        node: TailNode,
        item: RemoteFileItem,
        context: Context
    ): DownloadResult = withContext(Dispatchers.IO) {
        val extension = item.name.substringAfterLast('.', "").lowercase()
        val mimeType = if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }

        val startTime = System.currentTimeMillis()

        // 1. Primary: SFTP download
        try {
            val (session, channel) = openSftpChannel(node)
            try {
                return@withContext saveToPublicDownloads(
                    context = context,
                    fileName = item.name,
                    mimeType = mimeType,
                    startTime = startTime
                ) { outputStream ->
                    channel.get(item.path, outputStream)
                }
            } finally {
                channel.disconnect()
                session.disconnect()
            }
        } catch (sftpEx: Exception) {
            // 2. Secondary fallback: HTTP download if file server is active
            val baseUrl = node.getEffectiveFileServerUrl()
            val cleanPath = if (item.path.startsWith("/")) item.path else "/${item.path}"
            val fileUrl = "$baseUrl$cleanPath"

            val dlClient = httpClient.newBuilder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            try {
                val request = Request.Builder().url(fileUrl).get().build()
                dlClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult(
                            isSuccess = false,
                            errorMessage = "SFTP failed (${sftpEx.localizedMessage}) and HTTP returned ${response.code}"
                        )
                    }

                    val body = response.body ?: return@withContext DownloadResult(
                        isSuccess = false,
                        errorMessage = "Empty response body from file server"
                    )

                    return@withContext saveToPublicDownloads(
                        context = context,
                        fileName = item.name,
                        mimeType = mimeType,
                        startTime = startTime
                    ) { outputStream ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16384)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                outputStream.write(buffer, 0, read)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                return@withContext DownloadResult(
                    isSuccess = false,
                    errorMessage = "SFTP error: ${sftpEx.localizedMessage ?: "connection failed"}. HTTP fallback error: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun saveToPublicDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        startTime: Long,
        writer: (OutputStream) -> Unit
    ): DownloadResult {
        var bytesWritten = 0L
        val displayPath = "Downloads/$fileName"

        // Strategy A: Android 10+ (API 29+) MediaStore.Downloads (Public, visible in system Downloads & Files app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = try {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } catch (_: Exception) {
                null
            }

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { os ->
                        val countingOs = object : OutputStream() {
                            override fun write(b: Int) {
                                os.write(b)
                                bytesWritten++
                            }
                            override fun write(b: ByteArray, off: Int, len: Int) {
                                os.write(b, off, len)
                                bytesWritten += len
                            }
                            override fun flush() = os.flush()
                            override fun close() = os.close()
                        }
                        writer(countingOs)
                    } ?: throw IOException("Could not open stream for $uri")

                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    val durationSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
                    val speedMBs = (bytesWritten / (1024.0 * 1024.0)) / durationSec
                    val speedFormatted = String.format(Locale.US, "%.1f MB/s", speedMBs)

                    return DownloadResult(
                        isSuccess = true,
                        savedPath = displayPath,
                        savedUri = uri.toString(),
                        bytesDownloaded = bytesWritten,
                        transferSpeed = speedFormatted
                    )
                } catch (e: Exception) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    // If MediaStore write fails, fall through to Strategy B
                }
            }
        }

        // Strategy B: Public File direct write with MediaScannerConnection indexing
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!publicDownloads.exists()) publicDownloads.mkdirs()
            val targetFile = File(publicDownloads, fileName)

            FileOutputStream(targetFile).use { fos ->
                val countingOs = object : OutputStream() {
                    override fun write(b: Int) {
                        fos.write(b)
                        bytesWritten++
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        fos.write(b, off, len)
                        bytesWritten += len
                    }
                    override fun flush() = fos.flush()
                    override fun close() = fos.close()
                }
                writer(countingOs)
            }

            // Immediately notify MediaScanner so it is visible in Downloads & Files app
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)

            val durationSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
            val speedMBs = (bytesWritten / (1024.0 * 1024.0)) / durationSec
            val speedFormatted = String.format(Locale.US, "%.1f MB/s", speedMBs)

            return DownloadResult(
                isSuccess = true,
                savedPath = displayPath,
                savedUri = Uri.fromFile(targetFile).toString(),
                bytesDownloaded = bytesWritten,
                transferSpeed = speedFormatted
            )
        } catch (_: Exception) {
            // Strategy C: App external files directory with scanner fallback
            val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!appDownloads.exists()) appDownloads.mkdirs()
            val targetFile = File(appDownloads, fileName)

            FileOutputStream(targetFile).use { fos ->
                val countingOs = object : OutputStream() {
                    override fun write(b: Int) {
                        fos.write(b)
                        bytesWritten++
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        fos.write(b, off, len)
                        bytesWritten += len
                    }
                    override fun flush() = fos.flush()
                    override fun close() = fos.close()
                }
                writer(countingOs)
            }

            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)

            val durationSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
            val speedMBs = (bytesWritten / (1024.0 * 1024.0)) / durationSec
            val speedFormatted = String.format(Locale.US, "%.1f MB/s", speedMBs)

            return DownloadResult(
                isSuccess = true,
                savedPath = targetFile.absolutePath,
                savedUri = Uri.fromFile(targetFile).toString(),
                bytesDownloaded = bytesWritten,
                transferSpeed = speedFormatted
            )
        }
    }

    /**
     * Reads text content of a remote file via SFTP with a 512KB preview cap.
     * Fast, lightweight, and completely eliminates CPU/network exhaustion.
     */
    suspend fun getFileContent(node: TailNode, item: RemoteFileItem): String = withContext(Dispatchers.IO) {
        // 1. Try SFTP preview
        try {
            val (session, channel) = openSftpChannel(node)
            try {
                channel.get(item.path).use { inStream ->
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    val maxBytes = 512 * 1024 // 512 KB preview cap

                    while (total < maxBytes) {
                        val read = inStream.read(buffer)
                        if (read == -1) break
                        baos.write(buffer, 0, read)
                        total += read
                    }

                    val content = baos.toString("UTF-8")
                    if (total >= maxBytes) {
                        return@withContext content + "\n\n... [Preview truncated at 512 KB. Tap 'Download' to get complete file]"
                    }
                    return@withContext content.ifBlank { "(Empty file)" }
                }
            } finally {
                channel.disconnect()
                session.disconnect()
            }
        } catch (_: Exception) {}

        // 2. HTTP preview fallback
        val baseUrl = node.getEffectiveFileServerUrl()
        val cleanPath = if (item.path.startsWith("/")) item.path else "/${item.path}"
        val fileUrl = "$baseUrl$cleanPath"

        val previewClient = httpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        try {
            val request = Request.Builder().url(fileUrl).get().build()
            previewClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "HTTP ${response.code} (${response.message})\nEndpoint: $fileUrl"
                }

                val byteStream = response.body?.byteStream() ?: return@withContext "(Empty content)"
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                val maxBytes = 256 * 1024

                while (total < maxBytes) {
                    val read = byteStream.read(buffer)
                    if (read == -1) break
                    baos.write(buffer, 0, read)
                    total += read
                }

                val content = baos.toString("UTF-8")
                if (total >= maxBytes) {
                    return@withContext content + "\n\n... [Preview truncated at 256 KB. Tap 'Download' to get complete file]"
                }
                return@withContext content.ifBlank { "(Empty file)" }
            }
        } catch (e: Exception) {
            val reason = e.localizedMessage ?: "Connection timed out"
            return@withContext "⚠️ Could not load preview from remote file (${item.path})\nReason: $reason\n\nYou can still download the file directly via SFTP."
        }
    }

    /**
     * Deletes a remote file or empty directory via SFTP.
     */
    suspend fun deleteFile(node: TailNode, item: RemoteFileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val (session, channel) = openSftpChannel(node)
            try {
                if (item.isDirectory) {
                    channel.rmdir(item.path)
                } else {
                    channel.rm(item.path)
                }
                true
            } finally {
                channel.disconnect()
                session.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates a new remote directory via SFTP.
     */
    suspend fun createDirectory(node: TailNode, parentPath: String, dirName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val (session, channel) = openSftpChannel(node)
            try {
                val cleanParent = parentPath.ifBlank { "." }
                val targetDir = if (cleanParent.endsWith("/")) "$cleanParent$dirName" else "$cleanParent/$dirName"
                channel.mkdir(targetDir)
                true
            } finally {
                channel.disconnect()
                session.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens an authenticated JSch Session and ChannelSftp connection.
     */
    private fun openSftpChannel(node: TailNode): Pair<Session, ChannelSftp> {
        val jsch = JSch()
        if (node.sshPrivateKey.isNotBlank()) {
            try {
                jsch.addIdentity("tailnode_sftp_key", node.sshPrivateKey.toByteArray(), null, null)
            } catch (_: Exception) {}
        }

        val session: Session = jsch.getSession(
            node.sshUser.ifBlank { "root" },
            node.tailscaleIp.trim(),
            node.sshPort
        )

        if (node.sshPassword.isNotBlank()) {
            session.setPassword(node.sshPassword)
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,keyboard-interactive,password")
            put("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256")
        }
        session.setConfig(config)
        session.timeout = 10000 // 10s connect timeout
        session.connect()

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(10000)

        return Pair(session, channel)
    }

    private fun parseHtmlDirectoryListing(html: String, parentPath: String): List<RemoteFileItem> {
        val items = mutableListOf<RemoteFileItem>()
        val pattern = Pattern.compile("<a\\s+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        while (matcher.find()) {
            val href = matcher.group(1)?.trim() ?: continue
            val label = matcher.group(2)?.trim()?.replace("<[^>]+>".toRegex(), "") ?: href
            if (href.startsWith("?") || href == "../" || href == "./" || label.contains("Parent Directory")) {
                continue
            }
            val isDir = href.endsWith("/")
            val cleanName = label.trimEnd('/')
            val ext = if (cleanName.contains(".")) cleanName.substringAfterLast(".").lowercase() else ""
            items.add(
                RemoteFileItem(
                    name = cleanName,
                    path = "$parentPath/$cleanName".replace("//", "/"),
                    isDirectory = isDir,
                    size = 0,
                    modifiedDate = "Remote",
                    extension = ext
                )
            )
        }
        return items
    }
}

data class FileListingResult(
    val items: List<RemoteFileItem>,
    val isServerReachable: Boolean,
    val serverType: String = "",
    val errorMessage: String? = null,
    val resolvedPath: String? = null
)

data class DownloadResult(
    val isSuccess: Boolean,
    val savedPath: String? = null,
    val savedUri: String? = null,
    val errorMessage: String? = null,
    val bytesDownloaded: Long = 0L,
    val transferSpeed: String = "SFTP Native"
)

data class UploadResult(
    val isSuccess: Boolean,
    val remotePath: String? = null,
    val errorMessage: String? = null,
    val bytesUploaded: Long = 0L,
    val transferSpeed: String = "SFTP Native"
)
