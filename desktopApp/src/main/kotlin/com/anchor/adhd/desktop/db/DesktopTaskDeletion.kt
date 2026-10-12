package com.anchor.adhd.desktop.db

/** A deletion owns both its task snapshot and the private save identities removed by the cascade. */
internal data class DesktopTaskDeletion(
    val tasks: List<DesktopTask>,
    val captures: List<DesktopDeletedCapture>,
)

internal data class DesktopDeletedCapture(val key: String, val taskId: Long, val title: String)
