package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ChatConversation
import com.example.data.model.ChatMessage
import com.example.data.model.FileTransfer
import com.example.data.model.HermesMessage
import com.example.data.model.TailNode
import com.example.data.model.TerminalHistory

@Database(
    entities = [
        TailNode::class,
        ChatConversation::class,
        ChatMessage::class,
        FileTransfer::class,
        TerminalHistory::class,
        HermesMessage::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tailNodeDao(): TailNodeDao
    abstract fun chatConversationDao(): ChatConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun fileTransferDao(): FileTransferDao
    abstract fun terminalHistoryDao(): TerminalHistoryDao
    abstract fun hermesMessageDao(): HermesMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tailnode_power_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
