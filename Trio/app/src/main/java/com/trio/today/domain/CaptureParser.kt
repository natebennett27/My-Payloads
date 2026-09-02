package com.trio.today.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The result of reading a raw captured phrase.
 *
 * [title] is the phrase with every recognised time/recurrence token removed, so
 * "call the dentist tomorrow at 3pm" stores the task as "call the dentist".
 */
data class ParsedCapture(
    val title: String,
    val remindAt: LocalDateTime? = null,
    val recurrence: Recurrence? = null,
    val bucket: Bucket = Bucket.TODAY,
) {
    val timeAnchor: TimeAnchor
        get() = if (remindAt != null) TimeAnchor.AT_TIME else TimeAnchor.ANYTIME
}

/**
 * Parses spoken/typed capture phrases into a task.
 *
 * This exists to serve the report's first priority -- frictionless capture,
 * sub-three-second entry (§4, Stage 1). Natural-language dates are what let a
 * user dump a whole thought in one breath instead of tapping through a date
 * picker, and Todoist's parser is explicitly named as the bar to clear.
 *
 * Deliberate design rules:
 *  - Parsing never fails. An unrecognised phrase becomes a plain ANYTIME task
 *    rather than an error, because an error at capture time loses the thought.
 *  - Time is only ever attached when the user actually said something about
 *    time. We never invent a due date.
 *  - Rules are predictable over clever. An ADHD user needs to trust where a
 *    task landed without re-reading it.
 *
 * Contains no Android dependencies so it can be unit tested on a plain JVM.
 */
object CaptureParser {

