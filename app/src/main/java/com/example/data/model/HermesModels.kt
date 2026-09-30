package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Message in a Hermes Agent conversation.
 * Supports tool calls, computer action execution, and thinking tokens.
 */
@Entity(tableName = "hermes_messages")
data class HermesMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val nodeId: Long,
    val sessionId: String = "default",
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val thinkingContent: String? = null,
    val toolCallsJson: String = "", // Serialized HermesToolCall list
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class HermesToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "execute_bash", "mcp__filesystem__list_directory", "read_file"
    val arguments: String = "{}", // JSON arguments string
    val result: String? = null, // Tool execution output from host computer
    val status: HermesToolStatus = HermesToolStatus.RUNNING,
    val durationMs: Long? = null
)

enum class HermesToolStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR
}

data class HermesSession(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis()
)

data class HermesHealthStatus(
    val isReachable: Boolean,
    val latencyMs: Long = -1,
    val serverVersion: String? = null,
    val modelName: String = "hermes-agent",
    val toolsCount: Int = 0,
    val activeSessionsCount: Int = 0,
    val errorMessage: String? = null
)

data class HermesMcpTool(
    val name: String,
    val description: String,
    val category: String = "Host Tool", // "MCP Server", "Bash & System", "Filesystem"
    val serverName: String? = null
)
