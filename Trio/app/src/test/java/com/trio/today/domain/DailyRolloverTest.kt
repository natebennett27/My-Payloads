package com.trio.today.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DailyRolloverTest {

    private val newDay = LocalDate.of(2026, 9, 3)
    private val nowMillis =
        LocalDateTime.of(2026, 9, 2, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `completed tasks are archived`() {
        val plan = DailyRollover.plan(
            listOf(Task(id = 1, title = "done", createdAt = 0, completedAt = 1)),
            newDay,
            nowMillis,
        )
        assertTrue(plan.single() is RolloverAction.Archive)
    }

    @Test
    fun `unfinished tasks move quietly to Later rather than going overdue`() {
        val plan = DailyRollover.plan(
            listOf(Task(id = 2, title = "unfinished", createdAt = 0)),
            newDay,
            nowMillis,
        )
        assertTrue(plan.single() is RolloverAction.MoveToLater)
    }

    @Test
    fun `recurring tasks are rescheduled to the new day`() {
        val plan = DailyRollover.plan(
            listOf(Task(id = 3, title = "meds", createdAt = 0, recurrence = Recurrence.Daily)),
            newDay,
            nowMillis,
        )
        val action = plan.single()
        assertTrue(action is RolloverAction.Reschedule)
        assertEquals(newDay, (action as RolloverAction.Reschedule).nextDate)
    }

    @Test
    fun `a task pinned to a future time stays on Today`() {
        val laterToday =
            LocalDateTime.of(2026, 9, 3, 20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val plan = DailyRollover.plan(
            listOf(Task(id = 4, title = "pinned", createdAt = 0, remindAt = laterToday)),
            newDay,
            nowMillis,
        )
        assertTrue(plan.single() is RolloverAction.Keep)
    }
}
