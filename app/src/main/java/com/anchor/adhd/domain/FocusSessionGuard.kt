package com.anchor.adhd.domain

import com.anchor.adhd.service.FocusTimerState

/** Reads focus timer state directly — avoids stale WhileSubscribed-derived StateFlows. */
object FocusSessionGuard {
    fun lockedTaskId(): Long? {
        val state = FocusTimerState.state.value
        if (state.phase == FocusTimerState.Phase.IDLE) return null
        return state.taskId
    }

    fun isTaskLocked(taskId: Long): Boolean = lockedTaskId() == taskId
}
