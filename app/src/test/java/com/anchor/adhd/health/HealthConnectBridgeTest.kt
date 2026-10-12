package com.anchor.adhd.health

import androidx.health.connect.client.HealthConnectClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthConnectBridgeTest {
    private class FakeAccess : HealthConnectAccess {
        var status = HealthConnectClient.SDK_AVAILABLE
        var grants = HealthConnectBridge.permissions
        var queried: Pair<Instant, Instant>? = null
        var failure: Exception? = null
        override fun sdkStatus() = status
        override suspend fun grantedPermissions() = grants
        override suspend fun aggregate(start: Instant, end: Instant): HealthSnapshot {
            failure?.let { throw it }
            queried = start to end
            return HealthSnapshot(5200, 35)
        }
    }

    private fun bridge(access: FakeAccess, now: String = "2026-03-08T16:00:00Z") =
        HealthConnectBridge(access, Clock.fixed(Instant.parse(now), ZoneId.of("America/New_York")))

    @Test fun unavailableProviderDoesNotReadData() = runBlocking {
        val access = FakeAccess().apply { status = HealthConnectClient.SDK_UNAVAILABLE }
        assertNull(bridge(access).readTodaySnapshot())
        assertNull(access.queried)
    }

    @Test fun partialOrRevokedPermissionsDoNotReadOrProduceRewards() = runBlocking {
        val access = FakeAccess().apply { grants = HealthConnectBridge.permissions.take(1).toSet() }
        assertFalse(bridge(access).hasPermissions())
        assertNull(bridge(access).readTodaySnapshot())
        assertNull(access.queried)
    }

    @Test fun aggregatesFromLocalMidnightAcrossDaylightSavingChange() = runBlocking {
        val access = FakeAccess()
        assertEquals(HealthSnapshot(5200, 35), bridge(access).readTodaySnapshot())
        assertEquals(Instant.parse("2026-03-08T05:00:00Z") to Instant.parse("2026-03-08T16:00:00Z"), access.queried)
    }

    @Test fun midnightDoesNotSendAnInvalidEmptyTimeRange() = runBlocking {
        val access = FakeAccess()
        assertEquals(HealthSnapshot(0, 0), bridge(access, "2026-03-08T05:00:00Z").readTodaySnapshot())
        assertNull(access.queried)
    }

    @Test fun failedReadPropagatesInsteadOfReturningInventedTotals() = runBlocking {
        val access = FakeAccess().apply { failure = SecurityException("Permission revoked during read") }
        try {
            bridge(access).readTodaySnapshot()
            fail("Read failures must not yield a successful snapshot")
        } catch (_: SecurityException) {
            assertNull(access.queried)
        }
    }
}
