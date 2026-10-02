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

object DefaultFunLinks {
    val DEFAULTS = listOf(
        FunLinkEntity(name = "Wordle", url = "https://www.nytimes.com/games/wordle", emoji = "🟩", sortOrder = 0, enabled = true),
        FunLinkEntity(name = "Connections", url = "https://www.nytimes.com/games/connections", emoji = "🟪", sortOrder = 1, enabled = true),
        FunLinkEntity(name = "Chess", url = "https://lichess.org/training", emoji = "♟️", sortOrder = 2, enabled = true),
        FunLinkEntity(name = "Contexto", url = "https://contexto.me", emoji = "🧩", sortOrder = 3, enabled = true)
    )
}
