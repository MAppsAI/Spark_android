package com.example.data.repository

import android.content.Context
import com.example.data.backup.NodeBackupManager
import com.example.data.db.ChatConversationDao
import com.example.data.db.ChatMessageDao
import com.example.data.db.FileTransferDao
import com.example.data.db.HermesMessageDao
import com.example.data.db.TailNodeDao
import com.example.data.db.TerminalHistoryDao
import com.example.data.model.ChatConversation
import com.example.data.model.ChatMessage
import com.example.data.model.FileTransfer
import com.example.data.model.HermesMessage
import com.example.data.model.TailNode
import com.example.data.model.TerminalHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class TailNodeRepository(
    private val tailNodeDao: TailNodeDao,
    private val chatMessageDao: ChatMessageDao,
    private val fileTransferDao: FileTransferDao,
    private val terminalHistoryDao: TerminalHistoryDao,
    private val hermesMessageDao: HermesMessageDao? = null,
    private val chatConversationDao: ChatConversationDao? = null,
    private val context: Context? = null
) {
    val allNodes: Flow<List<TailNode>> = tailNodeDao.getAllNodes()

    fun getNodeById(nodeId: Long): Flow<TailNode?> = tailNodeDao.getNodeById(nodeId)

    suspend fun getNodeByIdDirect(nodeId: Long): TailNode? = tailNodeDao.getNodeByIdDirect(nodeId)

    suspend fun insertNode(node: TailNode): Long {
        val id = tailNodeDao.insertNode(node)
        persistBackup()
        return id
    }

    suspend fun updateNode(node: TailNode) {
        tailNodeDao.updateNode(node)
        persistBackup()
    }

    suspend fun deleteNode(node: TailNode) {
        tailNodeDao.deleteNode(node)
        persistBackup()
    }

    suspend fun toggleFavorite(nodeId: Long) {
        tailNodeDao.toggleFavorite(nodeId)
        persistBackup()
    }

    suspend fun persistBackup() {
        context?.let { ctx ->
            try {
                val nodes = tailNodeDao.getAllNodesDirect()
                if (nodes.isNotEmpty()) {
                    NodeBackupManager.saveBackup(ctx, nodes)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun restoreFromBackup(): Boolean {
        val ctx = context ?: return false
        if (NodeBackupManager.hasBackup(ctx)) {
            val nodes = NodeBackupManager.getBackupNodes(ctx)
            if (nodes.isNotEmpty()) {
                nodes.forEach { tailNodeDao.insertNode(it.copy(id = 0)) }
                return true
            }
        }
        return false
    }

    suspend fun exportConfigJson(): String {
        val nodes = tailNodeDao.getAllNodesDirect()
        return NodeBackupManager.exportConfigJson(nodes)
    }

    suspend fun importConfigJson(jsonString: String): Int {
        val imported = NodeBackupManager.parseNodesFromJson(jsonString)
        if (imported.isNotEmpty()) {
            imported.forEach { tailNodeDao.insertNode(it.copy(id = 0)) }
            persistBackup()
            return imported.size
        }
        return 0
    }

    suspend fun updateLastConnected(nodeId: Long, timestamp: Long) =
        tailNodeDao.updateLastConnected(nodeId, timestamp)

    // Chat conversations & multi-thread
    val allConversations: Flow<List<ChatConversation>> =
        chatConversationDao?.getAllConversations() ?: emptyFlow()

    suspend fun getConversationById(id: String): ChatConversation? =
        chatConversationDao?.getConversationById(id)

    suspend fun insertConversation(conversation: ChatConversation) =
        chatConversationDao?.insertConversation(conversation)

    suspend fun updateConversationTitle(id: String, title: String) =
        chatConversationDao?.updateTitle(id, title)

    suspend fun updateConversationModel(id: String, model: String) =
        chatConversationDao?.updateLastModel(id, model)

    suspend fun deleteConversation(id: String) {
        chatConversationDao?.deleteConversation(id)
        chatMessageDao.clearMessagesForConversation(id)
    }

    // Chat messages
    fun getMessagesForNode(nodeId: Long): Flow<List<ChatMessage>> =
        chatMessageDao.getMessagesForNode(nodeId)

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessage>> =
        chatMessageDao.getMessagesForConversation(conversationId)

    suspend fun insertChatMessage(message: ChatMessage): Long =
        chatMessageDao.insertMessage(message)

    suspend fun clearMessagesForNode(nodeId: Long) =
        chatMessageDao.clearMessagesForNode(nodeId)

    suspend fun clearMessagesForConversation(conversationId: String) =
        chatMessageDao.clearMessagesForConversation(conversationId)

    // File transfers
    fun getTransfersForNode(nodeId: Long): Flow<List<FileTransfer>> =
        fileTransferDao.getTransfersForNode(nodeId)

    suspend fun insertFileTransfer(transfer: FileTransfer): Long =
        fileTransferDao.insertTransfer(transfer)

    // Terminal history
    fun getTerminalHistory(nodeId: Long): Flow<List<TerminalHistory>> =
        terminalHistoryDao.getHistoryForNode(nodeId)

    suspend fun insertTerminalHistory(history: TerminalHistory): Long =
        terminalHistoryDao.insertHistory(history)

    suspend fun clearTerminalHistory(nodeId: Long) =
        terminalHistoryDao.clearHistoryForNode(nodeId)

    // Hermes Agent Messages & Sessions
    fun getHermesMessages(nodeId: Long, sessionId: String): Flow<List<HermesMessage>>? =
        hermesMessageDao?.getMessagesForSession(nodeId, sessionId)

    fun getHermesSessions(nodeId: Long): Flow<List<String>>? =
        hermesMessageDao?.getDistinctSessions(nodeId)

    suspend fun insertHermesMessage(message: HermesMessage): Long =
        hermesMessageDao?.insertMessage(message) ?: 0L

    suspend fun clearHermesSession(nodeId: Long, sessionId: String) =
        hermesMessageDao?.clearSession(nodeId, sessionId)

    suspend fun seedInitialNodesIfEmpty() {
        if (tailNodeDao.getNodeCount() == 0) {
            // Priority 1: Check if persistent backup exists (after app update or database recreation)
            if (context != null && NodeBackupManager.hasBackup(context)) {
                val backedUpNodes = NodeBackupManager.getBackupNodes(context)
                if (backedUpNodes.isNotEmpty()) {
                    backedUpNodes.forEach { tailNodeDao.insertNode(it.copy(id = 0)) }
                    return
                }
            }

            // Priority 2: Only if no backup exists, seed default sample nodes
            val sampleNodes = listOf(
                TailNode(
                    name = "Ubuntu GPU Rig",
                    tailscaleIp = "100.82.14.92",
                    osType = "LINUX",
                    sshPort = 22,
                    sshUser = "ubuntu",
                    llmPort = 11434,
                    llmType = "OLLAMA",
                    llmDefaultModel = "llama3.2:latest",
                    remoteDesktopPort = 6080,
                    remoteDesktopType = "WEB_DESKTOP",
                    fileServerPort = 8080,
                    tags = "RTX 4090 • Ollama • AI Dev",
                    isFavorite = true,
                    lastConnected = System.currentTimeMillis()
                ),
                TailNode(
                    name = "MacBook Pro M3",
                    tailscaleIp = "100.91.45.10",
                    osType = "MACOS",
                    sshPort = 22,
                    sshUser = "admin",
                    llmPort = 1234,
                    llmType = "LM_STUDIO",
                    llmDefaultModel = "qwen2.5:latest",
                    remoteDesktopPort = 5900,
                    remoteDesktopType = "VNC",
                    fileServerPort = 8080,
                    tags = "M3 Max • Local AI • Hermes",
                    isFavorite = true,
                    lastConnected = System.currentTimeMillis() - 3600000
                ),
                TailNode(
                    name = "Windows Workstation",
                    tailscaleIp = "100.104.78.23",
                    osType = "WINDOWS",
                    sshPort = 22,
                    sshUser = "user",
                    llmPort = 11434,
                    llmType = "OLLAMA",
                    llmDefaultModel = "qwen2.5-coder:latest",
                    remoteDesktopPort = 3389,
                    remoteDesktopType = "RDP",
                    fileServerPort = 8080,
                    tags = "RTX 3080 • Gaming • Dev",
                    isFavorite = false,
                    lastConnected = System.currentTimeMillis() - 86400000
                )
            )
            sampleNodes.forEach { tailNodeDao.insertNode(it) }
        }
    }
}
