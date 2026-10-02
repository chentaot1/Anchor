package com.anchor.adhd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.anchor.adhd.data.model.ChatTurnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatTurnDao {
    @Insert
    suspend fun insert(turn: ChatTurnEntity): Long

    @Query("SELECT * FROM chat_turns ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChatTurnEntity>>

    @Query("SELECT * FROM chat_turns WHERE taskId = :taskId ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun contextForTask(taskId: Long): ChatTurnEntity?

    /** Keeps only the newest `keep` rows, removing the rest (bounds table growth). */
    @Query("DELETE FROM chat_turns WHERE id NOT IN (SELECT id FROM chat_turns ORDER BY createdAtMillis DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM chat_turns")
    suspend fun clear()
}
