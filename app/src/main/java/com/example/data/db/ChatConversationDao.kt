package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatConversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatConversationDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ChatConversation>>

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ChatConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversation)

    @Update
    suspend fun updateConversation(conversation: ChatConversation)

    @Query("UPDATE chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_conversations SET lastModelUsed = :model, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastModel(id: String, model: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
}
