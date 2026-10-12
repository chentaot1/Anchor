package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.platform.DesktopAwakeClock
import com.anchor.adhd.desktop.DesktopDurationLimits

@kotlinx.serialization.Serializable
internal data class DesktopCountdownState(val remainingNanos: Long, val elapsedNanos: Long)

/** An awake-time countdown: late UI updates count, Windows sleep and hibernation do not. */
internal class DesktopCountdown(
    initialSeconds: Int,
    private val nanoTime: () -> Long = DesktopAwakeClock::nanoTime,
) {
    private var remainingNanos = initialSeconds.coerceAtLeast(0).toLong() * NANOS_PER_SECOND
    private var elapsedNanos = 0L
    private var lastTickNanos: Long? = null

    val remainingSeconds: Int
        get() {
            update()
            // Round up so a new session does not immediately lose its first displayed second.
            return ((remainingNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND).toInt()
        }

    val elapsedSeconds: Int
        get() {
            update()
            return (elapsedNanos / NANOS_PER_SECOND).toInt()
        }

    fun resume() {
        if (lastTickNanos == null) lastTickNanos = nanoTime()
    }

    fun pause() {
        update()
        lastTickNanos = null
    }

    fun reset(seconds: Int) {
        remainingNanos = seconds.coerceAtLeast(0).toLong() * NANOS_PER_SECOND
        elapsedNanos = 0L
        lastTickNanos = null
    }

    fun snapshot(): DesktopCountdownState {
        update()
        return DesktopCountdownState(remainingNanos, elapsedNanos)
    }

    /** Recovery always pauses; time while the process was closed is never credited. */
    fun restore(state: DesktopCountdownState) {
        require(state.remainingNanos in 0..86400L * NANOS_PER_SECOND)
        require(state.elapsedNanos in 0..Int.MAX_VALUE.toLong() * NANOS_PER_SECOND)
        remainingNanos = state.remainingNanos
        elapsedNanos = state.elapsedNanos
        lastTickNanos = null
    }

    fun adjustSeconds(delta: Int) {
        update()
        if (delta == 0) return
        // An adjustment must never reverse its direction at a boundary. Preserve existing
        // longer recovered timers, and do not add time when subtracting below one minute.
        val maximum = maxOf(remainingNanos, NANOS_PER_SECOND * DesktopDurationLimits.MAX_SECONDS)
        val minimum = if (delta < 0) minOf(remainingNanos, NANOS_PER_SECOND * DesktopDurationLimits.MIN_SECONDS)
            else NANOS_PER_SECOND * DesktopDurationLimits.MIN_SECONDS
        remainingNanos = (remainingNanos + delta.toLong() * NANOS_PER_SECOND)
            .coerceIn(minimum, maximum)
    }

    fun setTotalSeconds(seconds: Int) {
        update()
        remainingNanos = (seconds.coerceAtLeast(1).toLong() * NANOS_PER_SECOND - elapsedNanos).coerceAtLeast(0)
    }

    /** Sets a remaining-time target at this instant, preserving worked time and pause state. */
    fun setRemainingSeconds(seconds: Int) {
        update()
        remainingNanos = seconds.coerceIn(DesktopDurationLimits.MIN_SECONDS, DesktopDurationLimits.MAX_SECONDS)
            .toLong() * NANOS_PER_SECOND
    }

    private fun update() {
        val previous = lastTickNanos ?: return
        val now = nanoTime()
        val elapsed = (now - previous).coerceAtLeast(0).coerceAtMost(remainingNanos)
        elapsedNanos += elapsed
        remainingNanos -= elapsed
        lastTickNanos = now
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
