package com.anchor.adhd.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.anchor.adhd.data.model.CalendarEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads timed occupancy from [CalendarContract.Instances] on [Dispatchers.IO].
 * Never throws — permission or provider failures become an empty list.
 */
class DeviceCalendarSync(private val context: Context) {
    suspend fun queryWindow(startMillis: Long, endMillis: Long): List<CalendarEventEntity> =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext emptyList()
            }
            try {
                queryWindowLocked(startMillis, endMillis)
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_CALENDAR revoked", e)
                emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Instances query failed", e)
                emptyList()
            }
        }

    private fun queryWindowLocked(startMillis: Long, endMillis: Long): List<CalendarEventEntity> {
        val visible = visibleCalendarIds()
        if (visible.isEmpty()) return emptyList()

        val uri = instancesWindowUri(startMillis, endMillis)
        val cr = context.contentResolver
        val out = mutableListOf<CalendarEventEntity>()
        cr.query(uri, INSTANCE_PROJECTION, INSTANCE_SELECTION, INSTANCE_ARGS, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val statusIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
            val calIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            while (cursor.moveToNext()) {
                val calendarId = cursor.getLong(calIdx)
                if (!DeviceCalendarOccupancy.keep(
                        allDay = cursor.getInt(allDayIdx),
                        selfAttendeeStatus = cursor.getInt(statusIdx),
                        calendarVisible = calendarId in visible
                    )
                ) continue
                val eventId = cursor.getLong(idIdx)
                val begin = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)
                val title = cursor.getString(titleIdx).orEmpty()
                out += DeviceCalendarOccupancy.toEntity(eventId, title, begin, end)
            }
        }
        return out
    }

    private fun visibleCalendarIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            while (cursor.moveToNext()) ids += cursor.getLong(idIdx)
        }
        return ids
    }

    companion object {
        private const val TAG = "DeviceCalendarSync"

        internal fun instancesWindowUri(startMillis: Long, endMillis: Long): Uri {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            return builder.build()
        }

        private val INSTANCE_PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE
        )

        private const val INSTANCE_SELECTION =
            "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ?"

        private val INSTANCE_ARGS = arrayOf(DeviceCalendarOccupancy.STATUS_DECLINED.toString())
    }
}
