package com.example.network

import com.example.data.model.ChatMessage
import com.example.data.model.TailNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalLlmService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val activeCall = AtomicReference<okhttp3.Call?>(null)

    fun cancelActiveCall() {
        try {
            activeCall.getAndSet(null)?.cancel()
        } catch (_: Exception) {}
    }

    /**
     * Probes the configured LLM endpoint and returns the available models list.
     */
    suspend fun getAvailableModels(node: TailNode): List<String> = withContext(Dispatchers.IO) {
        val models = mutableListOf<String>()
        val baseUrl = node.getEffectiveLlmBaseUrl()

        val (tagsUrl, isOpenAi) = if (node.llmType.uppercase() == "OLLAMA") {
            val url = if (baseUrl.endsWith("/api")) "$baseUrl/tags" else "$baseUrl/api/tags"
            Pair(url, false)
        } else {
            val url = if (baseUrl.endsWith("/v1")) "$baseUrl/models" else "$baseUrl/v1/models"
            Pair(url, true)
        }

        val requestBuilder = Request.Builder().url(tagsUrl).get()
        val apiKey = node.llmApiKey.ifBlank { node.hermesApiKey }.trim()
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        if (!isOpenAi) {
                            // Ollama format: { "models": [ { "name": "llama3.2:latest" } ] }
                            val modelsArray = json.optJSONArray("models") ?: JSONArray()
                            for (i in 0 until modelsArray.length()) {
                                val item = modelsArray.getJSONObject(i)
                                val name = item.optString("name")
                                if (name.isNotBlank()) models.add(name)
                            }
                        } else {
                            // OpenAI / Hermes Agent format: { "data": [ { "id": "hermes-agent" } ] } or { "models": [...] }
                            val data = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                            for (i in 0 until data.length()) {
                                val item = data.getJSONObject(i)
                                val id = item.optString("id").ifBlank { item.optString("name") }
                                if (id.isNotBlank()) models.add(id)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Server might not be running or model list endpoint unsupported
        }

        // If no models were discovered automatically, return the node's configured default/hermes models
        if (models.isEmpty()) {
            if (node.hermesModel.isNotBlank()) {
                models.add(node.hermesModel)
            } else if (node.llmDefaultModel.isNotBlank()) {
                models.add(node.llmDefaultModel)
            } else if (node.name.contains("spark", ignoreCase = true) || baseUrl.contains("hermes", ignoreCase = true)) {
                models.add("hermes-agent")
            }
        }

        models
    }

    /**
     * Performs a fast connectivity check to the configured Base URL.
     */
    suspend fun testConnection(node: TailNode): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val baseUrl = node.getEffectiveLlmBaseUrl()

        val (testUrl, isOpenAi) = if (node.llmType.uppercase() == "OLLAMA") {
            val url = if (baseUrl.endsWith("/api")) "$baseUrl/tags" else "$baseUrl/api/tags"
            Pair(url, false)
        } else {
            val url = if (baseUrl.endsWith("/v1")) "$baseUrl/models" else "$baseUrl/v1/models"
            Pair(url, true)
        }

        val requestBuilder = Request.Builder().url(testUrl).get()
        if (node.llmApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${node.llmApiKey.trim()}")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val modelCount = try {
                        val json = JSONObject(body)
                        if (!isOpenAi) {
                            json.optJSONArray("models")?.length() ?: 0
                        } else {
                            json.optJSONArray("data")?.length() ?: 0
                        }
                    } catch (_: Exception) {
                        0
                    }
                    ConnectionTestResult(
                        isSuccess = true,
                        statusCode = response.code,
                        latencyMs = elapsed,
                        urlTested = testUrl,
                        message = "Connected to $baseUrl in ${elapsed}ms. Found $modelCount models."
                    )
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        latencyMs = elapsed,
                        urlTested = testUrl,
                        message = "HTTP ${response.code} ${response.message} from $testUrl."
                    )
                }
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            val detail = when (e) {
                is java.net.ConnectException -> "Connection refused. Is the server running and listening on 0.0.0.0?"
                is java.net.SocketTimeoutException -> "Connection timed out (host unreachable or firewall drop)."
                is java.net.UnknownHostException -> "Host name cannot be resolved on Tailscale."
                else -> e.localizedMessage ?: "Unknown network failure"
            }
            ConnectionTestResult(
                isSuccess = false,
                statusCode = -1,
                latencyMs = elapsed,
                urlTested = testUrl,
                message = "Failed to reach $testUrl: $detail"
            )
        }
    }

    /**
     * Sends a chat prompt to the remote LLM server with complete multi-turn conversation history.
     */
    suspend fun sendChatMessage(
        node: TailNode,
        model: String,
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        systemPrompt: String = ""
    ): LlmResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val baseUrl = node.getEffectiveLlmBaseUrl()
        val isOllama = node.llmType.uppercase() == "OLLAMA"

        val endpointUrl = if (isOllama) {
            if (baseUrl.endsWith("/api")) "$baseUrl/chat" else "$baseUrl/api/chat"
        } else {
            if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
        }

        // Build messages array containing full conversation context (up to last 16 turns)
        val messagesArray = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt.trim())
            })
        }
        val recentHistory = history.takeLast(16)
        for (msg in recentHistory) {
            if (msg.role in listOf("user", "assistant", "system") && msg.content.isNotBlank()) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content.trim())
                })
            }
        }
        // Current user prompt
        if (prompt.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt.trim())
            })
        }

        try {
            val requestBuilder = Request.Builder().url(endpointUrl)

            val apiKey = node.llmApiKey.ifBlank { node.hermesApiKey }.trim()
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            if (isOllama) {
                val jsonPayload = JSONObject().apply {
                    put("model", model.ifBlank { node.llmDefaultModel })
                    put("messages", messagesArray)
                    put("stream", false)
                }
                val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                requestBuilder.post(requestBody)

                val call = client.newCall(requestBuilder.build())
                activeCall.set(call)
                try {
                    call.execute().use { response ->
                        val elapsedMs = System.currentTimeMillis() - startTime
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JSONObject(body)
                                val messageObj = json.optJSONObject("message")
                                var responseText = messageObj?.optString("content") ?: json.optString("response", "")
                                if (responseText.isBlank()) {
                                    responseText = json.optString("text", "")
                                }
                                val totalDuration = json.optLong("total_duration", 0L)
                                val evalCount = json.optInt("eval_count", 0)
                                val tokPerSec = if (totalDuration > 0 && evalCount > 0) {
                                    String.format("%.1f", (evalCount.toDouble() / (totalDuration / 1_000_000_000.0)))
                                } else "Active"

                                val finalText = if (responseText.isBlank()) {
                                    if (node.name.contains("spark", ignoreCase = true) || baseUrl.contains("hermes", ignoreCase = true)) {
                                        "Hermes Gateway acknowledged request on ${node.name}, but returned an empty response. Check active model in ~/.hermes/.env."
                                    } else {
                                        "(Empty response from ${node.name}. Check model status.)"
                                    }
                                } else responseText

                                return@withContext LlmResponse(
                                    text = finalText,
                                    stats = "$tokPerSec tok/s • ${elapsedMs}ms • ${node.name}",
                                    isLiveConnection = true,
                                    isError = false
                                )
                            }
                        } else if (response.code == 404) {
                            // Fallback to /api/generate if /api/chat is not available on legacy Ollama versions
                            return@withContext sendOllamaGenerateFallback(node, model, prompt, startTime)
                        } else {
                            val errBody = response.body?.string() ?: ""
                            return@withContext LlmResponse(
                                text = "Ollama returned HTTP ${response.code} (${response.message})\nEndpoint: $endpointUrl\n$errBody",
                                stats = "HTTP ${response.code}",
                                isLiveConnection = false,
                                isError = true,
                                errorDetails = "Server error code ${response.code}"
                            )
                        }
                    }
                } finally {
                    activeCall.compareAndSet(call, null)
                }
            } else {
                // OpenAI-compatible endpoint (Hermes Gateway, LM Studio, vLLM, LocalAI, Jan, LiteLLM)
                val effectiveModel = if (model.isNotBlank()) {
                    model.trim()
                } else if (node.hermesModel.isNotBlank()) {
                    node.hermesModel.trim()
                } else if (node.llmDefaultModel.isNotBlank()) {
                    node.llmDefaultModel.trim()
                } else if (node.name.contains("spark", ignoreCase = true) || baseUrl.contains("hermes", ignoreCase = true)) {
                    "hermes-agent"
                } else {
                    "default"
                }

                val jsonPayload = JSONObject().apply {
                    put("model", effectiveModel)
                    put("messages", messagesArray)
                    put("stream", false) // Explicitly request non-streaming JSON from Hermes Gateway / OpenAI
                    put("temperature", 0.7)
                }
                val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                requestBuilder.post(requestBody)

                val call = client.newCall(requestBuilder.build())
                activeCall.set(call)
                try {
                    call.execute().use { response ->
                        val elapsedMs = System.currentTimeMillis() - startTime
                        if (response.isSuccessful) {
                            val rawBody = response.body?.string()
                            if (!rawBody.isNullOrBlank()) {
                                var extractedText = ""

                                // Check if response is Server-Sent Events (SSE) stream or plain JSON
                                if (rawBody.contains("data:") || response.header("Content-Type")?.contains("text/event-stream", ignoreCase = true) == true) {
                                    val contentSb = StringBuilder()
                                    val reasoningSb = StringBuilder()
                                    rawBody.lineSequence().forEach { line ->
                                        val trimmed = line.trim()
                                        if (trimmed.startsWith("data:") && !trimmed.contains("[DONE]")) {
                                            val dataStr = trimmed.removePrefix("data:").trim()
                                            if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                                                try {
                                                    val chunkJson = JSONObject(dataStr)
                                                    val choicesArr = chunkJson.optJSONArray("choices")
                                                    if (choicesArr != null && choicesArr.length() > 0) {
                                                        val ch = choicesArr.getJSONObject(0)
                                                        val delta = ch.optJSONObject("delta")
                                                        val msg = ch.optJSONObject("message")
                                                        val c = delta?.optString("content") ?: msg?.optString("content") ?: ch.optString("text", "")
                                                        val r = delta?.optString("reasoning_content") ?: msg?.optString("reasoning_content") ?: ""
                                                        if (c.isNotEmpty()) contentSb.append(c)
                                                        if (r.isNotEmpty()) reasoningSb.append(r)
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    extractedText = if (contentSb.isNotEmpty()) contentSb.toString() else reasoningSb.toString()
                                } else {
                                    try {
                                        val json = JSONObject(rawBody)

                                        // Check for error object inside 200 OK responses
                                        val errObj = json.optJSONObject("error")
                                        val errorMsg = errObj?.optString("message")
                                            ?: json.optString("error").takeIf { it.isNotBlank() }
                                            ?: json.optString("detail").takeIf { it.isNotBlank() }
                                        if (!errorMsg.isNullOrBlank()) {
                                            return@withContext LlmResponse(
                                                text = "⚠️ Hermes/Server reported error: $errorMsg",
                                                stats = "Error • ${elapsedMs}ms",
                                                isLiveConnection = true,
                                                isError = true,
                                                errorDetails = errorMsg
                                            )
                                        }

                                        val choices = json.optJSONArray("choices")
                                        if (choices != null && choices.length() > 0) {
                                            val choice = choices.getJSONObject(0)
                                            val messageObj = choice.optJSONObject("message")
                                            val deltaObj = choice.optJSONObject("delta")
                                            val contentStr = messageObj?.optString("content")
                                                ?: deltaObj?.optString("content")
                                                ?: choice.optString("text", "")
                                            val reasoningStr = messageObj?.optString("reasoning_content")
                                                ?: deltaObj?.optString("reasoning_content")
                                                ?: ""

                                            extractedText = if (contentStr.isNotBlank() && reasoningStr.isNotBlank()) {
                                                "$reasoningStr\n\n$contentStr"
                                            } else if (contentStr.isNotBlank()) {
                                                contentStr
                                            } else {
                                                reasoningStr
                                            }

                                            if (extractedText.isBlank() && messageObj != null) {
                                                val parts = messageObj.optJSONArray("content")
                                                if (parts != null) {
                                                    val sb = StringBuilder()
                                                    for (p in 0 until parts.length()) {
                                                        val partObj = parts.optJSONObject(p)
                                                        if (partObj != null) {
                                                            sb.append(partObj.optString("text"))
                                                        } else {
                                                            sb.append(parts.optString(p))
                                                        }
                                                    }
                                                    extractedText = sb.toString()
                                                }
                                            }
                                        }

                                        if (extractedText.isBlank()) {
                                            extractedText = json.optString("response", "")
                                            if (extractedText.isBlank()) extractedText = json.optString("content", "")
                                            if (extractedText.isBlank()) extractedText = json.optString("output", "")
                                            if (extractedText.isBlank()) extractedText = json.optString("text", "")
                                        }
                                    } catch (_: Exception) {
                                        extractedText = rawBody.trim()
                                    }
                                }

                                val finalText = if (extractedText.isBlank()) {
                                    if (node.name.contains("spark", ignoreCase = true) || baseUrl.contains("hermes", ignoreCase = true) || rawBody.contains("hermes", ignoreCase = true)) {
                                        "Hermes Gateway acknowledged request on ${node.name}, but returned an empty response. Check active model in ~/.hermes/.env."
                                    } else {
                                        "${node.name} acknowledged request, but returned an empty response. Check active model configuration in ~/.hermes/.env or endpoint settings."
                                    }
                                } else extractedText

                                return@withContext LlmResponse(
                                    text = finalText,
                                    stats = "${effectiveModel} • ${elapsedMs}ms • ${node.name}",
                                    isLiveConnection = true,
                                    isError = false
                                )
                            }
                        } else {
                            val errBody = response.body?.string() ?: ""
                            return@withContext LlmResponse(
                                text = "LLM Server returned HTTP ${response.code} (${response.message})\nEndpoint: $endpointUrl\n$errBody",
                                stats = "HTTP ${response.code}",
                                isLiveConnection = false,
                                isError = true,
                                errorDetails = "Server error code ${response.code}"
                            )
                        }
                    }
                } finally {
                    activeCall.compareAndSet(call, null)
                }
            }
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startTime
            if (e is IOException && (e.message?.contains("Canceled", ignoreCase = true) == true || e.message?.contains("Socket closed", ignoreCase = true) == true)) {
                return@withContext LlmResponse(
                    text = "(Generation stopped by user)",
                    stats = "Stopped",
                    isLiveConnection = true,
                    isError = false
                )
            }
            val detail = when (e) {
                is java.net.ConnectException -> "Connection refused at $endpointUrl. Is the LLM service running on ${node.name} and bound to 0.0.0.0 (OLLAMA_HOST=0.0.0.0)?"
                is java.net.SocketTimeoutException -> "Inference timed out after ${elapsedMs / 1000}s on ${node.name} (model may be loading into VRAM)."
                else -> e.localizedMessage ?: "Network error"
            }
            return@withContext LlmResponse(
                text = "⚠️ Connection Failed to $endpointUrl\n\n$detail",
                stats = "Failed (${elapsedMs}ms)",
                isLiveConnection = false,
                isError = true,
                errorDetails = detail
            )
        }

        LlmResponse(
            text = "No response data received from LLM endpoint.",
            stats = "Empty",
            isLiveConnection = false,
            isError = true
        )
    }

    /**
     * Streaming variant: SSE (OpenAI-compatible) or NDJSON (Ollama) token
     * stream. onToken fires per token as it arrives so callers can begin TTS
     * before generation finishes. Falls back to non-streaming on protocol
     * errors that yield no text at all.
     */
    suspend fun sendChatMessageStreaming(
        node: TailNode,
        model: String,
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        systemPrompt: String = "",
        onToken: (String) -> Unit
    ): LlmResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val baseUrl = node.getEffectiveLlmBaseUrl()
        val isOllama = node.llmType.uppercase() == "OLLAMA"

        val endpointUrl = if (isOllama) {
            if (baseUrl.endsWith("/api")) "$baseUrl/chat" else "$baseUrl/api/chat"
        } else {
            if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
        }

        val messagesArray = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt.trim())
            })
        }
        for (msg in history.takeLast(16)) {
            if (msg.role in listOf("user", "assistant", "system") && msg.content.isNotBlank()) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content.trim())
                })
            }
        }
        if (prompt.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt.trim())
            })
        }

        val jsonPayload = if (isOllama) {
            JSONObject().apply {
                put("model", model.ifBlank { node.llmDefaultModel })
                put("messages", messagesArray)
                put("stream", true)
            }
        } else {
            val effectiveModel = model.ifBlank {
                node.hermesModel.ifBlank {
                    node.llmDefaultModel.ifBlank {
                        if (node.name.contains("spark", true) || baseUrl.contains("hermes", true)) "hermes-agent" else "default"
                    }
                }
            }
            JSONObject().apply {
                put("model", effectiveModel)
                put("messages", messagesArray)
                put("stream", true)
                put("temperature", 0.7)
            }
        }

        val requestBuilder = Request.Builder().url(endpointUrl)
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "text/event-stream, application/x-ndjson, application/json")
        val apiKey = node.llmApiKey.ifBlank { node.hermesApiKey }.trim()
        if (apiKey.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $apiKey")

        val content = StringBuilder()
        val call = client.newCall(requestBuilder.build())
        activeCall.set(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LlmResponse(
                        text = "",
                        stats = "HTTP ${response.code}",
                        isLiveConnection = false,
                        isError = true,
                        errorDetails = "HTTP ${response.code} from $endpointUrl"
                    )
                }
                val src = response.body?.source()
                    ?: return@withContext LlmResponse(
                        text = "", stats = "Empty", isLiveConnection = false,
                        isError = true, errorDetails = "no body"
                    )
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val payload = if (trimmed.startsWith("data:")) {
                        val d = trimmed.removePrefix("data:").trim()
                        if (d == "[DONE]") break
                        d
                    } else trimmed
                    if (!payload.startsWith("{")) continue
                    try {
                        val obj = JSONObject(payload)
                        if (isOllama) {
                            val tok = obj.optString("response")
                            if (tok.isNotEmpty()) { content.append(tok); onToken(tok) }
                            if (obj.optBoolean("done", false)) break
                        } else {
                            val choices = obj.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val ch = choices.getJSONObject(0)
                                val delta = ch.optJSONObject("delta")
                                val msg = ch.optJSONObject("message")
                                val tok = delta?.optString("content").orEmpty()
                                    .ifEmpty { msg?.optString("content").orEmpty() }
                                    .ifEmpty { ch.optString("text") }
                                if (tok.isNotEmpty()) { content.append(tok); onToken(tok) }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            if (content.isNotEmpty()) {
                // partial generation with a mid-stream drop — still usable
                return@withContext LlmResponse(
                    text = content.toString(),
                    stats = "stream ${System.currentTimeMillis() - startTime}ms • ${node.name}",
                    isLiveConnection = true,
                    isError = false
                )
            }
            val detail = when (e) {
                is java.net.ConnectException -> "Connection refused at $endpointUrl."
                is java.net.SocketTimeoutException -> "Stream timed out on ${node.name}."
                else -> e.localizedMessage ?: "Network error"
            }
            return@withContext LlmResponse(
                text = "⚠️ Connection Failed to $endpointUrl\n\n$detail",
                stats = "Failed",
                isLiveConnection = false,
                isError = true,
                errorDetails = detail
            )
        } finally {
            activeCall.compareAndSet(call, null)
        }

        val elapsed = System.currentTimeMillis() - startTime
        if (content.isEmpty()) {
            // server ignored stream=true or returned nothing — one non-stream retry
            return@withContext sendChatMessage(node, model, prompt, history)
        }
        LlmResponse(
            text = content.toString(),
            stats = "stream ${elapsed}ms • ${node.name}",
            isLiveConnection = true,
            isError = false
        )
    }

    private fun sendOllamaGenerateFallback(
        node: TailNode,
        model: String,
        prompt: String,
        startTime: Long
    ): LlmResponse {
        val baseUrl = node.getEffectiveLlmBaseUrl()
        val generateUrl = if (baseUrl.endsWith("/api")) "$baseUrl/generate" else "$baseUrl/api/generate"
        val jsonPayload = JSONObject().apply {
            put("model", model.ifBlank { node.llmDefaultModel })
            put("prompt", prompt)
            put("stream", false)
        }
        val request = Request.Builder()
            .url(generateUrl)
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        activeCall.set(call)
        try {
            call.execute().use { response ->
                val elapsedMs = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    return LlmResponse(
                        text = json.optString("response"),
                        stats = "Ollama Generate • ${elapsedMs}ms",
                        isLiveConnection = true,
                        isError = false
                    )
                } else {
                    return LlmResponse(
                        text = "HTTP ${response.code} from $generateUrl",
                        stats = "Error",
                        isLiveConnection = false,
                        isError = true
                    )
                }
            }
        } catch (e: Exception) {
            if (e is IOException && (e.message?.contains("Canceled", ignoreCase = true) == true || e.message?.contains("Socket closed", ignoreCase = true) == true)) {
                return LlmResponse(
                    text = "(Generation stopped by user)",
                    stats = "Stopped",
                    isLiveConnection = true,
                    isError = false
                )
            }
            return LlmResponse(
                text = "Failed to connect to fallback generate: ${e.message}",
                stats = "Error",
                isLiveConnection = false,
                isError = true
            )
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }
}

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val urlTested: String,
    val message: String
)

data class LlmResponse(
    val text: String,
    val stats: String,
    val isLiveConnection: Boolean,
    val isError: Boolean = false,
    val errorDetails: String? = null
)
