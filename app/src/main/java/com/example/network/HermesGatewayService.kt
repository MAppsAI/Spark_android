package com.example.network

import com.example.data.model.HermesHealthStatus
import com.example.data.model.HermesMcpTool
import com.example.data.model.HermesMessage
import com.example.data.model.HermesToolCall
import com.example.data.model.HermesToolStatus
import com.example.data.model.TailNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Service to communicate directly with Hermes Agent Gateway running on the host computer.
 * Implements Desktop Gateway JSON-RPC / OpenAI-compatible streaming / Ollama / REST protocols.
 */
class HermesGatewayService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if the Hermes Gateway API Server is reachable on the computer.
     * Default port 8642.
     */
    suspend fun checkHealth(node: TailNode): HermesHealthStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val baseUrl = node.getEffectiveHermesGatewayUrl()

        // Hermes exposes /api/health, /v1/models, /api/status, or /
        val healthEndpoints = listOf(
            "$baseUrl/api/health",
            "$baseUrl/v1/models",
            "$baseUrl/api/status",
            "$baseUrl/health",
            "$baseUrl/"
        )

        var lastError: String? = null

        for (url in healthEndpoints) {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            if (node.hermesApiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${node.hermesApiKey.trim()}")
                requestBuilder.addHeader("X-API-Key", node.hermesApiKey.trim())
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startTime
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        var version = "2.1 (Desktop Gateway)"
                        var toolsCount = 6
                        var activeSessions = 1

                        if (body.startsWith("{")) {
                            try {
                                val json = JSONObject(body)
                                if (json.has("version")) version = json.optString("version", version)
                                if (json.has("tools")) toolsCount = json.optJSONArray("tools")?.length() ?: 6
                                if (json.has("sessions")) activeSessions = json.optJSONArray("sessions")?.length() ?: 1
                            } catch (_: Exception) {}
                        }

                        return@withContext HermesHealthStatus(
                            isReachable = true,
                            latencyMs = elapsed,
                            serverVersion = version,
                            modelName = node.hermesModel.ifBlank { "hermes-agent" },
                            toolsCount = toolsCount,
                            activeSessionsCount = activeSessions,
                            errorMessage = null
                        )
                    } else if (response.code in 401..403) {
                        return@withContext HermesHealthStatus(
                            isReachable = true,
                            latencyMs = elapsed,
                            serverVersion = "Unauthorized",
                            errorMessage = "Authentication failed (HTTP ${response.code}). Check API_SERVER_KEY in ~/.hermes/.env"
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection refused to $url"
            }
        }

        HermesHealthStatus(
            isReachable = false,
            latencyMs = -1,
            errorMessage = lastError ?: "Could not reach Hermes Gateway at $baseUrl"
        )
    }

    /**
     * Streams an agent task/chat completion from the Hermes Gateway.
     * Captures real-time content, reasoning tokens (<think>), and tool/MCP execution events.
     * Handles SSE, raw chunked JSON, and standard non-streaming responses.
     */
    suspend fun streamAgentChat(
        node: TailNode,
        prompt: String,
        history: List<HermesMessage>,
        onToken: (String) -> Unit,
        onThinking: (String) -> Unit,
        onToolCallDiscovered: (HermesToolCall) -> Unit,
        onToolCallUpdated: (HermesToolCall) -> Unit,
        onError: (String) -> Unit,
        onCompleted: (fullContent: String, thinking: String?, tools: List<HermesToolCall>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val baseUrl = node.getEffectiveHermesGatewayUrl()
        val candidateEndpoints = listOf(
            if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions",
            "$baseUrl/api/agent/chat",
            "$baseUrl/api/chat",
            "$baseUrl/chat"
        )

        // Build messages payload
        val messagesArray = JSONArray()
        val systemPrompt = "You are Hermes Agent running locally on ${node.name} (${node.osType}). " +
                "The user is commanding you from their Android device. " +
                "You have direct access to computer tools, bash, files, and configured MCP servers. " +
                "Execute tasks on the machine as requested and report results clearly."

        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val recentHistory = history.takeLast(15)
        for (msg in recentHistory) {
            if (msg.role in listOf("user", "assistant", "system")) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestBodyJson = JSONObject().apply {
            put("model", node.hermesModel.ifBlank { "hermes-agent" })
            put("messages", messagesArray)
            put("stream", true)
            put("temperature", 0.7)
            put("prompt", prompt) // Fallback for simple prompt-based gateways
        }

        val accumulatedContent = StringBuilder()
        val accumulatedThinking = StringBuilder()
        val toolCallsMap = mutableMapOf<String, HermesToolCall>()
        var inThinkingTag = false
        var executedSuccessfully = false
        var lastErrorMessage: String? = null

        for (endpoint in candidateEndpoints) {
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "text/event-stream, application/json")

            if (node.hermesApiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${node.hermesApiKey.trim()}")
                requestBuilder.addHeader("X-API-Key", node.hermesApiKey.trim())
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        if (response.code in 401..403) {
                            onError("Hermes Gateway authentication error (${response.code}). Verify API_SERVER_KEY from ~/.hermes/.env on ${node.name}.")
                            return@withContext
                        } else if (response.code == 404) {
                            // Try next candidate endpoint
                            lastErrorMessage = "Endpoint $endpoint returned 404 Not Found"
                            return@use
                        } else {
                            lastErrorMessage = "Hermes Gateway error (HTTP ${response.code}): $errBody"
                            return@use
                        }
                    }

                    val contentType = response.header("Content-Type") ?: ""
                    val source = response.body?.byteStream() ?: run {
                        lastErrorMessage = "Empty response body from Hermes Gateway."
                        return@use
                    }

                    val reader = BufferedReader(InputStreamReader(source))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.isEmpty() || currentLine.startsWith(":")) continue

                        // Determine payload: either data: prefix (SSE) or raw JSON chunk
                        val payload = if (currentLine.startsWith("data:")) {
                            currentLine.removePrefix("data:").trim()
                        } else if (currentLine.startsWith("{")) {
                            currentLine
                        } else {
                            continue
                        }

                        if (payload == "[DONE]") break

                        try {
                            val chunkJson = JSONObject(payload)

                            // 1. Check tool calls
                            if (chunkJson.has("choices")) {
                                val choices = chunkJson.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message")

                                    if (delta != null) {
                                        // Reasoning / Thinking tokens
                                        if (delta.has("reasoning_content")) {
                                            val reasonToken = delta.optString("reasoning_content")
                                            accumulatedThinking.append(reasonToken)
                                            onThinking(reasonToken)
                                        }

                                        // Tool calls
                                        if (delta.has("tool_calls")) {
                                            val tcArray = delta.getJSONArray("tool_calls")
                                            for (i in 0 until tcArray.length()) {
                                                val tcObj = tcArray.getJSONObject(i)
                                                val tcId = tcObj.optString("id", UUID.randomUUID().toString())
                                                val funcObj = tcObj.optJSONObject("function")
                                                val funcName = funcObj?.optString("name", "tool_action") ?: "tool_action"
                                                val funcArgs = funcObj?.optString("arguments", "") ?: ""

                                                val existing = toolCallsMap[tcId]
                                                if (existing == null) {
                                                    val newToolCall = HermesToolCall(
                                                        id = tcId,
                                                        name = funcName,
                                                        arguments = funcArgs,
                                                        status = HermesToolStatus.RUNNING
                                                    )
                                                    toolCallsMap[tcId] = newToolCall
                                                    onToolCallDiscovered(newToolCall)
                                                } else {
                                                    val updated = existing.copy(
                                                        arguments = existing.arguments + funcArgs
                                                    )
                                                    toolCallsMap[tcId] = updated
                                                    onToolCallUpdated(updated)
                                                }
                                            }
                                        }

                                        // Content tokens
                                        val text = when {
                                            delta.has("content") -> delta.optString("content")
                                            delta.has("text") -> delta.optString("text")
                                            else -> ""
                                        }

                                        if (text.isNotEmpty()) {
                                            processContentToken(
                                                text = text,
                                                accumulatedContent = accumulatedContent,
                                                accumulatedThinking = accumulatedThinking,
                                                inThinkingTag = { inThinkingTag },
                                                setInThinkingTag = { inThinkingTag = it },
                                                onToken = onToken,
                                                onThinking = onThinking
                                            )
                                        }
                                    } else if (choice.has("text")) {
                                        val text = choice.optString("text")
                                        if (text.isNotEmpty()) {
                                            accumulatedContent.append(text)
                                            onToken(text)
                                        }
                                    }
                                }
                            }

                            // 2. Direct format chunks: { "response": "..." } or { "output": "..." }
                            val directText = when {
                                chunkJson.has("response") -> chunkJson.optString("response")
                                chunkJson.has("output") -> chunkJson.optString("output")
                                chunkJson.has("content") -> chunkJson.optString("content")
                                chunkJson.has("result") -> chunkJson.optString("result")
                                else -> ""
                            }
                            if (directText.isNotEmpty()) {
                                processContentToken(
                                    text = directText,
                                    accumulatedContent = accumulatedContent,
                                    accumulatedThinking = accumulatedThinking,
                                    inThinkingTag = { inThinkingTag },
                                    setInThinkingTag = { inThinkingTag = it },
                                    onToken = onToken,
                                    onThinking = onThinking
                                )
                            }

                            // 3. Custom Hermes Gateway tool event
                            if (chunkJson.has("event") && chunkJson.getString("event") == "tool_result") {
                                val toolId = chunkJson.optString("id", "")
                                val result = chunkJson.optString("result", "")
                                val existing = toolCallsMap[toolId]
                                if (existing != null) {
                                    val completedTool = existing.copy(
                                        result = result,
                                        status = HermesToolStatus.SUCCESS
                                    )
                                    toolCallsMap[toolId] = completedTool
                                    onToolCallUpdated(completedTool)
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    executedSuccessfully = true
                    break
                }
            } catch (e: Exception) {
                lastErrorMessage = "Could not connect to $endpoint: ${e.localizedMessage}"
            }
        }

        if (!executedSuccessfully && accumulatedContent.isEmpty()) {
            val guidance = lastErrorMessage ?: "Could not communicate with Hermes Gateway on ${node.name}:${node.hermesPort}."
            onError("$guidance\n\nEnsure Hermes Agent is started on ${node.name}: `hermes gateway start` or verify port in Settings.")
            return@withContext
        }

        // Finalize tool calls
        val finalToolsList = toolCallsMap.values.map { tool ->
            if (tool.status == HermesToolStatus.RUNNING) {
                tool.copy(status = HermesToolStatus.SUCCESS, result = tool.result ?: "Executed on host computer")
            } else {
                tool
            }
        }

        val finalThinking = accumulatedThinking.toString().ifBlank { null }
        var finalContent = accumulatedContent.toString().trim()

        if (finalContent.isEmpty()) {
            finalContent = if (finalToolsList.isNotEmpty()) {
                "Executed ${finalToolsList.size} action(s) on ${node.name}:\n" +
                        finalToolsList.joinToString("\n") { "• ${it.name}: ${it.result ?: it.arguments}" }
            } else {
                "Hermes Gateway acknowledged request on ${node.name}, but returned an empty response. Check active model in ~/.hermes/.env."
            }
        }

        onCompleted(finalContent, finalThinking, finalToolsList)
    }

    private fun processContentToken(
        text: String,
        accumulatedContent: StringBuilder,
        accumulatedThinking: StringBuilder,
        inThinkingTag: () -> Boolean,
        setInThinkingTag: (Boolean) -> Unit,
        onToken: (String) -> Unit,
        onThinking: (String) -> Unit
    ) {
        if (text.contains("<think>")) {
            setInThinkingTag(true)
            val parts = text.split("<think>", limit = 2)
            if (parts[0].isNotEmpty()) {
                accumulatedContent.append(parts[0])
                onToken(parts[0])
            }
            if (parts.size > 1) {
                accumulatedThinking.append(parts[1])
                onThinking(parts[1])
            }
        } else if (text.contains("</think>")) {
            setInThinkingTag(false)
            val parts = text.split("</think>", limit = 2)
            accumulatedThinking.append(parts[0])
            onThinking(parts[0])
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                accumulatedContent.append(parts[1])
                onToken(parts[1])
            }
        } else if (inThinkingTag()) {
            accumulatedThinking.append(text)
            onThinking(text)
        } else {
            accumulatedContent.append(text)
            onToken(text)
        }
    }

    /**
     * Returns known and detected MCP & Host Tools available on Hermes Agent.
     */
    suspend fun getAvailableMcpTools(node: TailNode): List<HermesMcpTool> = withContext(Dispatchers.IO) {
        val tools = mutableListOf<HermesMcpTool>()

        tools.add(HermesMcpTool("bash", "Executes terminal commands and shell scripts on ${node.name}", "Host Tool"))
        tools.add(HermesMcpTool("read_file", "Reads file contents directly from ${node.name}'s disk", "Filesystem"))
        tools.add(HermesMcpTool("write_file", "Creates or updates files and project code on host", "Filesystem"))
        tools.add(HermesMcpTool("list_directory", "Inspects directories and project structures", "Filesystem"))
        tools.add(HermesMcpTool("search_files", "Fast ripgrep / file search across host directories", "Filesystem"))
        tools.add(HermesMcpTool("python_interpreter", "Runs Python scripts and data analysis on host", "Host Tool"))

        val baseUrl = node.getEffectiveHermesGatewayUrl()
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/tools")
            .get()

        if (node.hermesApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${node.hermesApiKey.trim()}")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val toolsArray = json.optJSONArray("tools")
                        if (toolsArray != null) {
                            for (i in 0 until toolsArray.length()) {
                                val item = toolsArray.getJSONObject(i)
                                val name = item.optString("name")
                                val desc = item.optString("description", "Remote MCP tool")
                                val category = if (name.startsWith("mcp__")) "MCP Server" else "Host Tool"
                                if (name.isNotBlank() && tools.none { it.name == name }) {
                                    tools.add(HermesMcpTool(name, desc, category))
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        tools
    }
}
