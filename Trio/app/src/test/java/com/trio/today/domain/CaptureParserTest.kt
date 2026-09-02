package com.trio.today.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Capture parsing rules.
 *
 * The same cases run outside Gradle via tools/logic-verification/run.sh, which
 * needs only kotlinc -- useful when the Android SDK is not available.
 */
class CaptureParserTest {

    /** Wednesday, 2 September 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 9, 2, 10, 0)

    @Test
    fun `strips time language from the title`() {
        val result = CaptureParser.parse("call the dentist tomorrow at 3pm", now)
        assertEquals("call the dentist", result.title)
        assertEquals(LocalDateTime.of(2026, 9, 3, 15, 0), result.remindAt)
    }

    @Test
    fun `strips a stranded preposition`() {
        assertEquals(
            "pick up parcel",
            CaptureParser.parse("pick up parcel on friday at 4pm", now).title,
        )
    }

    @Test
    fun `never invents a reminder`() {
        val result = CaptureParser.parse("buy milk", now)
        assertEquals("buy milk", result.title)
        assertNull(result.remindAt)
        assertEquals(Bucket.TODAY, result.bucket)
    }

    @Test
    fun `understands relative durations`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 2, 10, 20),
            CaptureParser.parse("email boss in 20 minutes", now).remindAt,
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 5, 10, 0),
            CaptureParser.parse("wash the car in 3 days", now).remindAt,
        )
    }

    @Test
    fun `tonight is the evening`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 2, 19, 0),
            CaptureParser.parse("gym tonight", now).remindAt,
        )
    }

    @Test
    fun `a bare hour reads the way people speak it`() {
        // "at 5" is the afternoon.
        assertEquals(
            LocalDateTime.of(2026, 9, 2, 17, 0),
            CaptureParser.parse("call mum at 5", now).remindAt,
        )
        // "at 9" is the morning, and rolls to tomorrow once today's has passed.
        assertEquals(
            LocalDateTime.of(2026, 9, 3, 9, 0),
            CaptureParser.parse("standup at 9", now).remindAt,
        )
    }

    @Test
    fun `midnight belongs to the next day`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 3, 0, 0),
            CaptureParser.parse("stop scrolling at midnight", now).remindAt,
        )
    }

    @Test
    fun `a recurring task first fires on its own day, not today`() {
        // Regression: "every friday at 5pm" said on a Wednesday must not fire
        // at 5pm the same afternoon.
        val result = CaptureParser.parse("submit report every friday at 5pm", now)
        assertEquals("submit report", result.title)
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.FRIDAY)), result.recurrence)
        assertEquals(LocalDateTime.of(2026, 9, 4, 17, 0), result.remindAt)
    }

    @Test
    fun `daily recurrence rolls past an hour already gone`() {
        val result = CaptureParser.parse("take meds every day at 8am", now)
        assertEquals(Recurrence.Daily, result.recurrence)
        assertEquals(LocalDateTime.of(2026, 9, 3, 8, 0), result.remindAt)
    }

    @Test
    fun `understands several weekdays at once`() {
        val result = CaptureParser.parse("every monday and thursday check inbox", now)
        assertEquals("check inbox", result.title)
        assertEquals(
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            result.recurrence,
        )
    }

    @Test
    fun `understands every weekday`() {
        assertEquals(
            Recurrence.Weekly(
                setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                )
            ),
            CaptureParser.parse("standup every weekday at 9:15", now).recurrence,
        )
    }

    @Test
    fun `understands a monthly day`() {
        val result = CaptureParser.parse("pay rent every month on the 3rd", now)
        assertEquals(Recurrence.Monthly(3), result.recurrence)
        assertEquals(LocalDateTime.of(2026, 9, 3, 9, 0), result.remindAt)
    }

    @Test
    fun `next weekday never resolves to today`() {
        // Plain "wednesday" on a Wednesday, with the hour still ahead, is today.
        assertEquals(
            LocalDateTime.of(2026, 9, 2, 16, 0),
            CaptureParser.parse("call the bank wednesday at 4pm", now).remindAt,
        )
        // "next wednesday" is a week out.
        assertEquals(
            LocalDateTime.of(2026, 9, 9, 16, 0),
            CaptureParser.parse("call the bank next wednesday at 4pm", now).remindAt,
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 7, 9, 0),
            CaptureParser.parse("book flights next monday", now).remindAt,
        )
    }

    @Test
    fun `a bare today does not invent an alarm`() {
        val result = CaptureParser.parse("water the plants today", now)
        assertEquals("water the plants", result.title)
        assertNull(result.remindAt)
        assertEquals(Bucket.TODAY, result.bucket)
    }

    @Test
    fun `someday routes to Later`() {
        val result = CaptureParser.parse("sort out the garage someday", now)
        assertEquals("sort out the garage", result.title)
        assertEquals(Bucket.LATER, result.bucket)
    }

    @Test
    fun `never throws on junk input`() {
        assertEquals("", CaptureParser.parse("   ", now).title)
        assertNull(CaptureParser.parse("at 99pm nonsense", now).remindAt)
    }

    @Test
    fun `monthly clamps to short months`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            Recurrence.Monthly(31).nextAfter(LocalDate.of(2026, 1, 31)),
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            Recurrence.Monthly(31).nextAfter(LocalDate.of(2026, 2, 28)),
        )
    }
}
