package com.anchor.adhd.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class HealthSnapshot(val steps: Long, val exerciseMinutes: Int)

internal interface HealthConnectAccess {
    fun sdkStatus(): Int
    suspend fun grantedPermissions(): Set<String>
    suspend fun aggregate(start: Instant, end: Instant): HealthSnapshot
}

/** Reads only today's step and exercise totals, while Anchor is in the foreground. */
class HealthConnectBridge internal constructor(
    private val access: HealthConnectAccess,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    constructor(context: Context) : this(AndroidHealthConnectAccess(context.applicationContext))

    fun sdkStatus(): Int = access.sdkStatus()
    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean =
        isAvailable() && access.grantedPermissions().containsAll(permissions)

    suspend fun readTodaySnapshot(): HealthSnapshot? {
        if (!hasPermissions()) return null
        val end = clock.instant()
        val start = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
        if (!end.isAfter(start)) return HealthSnapshot(0, 0)
        return access.aggregate(start, end)
    }

    companion object {
        val permissions: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
    }
}

private class AndroidHealthConnectAccess(private val context: Context) : HealthConnectAccess {
    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    override fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    override suspend fun grantedPermissions(): Set<String> =
        client().permissionController.getGrantedPermissions()

    override suspend fun aggregate(start: Instant, end: Instant): HealthSnapshot {
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        // Health Connect aggregation accounts for source priority and duplicate records.
        return HealthSnapshot(
            steps = (result[StepsRecord.COUNT_TOTAL] ?: 0L).coerceAtLeast(0),
            exerciseMinutes = (result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L)
                .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        )
    }
}
