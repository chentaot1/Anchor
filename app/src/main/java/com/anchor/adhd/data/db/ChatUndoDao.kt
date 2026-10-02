package com.anchor.adhd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.anchor.adhd.data.model.ChatUndoOpEntity

@Dao
interface ChatUndoDao {
    @Insert
    suspend fun insert(op: ChatUndoOpEntity): Long

    @Query("SELECT * FROM chat_undo_ops WHERE createdAtMillis >= :sinceMillis ORDER BY createdAtMillis ASC")
    suspend fun listSince(sinceMillis: Long): List<ChatUndoOpEntity>

    @Query("DELETE FROM chat_undo_ops WHERE createdAtMillis < :beforeMillis")
    suspend fun pruneBefore(beforeMillis: Long)

    @Query("DELETE FROM chat_undo_ops WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_undo_ops")
    suspend fun clear()
}
