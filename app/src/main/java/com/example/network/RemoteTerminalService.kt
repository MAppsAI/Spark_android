package com.example.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.TailNode
import com.example.data.model.TerminalPreset
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.TimeUnit

class RemoteTerminalService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getPresetCommands(osType: String): List<TerminalPreset> {
        return listOf(
            TerminalPreset("System Uptime", "uptime", "System"),
            TerminalPreset("Tailscale Status", "tailscale status", "Tailscale"),
            TerminalPreset("Disk Usage", "df -h /", "System"),
            TerminalPreset("Memory Stats", "free -h", "System"),
            TerminalPreset("GPU Status", "nvidia-smi", "AI / GPU"),
            TerminalPreset("Ollama Models", "ollama list", "AI / GPU"),
            TerminalPreset("Ollama PS", "ollama ps", "AI / GPU"),
            TerminalPreset("Docker Containers", "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'", "Docker"),
            TerminalPreset("Listen Ports", "ss -tulpn | grep -E '(11434|22|6080|8080|7681)'", "Network"),
            TerminalPreset("OS Details", "uname -a", "System"),
            TerminalPreset("Whoami", "whoami", "System")
        )
    }

    fun getSshCommand(node: TailNode): String {
        val user = node.sshUser.ifBlank { "user" }
        return "ssh $user@${node.tailscaleIp} -p ${node.sshPort}"
    }

    fun getTailscaleSshCommand(node: TailNode): String {
        val user = node.sshUser.ifBlank { "user" }
        return "tailscale ssh $user@${node.tailscaleIp}"
    }

    /**
     * Prepares launching Termux:
     * 1. Copies the exact SSH command to system clipboard so user can instantly paste and connect.
     * 2. Returns whether Termux app is installed and the launch intent.
     */
    fun prepareTermuxLaunch(context: Context, node: TailNode): TermuxLaunchResult {
        val sshCommand = getSshCommand(node)

        // Copy command to clipboard automatically
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SSH Command", sshCommand)
        clipboard.setPrimaryClip(clip)

        val packageManager = context.packageManager
        val termuxIntent = packageManager.getLaunchIntentForPackage("com.termux")

        return if (termuxIntent != null) {
            TermuxLaunchResult(
                isTermuxInstalled = true,
                sshCommand = sshCommand,
                intent = termuxIntent
            )
        } else {
            // Termux not installed
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases"))
            TermuxLaunchResult(
                isTermuxInstalled = false,
                sshCommand = sshCommand,
                intent = downloadIntent
            )
        }
    }

    /**
     * Executes command directly against the remote node via native SSH (port 22).
     * Connects with password, key, or Tailscale SSH.
     * Streams output chunks live via onOutputChunk callback.
     */
    suspend fun executeCommand(
        node: TailNode,
        command: String,
        passwordOverride: String? = null,
        onOutputChunk: ((String) -> Unit)? = null
    ): TerminalExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext TerminalExecutionResult(output = "", exitCode = 0)
        }

        // 1. Primary: Direct Native SSH execution
        try {
            val jsch = JSch()

            if (node.sshPrivateKey.isNotBlank()) {
                try {
                    jsch.addIdentity("tailnode_key", node.sshPrivateKey.toByteArray(), null, null)
                } catch (_: Exception) {}
            }

            val session: Session = jsch.getSession(node.sshUser.ifBlank { "root" }, node.tailscaleIp, node.sshPort)

            val passwordToUse = passwordOverride ?: node.sshPassword
            if (passwordToUse.isNotBlank()) {
                session.setPassword(passwordToUse)
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "publickey,keyboard-interactive,password")
                put("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256")
            }
            session.setConfig(config)
            session.timeout = 8000 // 8s connect timeout

            session.connect()

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(trimmed)
            channel.setErrStream(null)

            val inStream: InputStream = channel.inputStream
            val errStream: InputStream = channel.errStream

            channel.connect(8000)

            val buffer = ByteArray(1024)
            val sb = StringBuilder()
            val startTime = System.currentTimeMillis()

            while (true) {
                var chunkRead = false
                while (inStream.available() > 0) {
                    val read = inStream.read(buffer, 0, 1024)
                    if (read < 0) break
                    val chunk = String(buffer, 0, read)
                    sb.append(chunk)
                    onOutputChunk?.invoke(chunk)
                    chunkRead = true
                }
                while (errStream.available() > 0) {
                    val read = errStream.read(buffer, 0, 1024)
                    if (read < 0) break
                    val chunk = String(buffer, 0, read)
                    sb.append(chunk)
                    onOutputChunk?.invoke(chunk)
                    chunkRead = true
                }
                if (channel.isClosed) {
                    if (inStream.available() > 0 || errStream.available() > 0) continue
                    break
                }
                if (System.currentTimeMillis() - startTime > 30000) {
                    val timeoutMsg = "\n[Process timed out after 30 seconds]"
                    sb.append(timeoutMsg)
                    onOutputChunk?.invoke(timeoutMsg)
                    break
                }
                Thread.sleep(if (chunkRead) 10 else 30)
            }

            val exitCode = channel.exitStatus
            channel.disconnect()
            session.disconnect()

            val outputText = sb.toString().trimEnd()
            return@withContext TerminalExecutionResult(
                output = outputText.ifBlank { "(Command executed successfully with no output)" },
                exitCode = exitCode
            )
        } catch (e: JSchException) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("Auth fail", ignoreCase = true) || msg.contains("USERAUTH fail", ignoreCase = true)) {
                return@withContext TerminalExecutionResult(
                    output = """
[SSH Authentication Required]
Failed to authenticate as '${node.sshUser}' on ${node.tailscaleIp}:${node.sshPort}.

Tap 'Enter SSH Password' below to authenticate, or configure your password in Computer Settings.
                    """.trimIndent(),
                    exitCode = 1,
                    requiresPassword = true
                )
            }

            return@withContext tryHttpBridgeFallback(node, trimmed, e.localizedMessage ?: "SSH Connection Failed")
        } catch (e: Exception) {
            return@withContext tryHttpBridgeFallback(node, trimmed, e.localizedMessage ?: "SSH Error")
        }
    }

    private fun tryHttpBridgeFallback(node: TailNode, command: String, sshError: String): TerminalExecutionResult {
        val bridgeUrl = "http://${node.tailscaleIp}:${node.fileServerPort}/api/exec"
        try {
            val json = JSONObject().apply {
                put("command", command)
                put("user", node.sshUser)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(bridgeUrl).post(body).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: ""
                    val jsonResp = JSONObject(respBody)
                    val output = jsonResp.optString("output", "")
                    val exitCode = jsonResp.optInt("exitCode", 0)
                    return TerminalExecutionResult(
                        output = output.ifBlank { "(Executed via bridge with no output)" },
                        exitCode = exitCode
                    )
                }
            }
        } catch (_: Exception) {}

        return TerminalExecutionResult(
            output = """
⚠️ SSH Connection to ${node.tailscaleIp}:${node.sshPort} failed ($sshError).

Tip: You can tap 'Open in Termux' in the top bar to launch a full interactive PTY session with nano, htop, and tab-completion directly.
            """.trimIndent(),
            exitCode = -1
        )
    }
}

data class TerminalExecutionResult(
    val output: String,
    val exitCode: Int,
    val requiresPassword: Boolean = false
)

data class TermuxLaunchResult(
    val isTermuxInstalled: Boolean,
    val sshCommand: String,
    val intent: Intent
)
