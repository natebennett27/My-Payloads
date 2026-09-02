package com.trio.today.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A repeating task. Deliberately limited to the three shapes people actually
 * speak out loud ("every day", "every Friday", "every month on the 3rd").
 *
 * Richer recurrence rules are a complexity tax (§1, §4): they demand an editor
 * screen, and every configuration screen is a procrastination trap.
 */
sealed interface Recurrence {

    /** Returns the first date strictly after [from] on which this recurrence lands. */
    fun nextAfter(from: LocalDate): LocalDate

    /** Whether this recurrence lands on [date]. */
    fun occursOn(date: LocalDate): Boolean

    data object Daily : Recurrence {
        override fun nextAfter(from: LocalDate): LocalDate = from.plusDays(1)

        override fun occursOn(date: LocalDate): Boolean = true
    }

    data class Weekly(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty()) { "Weekly recurrence needs at least one day" }
        }

        override fun nextAfter(from: LocalDate): LocalDate {
            var candidate = from.plusDays(1)
            // At most 7 steps: some day of the week always matches.
            repeat(7) {
                if (candidate.dayOfWeek in days) return candidate
                candidate = candidate.plusDays(1)
            }
            return candidate
        }

        override fun occursOn(date: LocalDate): Boolean = date.dayOfWeek in days
    }

    /**
     * Monthly on [dayOfMonth]. Months shorter than the requested day clamp to
     * the last day of that month, so "every month on the 31st" still fires in
     * February rather than silently vanishing.
     */
    data class Monthly(val dayOfMonth: Int) : Recurrence {
        init {
            require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31" }
        }

        override fun nextAfter(from: LocalDate): LocalDate {
            val thisMonth = clampToMonth(from.year, from.monthValue)
            if (thisMonth.isAfter(from)) return thisMonth
            val next = from.withDayOfMonth(1).plusMonths(1)
            return clampToMonth(next.year, next.monthValue)
        }

        override fun occursOn(date: LocalDate): Boolean =
            date == clampToMonth(date.year, date.monthValue)

        private fun clampToMonth(year: Int, month: Int): LocalDate {
            val first = LocalDate.of(year, month, 1)
            return first.withDayOfMonth(minOf(dayOfMonth, first.lengthOfMonth()))
        }
    }
}
