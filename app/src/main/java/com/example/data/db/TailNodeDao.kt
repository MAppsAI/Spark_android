package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TailNode
import kotlinx.coroutines.flow.Flow

@Dao
interface TailNodeDao {
    @Query("SELECT * FROM tail_nodes ORDER BY isFavorite DESC, lastConnected DESC")
    fun getAllNodes(): Flow<List<TailNode>>

    @Query("SELECT * FROM tail_nodes ORDER BY isFavorite DESC, lastConnected DESC")
    suspend fun getAllNodesDirect(): List<TailNode>

    @Query("SELECT * FROM tail_nodes WHERE id = :nodeId LIMIT 1")
    fun getNodeById(nodeId: Long): Flow<TailNode?>

    @Query("SELECT * FROM tail_nodes WHERE id = :nodeId LIMIT 1")
    suspend fun getNodeByIdDirect(nodeId: Long): TailNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: TailNode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<TailNode>)

    @Update
    suspend fun updateNode(node: TailNode)

    @Delete
    suspend fun deleteNode(node: TailNode)

    @Query("UPDATE tail_nodes SET isFavorite = NOT isFavorite WHERE id = :nodeId")
    suspend fun toggleFavorite(nodeId: Long)

    @Query("UPDATE tail_nodes SET lastConnected = :timestamp WHERE id = :nodeId")
    suspend fun updateLastConnected(nodeId: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM tail_nodes")
    suspend fun getNodeCount(): Int
}
