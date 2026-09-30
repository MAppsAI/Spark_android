package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.TerminalHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface TerminalHistoryDao {
    @Query("SELECT * FROM terminal_history WHERE nodeId = :nodeId ORDER BY timestamp DESC")
    fun getHistoryForNode(nodeId: Long): Flow<List<TerminalHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TerminalHistory): Long

    @Query("DELETE FROM terminal_history WHERE nodeId = :nodeId")
    suspend fun clearHistoryForNode(nodeId: Long)
}
