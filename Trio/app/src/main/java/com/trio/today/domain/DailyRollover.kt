package com.trio.today.domain

import java.time.LocalDate

/**
 * What the overnight reset should do to a single task.
 */
sealed interface RolloverAction {
    /** Task stays on Today untouched. */
    data class Keep(val task: Task) : RolloverAction

    /** Task moves quietly to Later; nothing is flagged, nothing turns red. */
    data class MoveToLater(val task: Task) : RolloverAction

    /** A recurring task's next instance is scheduled. */
    data class Reschedule(val task: Task, val nextDate: LocalDate) : RolloverAction

    /** A completed task is archived out of the way. */
    data class Archive(val task: Task) : RolloverAction
}

/**
 * The gentle overnight reset.
 *
 * This is the direct implementation of the report's strongest behavioural
 * finding (§3, §4): carried-over undone tasks must not accumulate into a
 * "Snowball of Shame". Instead of ageing on Today and sprouting an overdue
 * badge, an unfinished task is moved -- silently, with no marker -- back to the
 * Later bin, where it is simply one of many options again rather than a
 * visible failure.
 *
 * The rules:
 *  - Completed tasks are archived. The day starts clean.
 *  - Unfinished non-recurring tasks move to Later with no "overdue" state.
 *  - Recurring tasks are rescheduled to their next occurrence.
 *  - Tasks with a reminder still in the future stay on Today, because the user
 *    explicitly pinned them to a clock.
 *
 * Contains no Android dependencies so it can be unit tested on a plain JVM.
 */
object DailyRollover {

    /**
     * Decides what happens to each of [tasks] when the day turns over to [newDay].
     *
     * [nowMillis] is compared against pinned reminders so that a task the user
     * deliberately scheduled for later today survives the reset.
     */
    fun plan(tasks: List<Task>, newDay: LocalDate, nowMillis: Long): List<RolloverAction> =
        tasks.map { task ->
            when {
                task.isDone -> RolloverAction.Archive(task)

                task.recurrence != null ->
                    RolloverAction.Reschedule(task, task.recurrence.nextAfter(newDay.minusDays(1)))

                // Pinned to a clock reading that has not arrived yet: leave it be.
                task.remindAt != null && task.remindAt > nowMillis -> RolloverAction.Keep(task)

                task.bucket == Bucket.TODAY -> RolloverAction.MoveToLater(task)

                else -> RolloverAction.Keep(task)
            }
        }
}
