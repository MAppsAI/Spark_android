package com.example.data.backup

import android.content.Context
import android.util.Log
import com.example.data.model.TailNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NodeBackupManager {
    private const val TAG = "NodeBackupManager"
    private const val PREFS_NAME = "tailnode_persistent_backup"
    private const val KEY_BACKUP_JSON = "saved_nodes_json"
    private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
    private const val BACKUP_FILENAME = "tailnodes_persistent_backup.json"

    fun saveBackup(context: Context, nodes: List<TailNode>) {
        if (nodes.isEmpty()) return
        try {
            val jsonArray = JSONArray()
            for (node in nodes) {
                jsonArray.put(nodeToJson(node))
            }
            val jsonString = jsonArray.toString(2)
            val now = System.currentTimeMillis()

            // 1. Save to SharedPreferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKUP_JSON, jsonString)
                .putLong(KEY_LAST_BACKUP_TIMESTAMP, now)
                .apply()

            // 2. Save to internal private file
            val file = File(context.filesDir, BACKUP_FILENAME)
            file.writeText(jsonString)
            Log.d(TAG, "Successfully backed up ${nodes.size} nodes to persistent storage.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save nodes backup", e)
        }
    }

    fun hasBackup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BACKUP_JSON, null)
        if (!json.isNullOrBlank()) return true

        val file = File(context.filesDir, BACKUP_FILENAME)
        return file.exists() && file.length() > 10
    }

    fun getBackupNodes(context: Context): List<TailNode> {
        try {
            // Check file first
            val file = File(context.filesDir, BACKUP_FILENAME)
            val jsonString = if (file.exists() && file.length() > 10) {
                file.readText()
            } else {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(KEY_BACKUP_JSON, "") ?: ""
            }

            if (jsonString.isNotBlank()) {
                return parseNodesFromJson(jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read nodes backup", e)
        }
        return emptyList()
    }

    fun getLastBackupTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
    }

    fun exportConfigJson(nodes: List<TailNode>): String {
        val jsonArray = JSONArray()
        for (node in nodes) {
            jsonArray.put(nodeToJson(node))
        }
        return jsonArray.toString(2)
    }

    fun parseNodesFromJson(jsonString: String): List<TailNode> {
        val list = mutableListOf<TailNode>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(jsonToNode(obj))
        }
        return list
    }

    private fun nodeToJson(node: TailNode): JSONObject {
        return JSONObject().apply {
            put("id", node.id)
            put("name", node.name)
            put("tailscaleIp", node.tailscaleIp)
            put("osType", node.osType)
            put("sshPort", node.sshPort)
            put("sshUser", node.sshUser)
            put("sshPassword", node.sshPassword)
            put("sshPrivateKey", node.sshPrivateKey)
            put("llmPort", node.llmPort)
            put("llmType", node.llmType)
            put("llmDefaultModel", node.llmDefaultModel)
            put("llmBaseUrl", node.llmBaseUrl)
            put("llmApiKey", node.llmApiKey)
            put("remoteDesktopPort", node.remoteDesktopPort)
            put("remoteDesktopType", node.remoteDesktopType)
            put("remoteDesktopUrl", node.remoteDesktopUrl)
            put("fileServerPort", node.fileServerPort)
            put("fileServerUrl", node.fileServerUrl)
            put("webTerminalPort", node.webTerminalPort)
            put("hermesPort", node.hermesPort)
            put("hermesDashboardPort", node.hermesDashboardPort)
            put("hermesApiKey", node.hermesApiKey)
            put("hermesBaseUrl", node.hermesBaseUrl)
            put("hermesModel", node.hermesModel)
            put("tags", node.tags)
            put("isFavorite", node.isFavorite)
            put("lastConnected", node.lastConnected)
        }
    }

    private fun jsonToNode(obj: JSONObject): TailNode {
        return TailNode(
            id = obj.optLong("id", 0L),
            name = obj.optString("name", "Unnamed Node"),
            tailscaleIp = obj.optString("tailscaleIp", "100.64.0.1"),
            osType = obj.optString("osType", "LINUX"),
            sshPort = obj.optInt("sshPort", 22),
            sshUser = obj.optString("sshUser", "admin"),
            sshPassword = obj.optString("sshPassword", ""),
            sshPrivateKey = obj.optString("sshPrivateKey", ""),
            llmPort = obj.optInt("llmPort", 11434),
            llmType = obj.optString("llmType", "OLLAMA"),
            llmDefaultModel = obj.optString("llmDefaultModel", "llama3.2:latest"),
            llmBaseUrl = obj.optString("llmBaseUrl", ""),
            llmApiKey = obj.optString("llmApiKey", ""),
            remoteDesktopPort = obj.optInt("remoteDesktopPort", 6080),
            remoteDesktopType = obj.optString("remoteDesktopType", "WEB_DESKTOP"),
            remoteDesktopUrl = obj.optString("remoteDesktopUrl", ""),
            fileServerPort = obj.optInt("fileServerPort", 8080),
            fileServerUrl = obj.optString("fileServerUrl", ""),
            webTerminalPort = obj.optInt("webTerminalPort", 7681),
            hermesPort = obj.optInt("hermesPort", 8642),
            hermesDashboardPort = obj.optInt("hermesDashboardPort", 9119),
            hermesApiKey = obj.optString("hermesApiKey", ""),
            hermesBaseUrl = obj.optString("hermesBaseUrl", ""),
            hermesModel = obj.optString("hermesModel", "hermes-agent"),
            tags = obj.optString("tags", "Saved Node"),
            isFavorite = obj.optBoolean("isFavorite", false),
            lastConnected = obj.optLong("lastConnected", System.currentTimeMillis())
        )
    }
}
