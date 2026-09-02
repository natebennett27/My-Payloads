package com.trio.today.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats reminder times for display.
 *
 * Phrasing is always neutral. There is no "overdue", no "late", no "2 days
 * ago" -- the report is explicit that overdue framing is a primary abandonment
 * driver (§4), so a time that has passed simply reads as the time it was.
 */
object TimeFormat {

    fun reminderLabel(
        context: Context,
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        val time = dateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

        return when (dateTime.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "Tomorrow, $time"
            else -> {
                val day = dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                "$day, $time"
            }
        }
    }
}
