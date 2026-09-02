package com.trio.today.domain

/**
 * Where a task currently lives.
 *
 * The report's central finding about "list guilt" (§3) drives this design: a
 * task is either on TODAY (a hard-capped, visible commitment) or in LATER (an
 * unbounded, deliberately out-of-sight bin). There is no "overdue" bucket, by
 * design -- carried-over undone tasks becoming "a visible record of your
 * failures" is the exact failure mode we are avoiding.
 */
enum class Bucket {
    TODAY,
    LATER,
    DONE,
}

/**
 * How strongly a task is anchored to a clock.
 *
 * ANYTIME tasks are the default. Time is opt-in, never required -- demanding a
 * due date on every capture is setup friction, and friction is what kills
 * capture.
 */
enum class TimeAnchor {
    ANYTIME,
    AT_TIME,
}

data class Task(
    val id: Long = 0L,
    val title: String,
    val bucket: Bucket = Bucket.TODAY,
    /** Epoch millis the task was captured. Used only for ordering, never shown as "age". */
    val createdAt: Long,
    /** Epoch millis of the reminder, if the user asked for one. */
    val remindAt: Long? = null,
    val timeAnchor: TimeAnchor = TimeAnchor.ANYTIME,
    val recurrence: Recurrence? = null,
    /** Epoch millis of completion; null while open. */
    val completedAt: Long? = null,
    /**
     * How many times this task has been moved on with "Not right now".
     *
     * Tracked so the app can offer help (break it down, drop it) rather than to
     * shame the user. It is never rendered as a counter in the UI.
     */
    val deferCount: Int = 0,
    /** Position within its bucket; lower sorts first. */
    val sortOrder: Int = 0,
) {
    val isDone: Boolean get() = completedAt != null
}
