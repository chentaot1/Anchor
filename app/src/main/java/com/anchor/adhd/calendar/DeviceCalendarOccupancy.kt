package com.anchor.adhd.calendar

import com.anchor.adhd.data.model.CalendarEventEntity

/**
 * Filters and ids for device-calendar occupancy. All-day rows and declined
 * attendance are not walls; hidden calendars are ignored.
 */
object DeviceCalendarOccupancy {
    const val SOURCE = "device"

    /** [android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED] */
    const val STATUS_DECLINED = 2

    /** [android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_NONE] */
    const val STATUS_NONE = 0

    /** [android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED] */
    const val STATUS_ACCEPTED = 1

    fun keep(allDay: Int, selfAttendeeStatus: Int, calendarVisible: Boolean): Boolean {
        if (!calendarVisible) return false
        if (allDay != 0) return false
        if (selfAttendeeStatus == STATUS_DECLINED) return false
        return true
    }

    fun id(eventId: Long, beginMillis: Long): String = "$SOURCE:$eventId:$beginMillis"

    fun eventId(id: String): Long? {
        val parts = id.split(':')
        return if (parts.size == 3 && parts[0] == SOURCE) parts[1].toLongOrNull() else null
    }

    fun toEntity(
        eventId: Long,
        title: String,
        beginMillis: Long,
        endMillis: Long
    ): CalendarEventEntity = CalendarEventEntity(
        id = id(eventId, beginMillis),
        title = title.ifBlank { "(No title)" },
        startMillis = beginMillis,
        endMillis = endMillis,
        source = SOURCE,
        isAllDay = false
    )
}
