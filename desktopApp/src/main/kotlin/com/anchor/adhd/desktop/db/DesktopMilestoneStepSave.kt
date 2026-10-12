package com.anchor.adhd.desktop.db

internal data class DesktopMilestoneStepSave(
    val key: String,
    val sourceId: Long,
    val sourceTitle: String,
    val steps: List<String>,
    val startFirst: Boolean,
)
