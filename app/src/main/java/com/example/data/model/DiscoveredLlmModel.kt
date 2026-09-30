package com.example.data.model

data class DiscoveredLlmModel(
    val modelName: String,
    val nodeId: Long,
    val nodeName: String,
    val nodeOs: String,
    val nodeIp: String,
    val port: Int,
    val llmType: String
) {
    val displayName: String
        get() = "$modelName • $nodeName ($nodeOs)"
}
