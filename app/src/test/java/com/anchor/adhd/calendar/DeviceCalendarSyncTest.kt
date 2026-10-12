package com.anchor.adhd.calendar

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceCalendarSyncTest {
    @Test
    fun instancesUri_usesInstancesNotEvents_andAppendsWindowIds() {
        val start = 1_700_000_000_000L
        val end = start + 86_400_000L
        val uri = DeviceCalendarSync.instancesWindowUri(start, end)
        assertTrue(uri.toString().startsWith(CalendarContract.Instances.CONTENT_URI.toString()))
        assertTrue(!uri.toString().contains("/events"))
        val segments = uri.pathSegments
        assertTrue("when" in segments)
        assertEquals(start.toString(), segments[segments.size - 2])
        assertEquals(end.toString(), segments.last())
    }
}
