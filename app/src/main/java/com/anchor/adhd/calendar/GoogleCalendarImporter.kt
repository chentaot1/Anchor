package com.anchor.adhd.calendar

import android.content.Context
import com.anchor.adhd.data.model.CalendarEventEntity
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class GoogleCalendarImporter(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun signInClient() = GoogleSignInBridge.calendarSignInClient(context, CALENDAR_SCOPE)

    suspend fun importWeek(): List<CalendarEventEntity> = withContext(Dispatchers.IO) {
        val account = GoogleSignInBridge.getLastSignedInAccount(context)
            ?: error("Not signed in to Google")
        val email = account.email ?: error("No Google account email")
        @Suppress("DEPRECATION")
        val token = GoogleAuthUtil.getToken(context, email, "oauth2:$CALENDAR_SCOPE")
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val end = LocalDate.now(zone).plusDays(7).atStartOfDay(zone).toInstant()
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
            "?singleEvents=true&orderBy=startTime" +
            "&timeMin=${start}&timeMax=${end}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Calendar API error: ${response.code}")
            val body = response.body?.string() ?: error("Empty calendar response")
            parseEvents(body, zone)
        }
    }

    private fun parseEvents(json: String, zone: ZoneId): List<CalendarEventEntity> {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: return emptyList()
        val result = mutableListOf<CalendarEventEntity>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.getString("id")
            val title = item.optString("summary", "(No title)")
            val startObj = item.getJSONObject("start")
            val endObj = item.getJSONObject("end")
            val allDay = startObj.has("date")
            val startMillis = if (allDay) {
                LocalDate.parse(startObj.getString("date")).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                Instant.parse(startObj.getString("dateTime")).toEpochMilli()
            }
            val endMillis = if (allDay) {
                LocalDate.parse(endObj.getString("date")).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                Instant.parse(endObj.getString("dateTime")).toEpochMilli()
            }
            result += CalendarEventEntity(
                id = id,
                title = title,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = allDay
            )
        }
        return result
    }

    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    }
}
