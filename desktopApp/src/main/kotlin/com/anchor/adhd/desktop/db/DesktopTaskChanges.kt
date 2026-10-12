package com.anchor.adhd.desktop.db

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.sql.SQLException

/** Share the app's reservation and keep task acknowledgement and undo bookkeeping together. */
internal class DesktopTaskChanges(
    private val operations: DesktopDataOperations,
    private val allowed: () -> Boolean,
) {
    fun start(label: String, action: suspend () -> Unit): Boolean = operations.start(
        label,
        allowed = allowed,
        deniedMessage = "Wait for timer recovery or a pending save to finish, then try again.",
    ) {
        // Screen navigation cannot discard a result. Once begun, closing the owner cannot
        // interrupt the acknowledgement between a committed deletion and its Undo entry.
        withContext(NonCancellable) {
            try {
                action()
            } catch (failure: SQLException) {
                throw IllegalStateException("Your milestones are still available. Check the list and try again.", failure)
            }
        }
        null
    }
}
