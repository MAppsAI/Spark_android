package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tail_nodes")
data class TailNode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val tailscaleIp: String,
    val osType: String = "LINUX", // LINUX, MACOS, WINDOWS
    val sshPort: Int = 22,
    val sshUser: String = "admin",
    val sshPassword: String = "", // Optional SSH password
    val sshPrivateKey: String = "", // Optional private key
    val llmPort: Int = 11434, // Ollama default
    val llmType: String = "OLLAMA", // OLLAMA, OPENAI_COMPATIBLE
    val llmDefaultModel: String = "llama3.2:latest",
    val llmBaseUrl: String = "", // Custom Base URL e.g. "http://100.82.14.92:11434" or "http://100.82.14.92:1234/v1"
    val llmApiKey: String = "", // Optional Bearer token / API key
    val remoteDesktopPort: Int = 6080, // Web-based noVNC/Guacamole (6080) or RDP (3389)
    val remoteDesktopType: String = "WEB_DESKTOP", // WEB_DESKTOP, RDP, VNC
    val remoteDesktopUrl: String = "", // Custom web desktop URL e.g. "http://100.82.14.92:6080/vnc.html"
    val fileServerPort: Int = 8080,
    val fileServerUrl: String = "", // Custom file server URL
    val webTerminalPort: Int = 7681, // e.g. ttyd port
    val hermesPort: Int = 8642, // Hermes Agent Desktop Gateway default port
    val hermesDashboardPort: Int = 9119, // Hermes WebUI / Dashboard port
    val hermesApiKey: String = "", // API_SERVER_KEY from ~/.hermes/.env
    val hermesBaseUrl: String = "", // Custom Gateway URL e.g. "http://100.82.14.92:8642"
    val hermesModel: String = "hermes-agent",
    val tags: String = "Workstation",
    val isFavorite: Boolean = false,
    val lastConnected: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the active Hermes Agent Gateway URL.
     * Default port 8642 as used by Hermes Agent Desktop Gateway.
     */
    fun getEffectiveHermesGatewayUrl(): String {
        val custom = hermesBaseUrl.trim()
        if (custom.isNotBlank()) return custom.trimEnd('/')
        val cleanIp = tailscaleIp.trim()
        val port = if (hermesPort > 0) hermesPort else 8642
        return "http://$cleanIp:$port"
    }

    /**
     * Resolves the Hermes WebUI Dashboard URL (default port 9119).
     */
    fun getEffectiveHermesDashboardUrl(): String {
        val cleanIp = tailscaleIp.trim()
        val port = if (hermesDashboardPort > 0) hermesDashboardPort else 9119
        return "http://$cleanIp:$port"
    }

    /**
     * Resolves the active Base URL for LLM queries.
     * If user explicitly set [llmBaseUrl], use it directly.
     * Otherwise construct from tailscaleIp and llmPort.
     */
    fun getEffectiveLlmBaseUrl(): String {
        val custom = llmBaseUrl.trim()
        if (custom.isNotBlank()) {
            return custom.trimEnd('/')
        }
        val cleanIp = tailscaleIp.trim()
        val port = if (llmPort > 0) llmPort else 11434
        return "http://$cleanIp:$port"
    }

    fun getEffectiveDesktopUrl(): String {
        val custom = remoteDesktopUrl.trim()
        if (custom.isNotBlank()) return custom
        return "http://${tailscaleIp.trim()}:$remoteDesktopPort"
    }

    fun getEffectiveFileServerUrl(): String {
        val custom = fileServerUrl.trim()
        if (custom.isNotBlank()) return custom.trimEnd('/')
        return "http://${tailscaleIp.trim()}:$fileServerPort"
    }
}

enum class OsType(val displayName: String) {
    LINUX("Linux"),
    MACOS("macOS"),
    WINDOWS("Windows")
}

data class NodeStatus(
    val isOnline: Boolean = false,
    val latencyMs: Long = -1,
    val sshReachable: Boolean = false,
    val llmReachable: Boolean = false,
    val fileServerReachable: Boolean = false,
    val desktopReachable: Boolean = false,
    val lastChecked: Long = 0,
    val diagnosticSummary: String = ""
)

data class SystemTelemetry(
    val nodeName: String = "",
    val tailscaleIp: String = "",
    val isOnline: Boolean = false,
    val latencyMs: Long = -1,
    val sshPort: Int = 22,
    val sshUp: Boolean = false,
    val llmPort: Int = 11434,
    val llmUp: Boolean = false,
    val llmBaseUrl: String = "",
    val desktopPort: Int = 6080,
    val desktopUp: Boolean = false,
    val filePort: Int = 8080,
    val fileUp: Boolean = false,
    val lastCheckTime: Long = 0
)

