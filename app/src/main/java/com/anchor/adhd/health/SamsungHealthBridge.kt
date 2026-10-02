package com.anchor.adhd.health

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthSnapshot(
    val steps: Long,
    val exerciseMinutes: Int,
)

/**
 * Direct Samsung Health Data SDK access (same AAR as Plate / Nut AI).
 * On an S24 Ultra this reads the watch/phone source instead of going through
 * Health Connect, which is often missing or unlinked.
 */
class SamsungHealthBridge(private val context: Context) {
    private val zone: ZoneId = ZoneId.systemDefault()

    private val permissions = setOf(
        Permission.of(DataTypes.STEPS, AccessType.READ),
        Permission.of(DataTypes.EXERCISE, AccessType.READ),
    )

    fun isAvailable(): Boolean = runCatching {
        HealthDataService.getStore(context)
        true
    }.getOrDefault(false)

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            val granted = HealthDataService.getStore(context).getGrantedPermissions(permissions)
            granted.containsAll(permissions)
        }.getOrDefault(false)
    }

    /**
     * Shows the Samsung Health permission sheet. If Health is missing or needs
     * an update, launches the SDK's resolution (install/update prompt).
     */
    suspend fun requestPermissions(activity: Activity): Boolean {
        val store = runCatching { HealthDataService.getStore(context) }.getOrNull()
            ?: return false
        return try {
            val granted = store.requestPermissions(permissions, activity)
            granted.containsAll(permissions)
        } catch (e: ResolvablePlatformException) {
            if (e.hasResolution) {
                runCatching { e.resolve(activity) }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readTodaySnapshot(): HealthSnapshot? {
        if (!isAvailable() || !hasPermissions()) return null
        val store = HealthDataService.getStore(context)
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toLocalDateTime()
        val end = Instant.now().atZone(zone).toLocalDateTime()
        val timeFilter = LocalTimeFilter.of(start, end)
        val dateFilter = LocalDateFilter.of(today, today)

        val steps = runCatching {
            val request = DataType.StepsType.TOTAL.requestBuilder
                .setLocalTimeFilter(timeFilter)
                .build()
            store.aggregateData(request).dataList.firstOrNull()?.value ?: 0L
        }.getOrDefault(0L)

        val exerciseMinutes = runCatching {
            val request = DataType.ExerciseType.TOTAL_DURATION.requestBuilder
                .setLocalDateFilter(dateFilter)
                .build()
            val duration = store.aggregateData(request).dataList.firstOrNull()?.value
                ?: Duration.ZERO
            duration.toMinutes().toInt().coerceAtLeast(0)
        }.getOrDefault(0)

        return HealthSnapshot(steps = steps, exerciseMinutes = exerciseMinutes)
    }
}
