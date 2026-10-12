package com.anchor.adhd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FocusTimerState {
    enum class Phase { IDLE, WORK, BREAK }

    data class State(
        val sessionId: Long? = null,
        val taskId: Long? = null,
        val taskTitle: String? = null,
        val phase: Phase = Phase.IDLE,
        val remainingSeconds: Int = 0,
        val totalWorkSeconds: Int = 0,
        val workMinutes: Int = 20,
        val breakMinutes: Int = 5,
        val locked: Boolean = true,
        val startedAtMillis: Long = 0L,
        val fiveMinWarned: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = State()
    }
}

object PendingPostFocus {
    private val _value = MutableStateFlow<com.anchor.adhd.data.model.PostFocusSummary?>(null)
    val value: StateFlow<com.anchor.adhd.data.model.PostFocusSummary?> = _value.asStateFlow()

    fun set(summary: com.anchor.adhd.data.model.PostFocusSummary?) {
        _value.value = summary
    }
}
