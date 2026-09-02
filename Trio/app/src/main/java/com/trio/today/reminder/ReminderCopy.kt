package com.trio.today.reminder

import kotlin.random.Random

/**
 * Picks the wording for a reminder notification.
 *
 * Notification blindness is a documented ADHD failure mode (§3): identical
 * repeated reminders stop registering. Varying the phrasing keeps the
 * notification a real signal instead of wallpaper.
 *
 * Every line here is deliberately neutral or warm. None of them nag, none
 * imply lateness, and none reference a streak -- guilt language is exactly what
 * the report says drives uninstalls (§4).
 */
object ReminderCopy {

    private val OPENERS = listOf(
        "Now would be a good time",
        "Here's the one you picked",
        "Ready when you are",
        "This one's up",
        "Gentle nudge",
        "Whenever you're ready",
        "Your turn",
        "Still here for you",
    )

    private val NUDGES = listOf(
        "Two minutes is enough to start.",
        "Starting counts. Finishing is optional.",
        "You can stop after the first step.",
        "No pressure -- move it on if now isn't right.",
        "Just open the thing. That's the whole job.",
        "Small start, still a start.",
    )

    /**
     * Returns a title and body for [taskTitle].
     *
     * [seed] makes the choice deterministic for a given task and time, so the
     * same notification re-posted after a reboot does not change its wording
     * mid-flight.
     */
    fun forTask(taskTitle: String, seed: Long = System.currentTimeMillis()): Pair<String, String> {
        val random = Random(seed)
        val opener = OPENERS[random.nextInt(OPENERS.size)]
        val nudge = NUDGES[random.nextInt(NUDGES.size)]
        return "$opener: $taskTitle" to nudge
    }
}
