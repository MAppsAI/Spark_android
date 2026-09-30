package com.example.network

import com.example.data.model.NodeStatus
import com.example.data.model.TailNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TailscalePingService {

    suspend fun checkNodeReachability(node: TailNode): NodeStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val ip = node.tailscaleIp.trim()

        if (ip.isBlank()) {
            return@withContext NodeStatus(
                isOnline = false,
                latencyMs = -1,
                diagnosticSummary = "No IP configured"
            )
        }

        // Test SSH port (default 22)
        val sshUp = isPortOpen(ip, node.sshPort, 1000)

        // Test LLM port
        val llmPort = if (node.llmPort > 0) node.llmPort else 11434
        val llmUp = isPortOpen(ip, llmPort, 1000)

        // Test File server port
        val fileUp = if (node.fileServerPort > 0) isPortOpen(ip, node.fileServerPort, 1000) else false

        // Test Desktop port
        val desktopUp = if (node.remoteDesktopPort > 0) isPortOpen(ip, node.remoteDesktopPort, 1000) else false

        // Ping or probe web/DNS fallback if none of specific ports responded
        var generalProbeUp = false
        var latencyMs: Long = -1

        if (sshUp || llmUp || fileUp || desktopUp) {
            latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(4)
        } else {
            // Probe common ports 80, 443, 53
            generalProbeUp = isPortOpen(ip, 80, 800) || isPortOpen(ip, 443, 800)
            if (generalProbeUp) {
                latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(8)
            }
        }

        val isOnline = sshUp || llmUp || fileUp || desktopUp || generalProbeUp

        val openServices = mutableListOf<String>()
        if (sshUp) openServices.add("SSH :${node.sshPort}")
        if (llmUp) openServices.add("LLM :$llmPort")
        if (desktopUp) openServices.add("Desktop :${node.remoteDesktopPort}")
        if (fileUp) openServices.add("Files :${node.fileServerPort}")

        val summary = if (isOnline) {
            if (openServices.isNotEmpty()) {
                "Online • Reachable: ${openServices.joinToString(", ")}"
            } else {
                "Host pingable (${latencyMs}ms), but specific ports are closed"
            }
        } else {
            "Host unreachable on Tailscale. Verify WireGuard tunnel & firewall."
        }

        NodeStatus(
            isOnline = isOnline,
            latencyMs = latencyMs,
            sshReachable = sshUp,
            llmReachable = llmUp,
            fileServerReachable = fileUp,
            desktopReachable = desktopUp,
            lastChecked = System.currentTimeMillis(),
            diagnosticSummary = summary
        )
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        if (port <= 0 || port > 65535) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
