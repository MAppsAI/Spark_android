package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FileTransfer
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTransferDao {
    @Query("SELECT * FROM file_transfers WHERE nodeId = :nodeId ORDER BY timestamp DESC")
    fun getTransfersForNode(nodeId: Long): Flow<List<FileTransfer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: FileTransfer): Long

    @Query("DELETE FROM file_transfers WHERE nodeId = :nodeId")
    suspend fun clearTransfersForNode(nodeId: Long)
}
