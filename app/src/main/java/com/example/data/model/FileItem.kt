package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_transfers")
data class FileTransfer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nodeId: Long,
    val fileName: String,
    val fileSize: Long,
    val direction: String, // "UPLOAD" or "DOWNLOAD"
    val status: String, // "COMPLETED", "IN_PROGRESS", "FAILED"
    val remotePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val transferSpeed: String = "45.2 MB/s"
)

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modifiedDate: String = "Today",
    val extension: String = "",
    val permissions: String = "rw-r--r--"
)
