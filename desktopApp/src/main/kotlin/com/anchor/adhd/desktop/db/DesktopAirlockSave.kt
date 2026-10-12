package com.anchor.adhd.desktop.db

internal data class DesktopAirlockTask(val title: String, val durationMinutes: Int)

internal data class DesktopAirlockSave(
    val key: String,
    val tasks: List<DesktopAirlockTask>,
    val anchorIndex: Int? = null,
)
