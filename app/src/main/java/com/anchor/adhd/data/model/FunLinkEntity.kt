package com.anchor.adhd.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fun_links")
data class FunLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val emoji: String,
    val sortOrder: Int,
    val enabled: Boolean = true
)