    /** Time assumed when a user names a day but no clock time ("call mum tomorrow"). */
    val DEFAULT_TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)

    /** Time assumed for "tonight". */
    private val EVENING = LocalTime.of(19, 0)

    private val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
        put("monday", DayOfWeek.MONDAY); put("mon", DayOfWeek.MONDAY)
        put("tuesday", DayOfWeek.TUESDAY); put("tues", DayOfWeek.TUESDAY); put("tue", DayOfWeek.TUESDAY)
        put("wednesday", DayOfWeek.WEDNESDAY); put("weds", DayOfWeek.WEDNESDAY); put("wed", DayOfWeek.WEDNESDAY)
        put("thursday", DayOfWeek.THURSDAY); put("thurs", DayOfWeek.THURSDAY); put("thur", DayOfWeek.THURSDAY); put("thu", DayOfWeek.THURSDAY)
        put("friday", DayOfWeek.FRIDAY); put("fri", DayOfWeek.FRIDAY)
        put("saturday", DayOfWeek.SATURDAY); put("sat", DayOfWeek.SATURDAY)
        put("sunday", DayOfWeek.SUNDAY); put("sun", DayOfWeek.SUNDAY)
    }

    private const val WEEKDAY_ALT =
        "monday|mon|tuesday|tues|tue|wednesday|weds|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun"

    // --- Recurrence ------------------------------------------------------

    private val EVERY_WEEKDAY = Regex("""\bevery\s+weekday\b""")
    private val EVERY_DAY = Regex("""\b(?:every\s*day|everyday|daily)\b""")
    private val EVERY_WEEKDAYS = Regex(
        """\bevery\s+((?:$WEEKDAY_ALT)(?:\s*(?:,|and|&|/)\s*(?:$WEEKDAY_ALT))*)\b"""
    )
    private val EVERY_MONTH_ON = Regex(
        """\b(?:every\s+month|monthly)(?:\s+on)?(?:\s+the)?\s+(\d{1,2})(?:st|nd|rd|th)?\b"""
    )
    private val EVERY_MONTH = Regex("""\b(?:every\s+month|monthly)\b""")
    private val EVERY_WEEK = Regex("""\b(?:every\s+week|weekly)\b""")

    // --- Absolute / relative days ---------------------------------------

    private val IN_DURATION = Regex("""\bin\s+(\d{1,3})\s*(minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w)\b""")
    private val DAY_AFTER_TOMORROW = Regex("""\bday\s+after\s+tomorrow\b""")
    private val TOMORROW = Regex("""\b(?:tomorrow|tmr|tmrw)\b""")
    private val TONIGHT = Regex("""\b(?:tonight|this\s+evening)\b""")
    private val TODAY_WORD = Regex("""\btoday\b""")
    private val NAMED_WEEKDAY = Regex("""\b(?:(next|this)\s+)?($WEEKDAY_ALT)\b""")

    // --- Clock times -----------------------------------------------------

    private val NOON = Regex("""\b(?:at\s+)?noon\b""")
    private val MIDNIGHT = Regex("""\b(?:at\s+)?midnight\b""")
    private val TIME_MERIDIEM = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
    private val TIME_24H = Regex("""\b(?:at\s+)?(\d{1,2}):(\d{2})\b""")
    private val TIME_BARE_HOUR = Regex("""\bat\s+(\d{1,2})\b""")

    // --- Bucket hints ----------------------------------------------------

    private val LATER_HINT = Regex("""\b(?:later|someday|some\s+day|sometime|eventually)\b""")

    private val CONNECTOR_EDGE = Regex("""^(?:at|on|by|in|to|for|the|a|an|and|,|-|–|:)\b\s*|\s*\b(?:at|on|by|in|to|for|the|and|,|-|–|:)$""")

    fun parse(raw: String, now: LocalDateTime): ParsedCapture {
        val text = raw.trim()
        if (text.isEmpty()) return ParsedCapture(title = "")

        val lower = text.lowercase()
        val consumed = BooleanArray(text.length)

        // Order matters: recurrence phrases contain day and time words
        // ("every friday at 5pm"), so they must claim their span first.
        val recurrence = parseRecurrence(lower, consumed, now.toLocalDate())
        val time = parseTimeOfDay(lower, consumed)
        val dayResolver = parseDay(lower, consumed, now, time)
        val laterHinted = claim(LATER_HINT, lower, consumed) != null

        val remindAt = resolveRemindAt(now, dayResolver, time, recurrence)

        val title = cleanTitle(text, consumed)

        // An explicit "later" wins; otherwise anything with a future clock time
        // beyond today still starts life on Today only if it is due today.
        val bucket = when {
            laterHinted -> Bucket.LATER
            remindAt != null && remindAt.toLocalDate().isAfter(now.toLocalDate()) -> Bucket.LATER
            else -> Bucket.TODAY
        }

        return ParsedCapture(
            title = title.ifEmpty { text },
            remindAt = remindAt,
            recurrence = recurrence,
            bucket = bucket,
        )
    }

    // ---------------------------------------------------------------------

    private fun parseRecurrence(lower: String, consumed: BooleanArray, today: LocalDate): Recurrence? {
        claim(EVERY_WEEKDAY, lower, consumed)?.let {
            return Recurrence.Weekly(
                setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                )
            )
        }
        claim(EVERY_DAY, lower, consumed)?.let { return Recurrence.Daily }
        claim(EVERY_MONTH_ON, lower, consumed)?.let { m ->
            val day = m.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) return Recurrence.Monthly(day)
        }
        claim(EVERY_WEEKDAYS, lower, consumed)?.let { m ->
            val days = Regex(WEEKDAY_ALT).findAll(m.groupValues[1])
                .mapNotNull { WEEKDAYS[it.value] }
                .toSet()
            if (days.isNotEmpty()) return Recurrence.Weekly(days)
        }
        claim(EVERY_MONTH, lower, consumed)?.let { return Recurrence.Monthly(today.dayOfMonth) }
        claim(EVERY_WEEK, lower, consumed)?.let { return Recurrence.Weekly(setOf(today.dayOfWeek)) }
        return null
    }

    /**
     * A resolved clock time plus whether the user actually stated one. "tomorrow"
     * with no time still needs a time to fire at, but we must not treat that
     * default as the user having asked for 09:00 specifically.
     */
    private data class TimeResult(val time: LocalTime, val rollsToNextDay: Boolean = false)

    private fun TimeResult?.rollsToNextDay(): Boolean = this?.rollsToNextDay == true

    private fun parseTimeOfDay(lower: String, consumed: BooleanArray): TimeResult? {
        claim(NOON, lower, consumed)?.let { return TimeResult(LocalTime.NOON) }
        claim(MIDNIGHT, lower, consumed)?.let { return TimeResult(LocalTime.MIDNIGHT, rollsToNextDay = true) }
        claim(TIME_MERIDIEM, lower, consumed)?.let { m ->
            val rawHour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (rawHour !in 1..12 || minute !in 0..59) return@let
            val pm = m.groupValues[3] == "pm"
            val hour = when {
                pm && rawHour < 12 -> rawHour + 12
                !pm && rawHour == 12 -> 0
                else -> rawHour
            }
            return TimeResult(LocalTime.of(hour, minute))
        }
        claim(TIME_24H, lower, consumed)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour in 0..23 && minute in 0..59) return TimeResult(LocalTime.of(hour, minute))
        }
        claim(TIME_BARE_HOUR, lower, consumed)?.let { m ->
            val hour = m.groupValues[1].toInt()
            if (hour in 0..23) return TimeResult(LocalTime.of(waking12HourReading(hour), 0))
        }
        return null
    }

    /**
     * Reads a bare hour the way a person means it. "at 5" is five in the
     * afternoon, not five in the morning; "at 9" is nine in the morning.
     *
     * A fixed reading beats a clever one here: the user needs to predict where
     * the task landed without opening it.
     */
    private fun waking12HourReading(hour: Int): Int = when (hour) {
        in 1..6 -> hour + 12
        else -> hour
    }

    /** How the phrase pinned a calendar day, if it did. */
    private sealed interface DayHint {
        data class Exact(val date: LocalDate) : DayHint
        data class Instant(val at: LocalDateTime) : DayHint
    }

    private fun parseDay(
        lower: String,
        consumed: BooleanArray,
        now: LocalDateTime,
        time: TimeResult?,
    ): DayHint? {
        claim(IN_DURATION, lower, consumed)?.let { m ->
            val amount = m.groupValues[1].toLong()
            val unit = m.groupValues[2]
            val at = when {
                unit.startsWith("m") && !unit.startsWith("mo") -> now.plusMinutes(amount)
                unit.startsWith("h") -> now.plusHours(amount)
                unit.startsWith("d") -> now.plusDays(amount)
                unit.startsWith("w") -> now.plusWeeks(amount)
                else -> return@let
            }
            return DayHint.Instant(at)
        }
        claim(DAY_AFTER_TOMORROW, lower, consumed)?.let { return DayHint.Exact(now.toLocalDate().plusDays(2)) }
        claim(TOMORROW, lower, consumed)?.let { return DayHint.Exact(now.toLocalDate().plusDays(1)) }
        claim(TONIGHT, lower, consumed)?.let {
            return DayHint.Instant(now.toLocalDate().atTime(time?.time ?: EVENING))
        }
        claim(TODAY_WORD, lower, consumed)?.let { return DayHint.Exact(now.toLocalDate()) }
        claim(NAMED_WEEKDAY, lower, consumed)?.let { m ->
            val target = WEEKDAYS[m.groupValues[2]] ?: return@let
            val forceFuture = m.groupValues[1] == "next"
            return DayHint.Exact(nextWeekday(now, target, time, forceFuture))
        }
        return null
    }

    /**
     * "friday" means the soonest Friday that has not already passed -- today
     * counts when the stated time is still ahead. "next friday" never means
     * today, which is the one place the two forms differ.
     */
    private fun nextWeekday(
        now: LocalDateTime,
        target: DayOfWeek,
        time: TimeResult?,
        forceFuture: Boolean,
    ): LocalDate {
        val today = now.toLocalDate()
        val todayQualifies = today.dayOfWeek == target &&
            !forceFuture &&
            (time == null || today.atTime(time.time).isAfter(now))
        if (todayQualifies) return today

        var candidate = today.plusDays(1)
        repeat(7) {
            if (candidate.dayOfWeek == target) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    private fun resolveRemindAt(
        now: LocalDateTime,
        day: DayHint?,
        time: TimeResult?,
        recurrence: Recurrence?,
    ): LocalDateTime? {
        // "in 20 minutes" already carries a full instant.
        if (day is DayHint.Instant) return day.at

        val date = when (day) {
            is DayHint.Exact -> day.date
            else -> null
        }

        return when {
            date != null && time != null -> {
                val base = date.atTime(time.time)
                if (time.rollsToNextDay) base.plusDays(1) else base
            }

            // A day with no stated time fires at the default hour. "today" is
            // the exception: the user said when, not what time, so pinning an
            // alarm to a 09:00 that may already have passed would either fire
            // instantly or never. It stays an anytime task on Today.
            date != null -> {
                val at = date.atTime(DEFAULT_TIME_OF_DAY)
                if (at.isAfter(now)) at else null
            }

            // A recurring task names its own day, so the first fire is the next
            // occurrence -- not simply the next time that clock reading comes
            // round. "every friday at 5pm" said on a Wednesday means Friday.
            recurrence != null -> {
                val timeOfDay = time?.time ?: DEFAULT_TIME_OF_DAY
                val today = now.toLocalDate()
                val todayWorks = recurrence.occursOn(today) &&
                    !time.rollsToNextDay() &&
                    today.atTime(timeOfDay).isAfter(now)
                val day = if (todayWorks) today else recurrence.nextAfter(today)
                day.atTime(timeOfDay)
            }

            // A time with no day means the next time that clock reading occurs.
            time != null -> {
                val todayAt = now.toLocalDate().atTime(time.time)
                if (time.rollsToNextDay || !todayAt.isAfter(now)) {
                    todayAt.plusDays(1)
                } else {
                    todayAt
                }
            }

            else -> null
        }
    }

    // ---------------------------------------------------------------------

    /**
     * Finds the first match of [regex] that does not overlap an already-claimed
     * span, marks that span consumed, and returns the match.
     */
    private fun claim(regex: Regex, lower: String, consumed: BooleanArray): MatchResult? {
        var match = regex.find(lower)
        while (match != null) {
            val range = match.range
            if (range.none { consumed[it] }) {
                for (i in range) consumed[i] = true
                return match
            }
            match = match.next()
        }
        return null
    }

    /** Rebuilds the title from the characters no rule claimed. */
    private fun cleanTitle(original: String, consumed: BooleanArray): String {
        val kept = buildString {
            original.forEachIndexed { index, ch ->
                append(if (consumed[index]) ' ' else ch)
            }
        }
        var result = kept.replace(Regex("""\s+"""), " ").trim()
        // Strip prepositions and punctuation stranded by the removals, e.g.
        // "call mum on" once "friday" is taken out.
        var previous: String
        do {
            previous = result
            result = CONNECTOR_EDGE.replace(result, "").trim().trim(',', '-', ':').trim()
        } while (result != previous)
        return result
    }
}
