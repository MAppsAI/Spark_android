package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "terminal_history")
data class TerminalHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nodeId: Long,
    val command: String,
    val output: String,
    val exitCode: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class TerminalPreset(
    val title: String,
    val command: String,
    val category: String, // "System", "Tailscale", "AI / GPU", "Docker"
    val iconName: String = "code"
)

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
