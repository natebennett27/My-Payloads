# Trio — today's three things

An Android-first to-do app built to the Stage 1 MVP specification in
*The Android To-Do App Landscape for ADHD Users: Market Analysis & Design
Guidance (2026)*.

The report's strategic case: the ADHD task-app category is crowded on iOS and
genuinely underserved on Android. Tiimo left Android around September–November
2025 before rebuilding a reduced app, and the most ADHD-thoughtful newcomers
(Focus One, Ravi, Tiny Steps, Hypermonkey, Do-dono) are iPhone-only. That gap is
what this app is aimed at.

> Trio is not a medical device and does not diagnose, treat, cure, or prevent
> any medical condition.

## What it does

**Capture in under three seconds.** A home-screen widget, a launcher shortcut,
the share sheet and the Assistant's note action all open a transparent capture
sheet over whatever you were doing. Voice input is one tap. Type it how you'd
say it — *"call the dentist tomorrow at 3pm"*, *"submit report every Friday at
5pm"* — and the parser takes the time and keeps the task.

**One screen, capped.** Today holds at most five open tasks with a progress
ring. Everything else waits in Later, which you have to choose to open. No
backlog on the home screen, no badge, no counter of what you owe.

**Completion that feels like something.** Swipe a row away and you get a
two-note chime, a matched two-beat haptic and a short confetti burst. It is the
only place in the app with high-fidelity feedback, deliberately.

**Forgiving by default.** Unfinished tasks move quietly to Later overnight
rather than turning red. Nothing is ever marked overdue, there are no streaks,
and "Not right now" is one tap on every row.

**Reminders that arrive.** Exact alarms via `setAlarmClock()`, re-armed after
reboot, update and clock changes, plus a one-time wizard that detects your
phone's manufacturer and walks you to the specific battery settings that would
otherwise kill notifications silently.

Everything stays on the device. No account, no network calls, no analytics.

## Building

Requires Android Studio (Ladybug or newer) or a local Android SDK.

```bash
cd Trio
./gradlew :app:assembleDebug      # build
./gradlew :app:testDebugUnitTest  # unit tests
```

- Kotlin 2.0.21, AGP 8.7.3, Jetpack Compose (BOM 2024.12.01)
- Room, DataStore, WorkManager, Glance for the widget
- `minSdk` 26, `targetSdk` 35
- Dependencies are wired by hand in `TrioApp.kt` — four objects did not justify
  a DI framework

## Verifying the logic without an Android SDK

All the rules worth getting right — capture parsing, the overnight rollover, the
OEM guidance table — live in `com.trio.today.domain` and import nothing from
Android. They can be compiled and exercised with a plain Kotlin compiler:

```bash
tools/logic-verification/run.sh    # needs kotlinc on PATH
```

This runs 61 assertions against the same source files the app ships, and is
what caught the bug where *"every friday at 5pm"* said on a Wednesday scheduled
itself for that same afternoon. The equivalent JUnit tests in `app/src/test`
run under Gradle in the usual way.

## Layout

```
app/src/main/java/com/trio/today/
├── domain/          Pure Kotlin: task model, capture parser, rollover, OEM table
├── data/            Room database, repository, settings
├── reminder/        Exact alarms, boot re-arming, notifications, OEM guidance
├── work/            Nightly gentle-reset worker
├── widget/          Glance home-screen widget
└── ui/              Compose: Today, Later, capture sheet, reliability wizard
docs/
├── design-decisions.md   Every choice traced to its finding in the report
└── play-listing.md       Health-policy compliance and draft store copy
tools/logic-verification/ Standalone domain checks
```

## Before shipping

`docs/play-listing.md` carries the full checklist. The load-bearing items:
complete Play Console's **Health apps declaration**, keep the "not a medical
device" disclaimer in the **first paragraph** of the store description (required
by the January 2026 enforcement phase), and complete Organization verification.
Market around organisation and neurodivergent-friendly design — never clinical
treatment. The evidence base for digital ADHD interventions is low quality and
~30% of reviews report adverse effects, so efficacy claims are not defensible.

## Not built yet

Stage 1 is "nail the three priorities and nothing else". AI task breakdown, a
visual timer, task editing, monetisation and the retention analytics behind the
report's course-correction benchmarks are all deliberately absent — see the end
of `docs/design-decisions.md`.
