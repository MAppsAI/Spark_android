package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.HermesMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface HermesMessageDao {
    @Query("SELECT * FROM hermes_messages WHERE nodeId = :nodeId AND sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(nodeId: Long, sessionId: String): Flow<List<HermesMessage>>

    @Query("SELECT DISTINCT sessionId FROM hermes_messages WHERE nodeId = :nodeId ORDER BY timestamp DESC")
    fun getDistinctSessions(nodeId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: HermesMessage): Long

    @Query("DELETE FROM hermes_messages WHERE nodeId = :nodeId AND sessionId = :sessionId")
    suspend fun clearSession(nodeId: Long, sessionId: String)

    @Query("DELETE FROM hermes_messages WHERE nodeId = :nodeId")
    suspend fun clearAllForNode(nodeId: Long)
}
