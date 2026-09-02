# Design decisions, traced to the report

Every non-obvious choice in this codebase exists because of a specific finding.
This file is the map between the two, so a future change can tell whether it is
touching a preference or a load-bearing constraint.

## The three priorities (Stage 1 scope)

| Priority | Where it lives |
|---|---|
| 1. Frictionless capture | `ui/capture/`, `widget/`, `domain/CaptureParser.kt`, launcher shortcut, Assistant + share-sheet intents |
| 2. At-a-glance progress | `ui/components/ProgressRing.kt`, `ui/today/TodayScreen.kt`, the Today cap in `TaskRepository` |
| 3. Satisfying completion | `ui/components/CompletionFeedback.kt`, `Celebration.kt`, `TaskRow.kt`, `res/raw/complete.wav` |

## Decisions and their sources

**Today is capped at 5 open tasks.**
§3, "list guilt": *"If the list has 40 items and you finish 3, the visual weight
of the remaining 37 overwhelms the satisfaction of completing 3. Net dopamine:
negative."* The cap is enforced in `TaskRepository.capture`, which never rejects
input — an over-cap task still saves, it just lands in Later.

**Nothing is ever marked overdue. There is no red.**
§4, avoid list: guilt-inducing overdue red counters. The palette in
`ui/theme/Color.kt` deliberately contains no error red for task state, and
`TimeFormat` has no "late" phrasing. Unfinished tasks are moved by
`DailyRollover`, not flagged.

**Unfinished tasks move to Later overnight, silently.**
§4: *"Let incomplete tasks reset gently rather than accumulate as a 'Snowball of
Shame.'"* Implemented in `domain/DailyRollover.kt`, run both by
`work/DailyRolloverWorker` and again on app launch, so an OEM-killed worker
cannot leave a stale list.

**"Not right now" is on every row, with no penalty and no counter shown.**
§2, Focus One's "Too Hard Right Now" button and its explicit "No streaks. No
guilt mechanics." `deferCount` is tracked on the model but never rendered.

**Exact alarms via `setAlarmClock()`, plus a boot receiver.**
§6, called "the hidden killer" and "existential" for a reminder app. Standard
background jobs "will let you down". See `reminder/AlarmScheduler.kt` for why
`setAlarmClock` specifically, over `setExactAndAllowWhileIdle`.

**A brand-detecting battery-optimisation wizard.**
§6: *"plan an in-app setup wizard that detects the device brand."* The mapping
is in `domain/OemGuidance.kt` (pure, unit tested); the Android half is
`reminder/OemBatteryGuidance.kt`. This is the one place the near-zero-setup rule
is broken, and `ui/setup/ReliabilityScreen.kt` explains why in a comment.

**Reminder wording varies.**
§3, notification blindness: users tune out repeated identical reminders.
`reminder/ReminderCopy.kt` rotates openers and nudges, seeded by task id so a
re-posted notification does not change wording mid-flight.

**The highest-fidelity feedback is spent only on completion.**
§4 and the Peak-End rule: *"Save your highest-fidelity animation and haptic for
the completion 'success moment'."* Capture and navigation are deliberately plain.

**Swipe-to-complete rather than a checkbox.**
§4: *"Swipe-to-complete + haptic is the minimum viable delight."* It is also a
large, imprecise target, which matters when someone is moving fast.

**Almost no settings, and no onboarding beyond the reliability wizard.**
§3, the setup trap: *"Building your system IS the procrastination... The more
complex the system, the more executive function it demands."* `SettingsStore`
holds four values, three of which are booleans with sensible defaults.

**No nested projects, tags, priorities or sub-tasks.**
§4, avoid list: complex nested hierarchies. Also §1's read on Notion: *"Notion's
setup became its own procrastination project."*

**Recurrence is limited to three shapes.**
Daily, weekly-on-days, monthly-on-a-date — the forms people say out loud.
Anything richer needs an editor screen, which is a procrastination trap.

**Gamification is limited to a confetti burst.**
§2's warning about Habitica: the RPG layer is a maintenance burden and
health-loss mechanics are punitive. §Stage 3 says copy Finch's non-punitive
model instead. A burst that costs nothing and asks nothing is the safe form.

**Everything stays on-device.**
Not a report finding, but it removes the account, the sign-up screen and the
privacy questions — all of them friction before first use.

## Deliberately not built

Stage 1 is "nail your three priorities and nothing else". These are Stage 2/3
items from the report, left undone on purpose:

- **AI task breakdown** (Goblin Tools-style micro-steps) — Stage 2. The largest
  single differentiator, and the one that needs a model and a cost model.
- **Visual/countdown timer for time blindness** — Stage 2.
- **Body doubling** — mentioned in §3, not in any recommended stage.
- **Subscription, free tier, lifetime option** — Stage 3. The report's price
  band is $3–5/mo or $30–40/yr, undercutting Todoist Pro ($60/yr).
- **Task editing** — tapping a row is currently a no-op. Rename and reminder
  APIs exist on `TaskRepository`; only the edit sheet is missing.
- **Analytics for the report's benchmarks** — Day-7 retention below ~25% or
  median tasks-added-per-active-day below ~2 are the stated signals to change
  course, and nothing here measures them. Any solution must not compromise the
  on-device, no-tracking position above.
