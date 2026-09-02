package com.trio.today.verification

import com.trio.today.domain.Bucket
import com.trio.today.domain.CaptureParser
import com.trio.today.domain.DailyRollover
import com.trio.today.domain.OemGuidanceTable
import com.trio.today.domain.Recurrence
import com.trio.today.domain.RolloverAction
import com.trio.today.domain.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Standalone runner for the pure-domain logic.
 *
 * The Android layer needs the Android SDK to build, but everything in
 * `com.trio.today.domain` is deliberately free of Android imports, so the
 * parsing and rollover rules -- the two places a subtle bug would quietly
 * misfile a user's tasks -- can be compiled and exercised with plain kotlinc.
 *
 * Run:  see tools/logic-verification/run.sh
 */
private var failures = 0
private var checks = 0

private fun <T> expect(label: String, actual: T, expected: T) {
    checks++
    if (actual != expected) {
        failures++
        println("  FAIL  $label")
        println("        expected: $expected")
        println("        actual:   $actual")
    } else {
        println("  ok    $label")
    }
}

/** Wednesday, 2 September 2026, 10:00. */
private val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 2, 10, 0)

private fun section(name: String) = println("\n$name")

private fun main0() {
    expect("fixture day is a Wednesday", NOW.dayOfWeek, DayOfWeek.WEDNESDAY)

    section("Capture parsing - titles are stripped of time language")
    CaptureParser.parse("call the dentist tomorrow at 3pm", NOW).let {
        expect("title", it.title, "call the dentist")
        expect("remindAt", it.remindAt, LocalDateTime.of(2026, 9, 3, 15, 0))
        expect("bucket is Later (not due today)", it.bucket, Bucket.LATER)
    }
    CaptureParser.parse("pick up parcel on friday at 4pm", NOW).let {
        expect("trailing preposition removed", it.title, "pick up parcel")
        expect("upcoming Friday", it.remindAt, LocalDateTime.of(2026, 9, 4, 16, 0))
    }
    CaptureParser.parse("buy milk", NOW).let {
        expect("plain task keeps its title", it.title, "buy milk")
        expect("no invented reminder", it.remindAt, null)
        expect("lands on Today", it.bucket, Bucket.TODAY)
    }

    section("Capture parsing - relative time")
    expect(
        "in 20 minutes",
        CaptureParser.parse("email boss in 20 minutes", NOW).remindAt,
        LocalDateTime.of(2026, 9, 2, 10, 20),
    )
    expect(
        "in 3 days",
        CaptureParser.parse("wash the car in 3 days", NOW).remindAt,
        LocalDateTime.of(2026, 9, 5, 10, 0),
    )
    expect(
        "tonight defaults to 19:00",
        CaptureParser.parse("gym tonight", NOW).remindAt,
        LocalDateTime.of(2026, 9, 2, 19, 0),
    )
    expect(
        "day after tomorrow with explicit time",
        CaptureParser.parse("dentist day after tomorrow at 2:30pm", NOW).remindAt,
        LocalDateTime.of(2026, 9, 4, 14, 30),
    )

    section("Capture parsing - bare hours read the way people speak")
    expect(
        "'at 5' means the afternoon",
        CaptureParser.parse("call mum at 5", NOW).remindAt,
        LocalDateTime.of(2026, 9, 2, 17, 0),
    )
    expect(
        "'at 9' means the morning, and rolls when already past",
        CaptureParser.parse("standup at 9", NOW).remindAt,
        LocalDateTime.of(2026, 9, 3, 9, 0),
    )
    expect(
        "noon today is still ahead",
        CaptureParser.parse("meeting at noon", NOW).remindAt,
        LocalDateTime.of(2026, 9, 2, 12, 0),
    )
    expect(
        "midnight belongs to the next day",
        CaptureParser.parse("stop scrolling at midnight", NOW).remindAt,
        LocalDateTime.of(2026, 9, 3, 0, 0),
    )

    section("Capture parsing - recurrence")
    CaptureParser.parse("submit report every friday at 5pm", NOW).let {
        expect("title", it.title, "submit report")
        expect("weekly on Friday", it.recurrence, Recurrence.Weekly(setOf(DayOfWeek.FRIDAY)))
        expect("first fire", it.remindAt, LocalDateTime.of(2026, 9, 4, 17, 0))
    }
    CaptureParser.parse("take meds every day at 8am", NOW).let {
        expect("daily", it.recurrence, Recurrence.Daily)
        expect("8am already passed, so tomorrow", it.remindAt, LocalDateTime.of(2026, 9, 3, 8, 0))
    }
    CaptureParser.parse("every monday and thursday check inbox", NOW).let {
        expect("title", it.title, "check inbox")
        expect(
            "two weekdays",
            it.recurrence,
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        )
    }
    CaptureParser.parse("standup every weekday at 9:15", NOW).let {
        expect(
            "weekday shorthand",
            it.recurrence,
            Recurrence.Weekly(
                setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                )
            ),
        )
    }
    CaptureParser.parse("pay rent every month on the 3rd", NOW).let {
        expect("monthly", it.recurrence, Recurrence.Monthly(3))
        expect("next 3rd", it.remindAt, LocalDateTime.of(2026, 9, 3, 9, 0))
    }

    section("Capture parsing - weekday resolution")
    expect(
        "'next monday' from a Wednesday",
        CaptureParser.parse("book flights next monday", NOW).remindAt,
        LocalDateTime.of(2026, 9, 7, 9, 0),
    )
    expect(
        "'wednesday' today, with the hour still ahead, means today",
        CaptureParser.parse("call the bank wednesday at 4pm", NOW).remindAt,
        LocalDateTime.of(2026, 9, 2, 16, 0),
    )
    expect(
        "'next wednesday' never means today",
        CaptureParser.parse("call the bank next wednesday at 4pm", NOW).remindAt,
        LocalDateTime.of(2026, 9, 9, 16, 0),
    )

    section("Capture parsing - forgiving defaults")
    CaptureParser.parse("water the plants today", NOW).let {
        expect("no alarm invented for a bare 'today'", it.remindAt, null)
        expect("stays on Today", it.bucket, Bucket.TODAY)
        expect("title", it.title, "water the plants")
    }
    CaptureParser.parse("sort out the garage someday", NOW).let {
        expect("'someday' routes to Later", it.bucket, Bucket.LATER)
        expect("title", it.title, "sort out the garage")
    }
    CaptureParser.parse("   ", NOW).let {
        expect("blank input never throws", it.title, "")
    }
    CaptureParser.parse("at 99pm nonsense", NOW).let {
        expect("out-of-range times are left alone", it.remindAt, null)
    }

    section("Monthly clamps to short months")
    expect(
        "31st in February clamps to the 28th",
        Recurrence.Monthly(31).nextAfter(LocalDate.of(2026, 1, 31)),
        LocalDate.of(2026, 2, 28),
    )
    expect(
        "and returns to the 31st in March",
        Recurrence.Monthly(31).nextAfter(LocalDate.of(2026, 2, 28)),
        LocalDate.of(2026, 3, 31),
    )

    section("OEM reliability guidance table")
    listOf("samsung", "Xiaomi", "OnePlus", "OPPO", "realme", "vivo", "HUAWEI", "Redmi", "honor", "iqoo")
        .forEach { brand ->
            expect("$brand has guidance", OemGuidanceTable.forManufacturer(brand) != null, true)
        }
    expect(
        "matching is case-insensitive",
        OemGuidanceTable.forManufacturer("SAMSUNG"),
        OemGuidanceTable.forManufacturer("samsung"),
    )
    expect("Redmi inherits Xiaomi", OemGuidanceTable.forManufacturer("Redmi")?.brandLabel, "Xiaomi")
    expect("realme keeps its own label", OemGuidanceTable.forManufacturer("realme")?.brandLabel, "realme")
    expect(
        "realme shares OPPO's steps",
        OemGuidanceTable.forManufacturer("realme")?.steps,
        OemGuidanceTable.forManufacturer("oppo")?.steps,
    )
    expect(
        "every entry has steps and targets",
        listOf("samsung", "xiaomi", "oneplus", "oppo", "vivo", "huawei").all { brand ->
            val g = OemGuidanceTable.forManufacturer(brand)!!
            g.steps.isNotEmpty() && g.steps.none { it.isBlank() } &&
                g.settingsTargets.isNotEmpty() &&
                g.settingsTargets.all { (pkg, cls) -> pkg.isNotBlank() && cls.contains('.') }
        },
        true,
    )
    expect("unknown brands get nothing", OemGuidanceTable.forManufacturer("Google"), null)

    section("Gentle overnight rollover")
    val newDay = LocalDate.of(2026, 9, 3)
    val nowMillis = NOW.toInstant(ZoneOffset.UTC).toEpochMilli()
    val laterToday = LocalDateTime.of(2026, 9, 3, 20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    val tasks = listOf(
        Task(id = 1, title = "done thing", createdAt = 0, completedAt = 1),
        Task(id = 2, title = "unfinished thing", createdAt = 0),
        Task(id = 3, title = "recurring thing", createdAt = 0, recurrence = Recurrence.Daily),
        Task(id = 4, title = "pinned thing", createdAt = 0, remindAt = laterToday),
    )
    val plan = DailyRollover.plan(tasks, newDay, nowMillis)
    expect("completed task is archived", plan[0] is RolloverAction.Archive, true)
    expect("unfinished task moves quietly to Later", plan[1] is RolloverAction.MoveToLater, true)
    expect("recurring task is rescheduled", plan[2] is RolloverAction.Reschedule, true)
    expect(
        "rescheduled to the new day",
        (plan[2] as RolloverAction.Reschedule).nextDate,
        newDay,
    )
    expect("future-pinned task stays put", plan[3] is RolloverAction.Keep, true)
    expect(
        "nothing is ever marked overdue",
        plan.none { it.toString().contains("Overdue", ignoreCase = true) },
        true,
    )
}

fun main() {
    println("Trio domain verification")
    main0()
    println("\n$checks checks, $failures failure(s)")
    if (failures > 0) kotlin.system.exitProcess(1)
}
