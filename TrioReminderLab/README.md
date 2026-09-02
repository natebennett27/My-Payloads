# Trio Reminder Lab

A small Expo (React Native) app with one job: **measure whether
`expo-notifications` delivers reminders reliably on your real Android devices**
— before committing the full ADHD to-do app to React Native.

## Why this exists

The market report behind this project names Android reminder reliability the
**"hidden killer"** and a **P0 requirement**: OEM battery managers on Samsung,
Xiaomi, OnePlus and Oppo silently delay or drop scheduled notifications, and for
an ADHD reminder app a reminder that never arrives reads as *"the app stopped
working"* — the single most common reason these apps get uninstalled.

The native Kotlin app in [`../Trio`](../Trio) solves this with
`AlarmManager.setAlarmClock()`, the strongest exact-alarm tier Android offers.
The open question for an Expo rebuild is whether `expo-notifications` — which
can't reach `setAlarmClock` — clears the same bar. **That question is cheaper to
answer with this 400-line harness than by rebuilding the whole app and finding
out.** Hence the risk-first spike.

## The verdict it produces

Two numbers decide it, shown live in the app:

- **Delivery rate** — of the reminders that resolved, how many actually arrived.
  Anything below ~95% on a target device means Expo alone is not enough.
- **Drift** — how late they were. A median beyond a minute or two, or a bad
  worst-case, is a fail for time-anchored reminders.

If a device scores well *with battery optimisation left on*, Expo is viable for
that OEM. If it only scores well *after* the battery-settings steps, the real
app will need the same setup wizard the native version already has. If it fails
even then, that OEM needs native code (a dev-client module) or the project stays
native.

## Running it

Requires Node and the Expo tooling. A **physical Android device** — ideally the
aggressive-OEM ones (Samsung, Xiaomi, …) — is the whole point; an emulator will
not reproduce the battery-manager behaviour under test.

```bash
cd TrioReminderLab
npm install

# expo-notifications scheduling needs a real build, not Expo Go (SDK 53+ removed
# remote-push support from Expo Go, and exact scheduling is most trustworthy in a
# dev client or a preview build). Build one with EAS:
npx eas-cli build --profile development --platform android
# then install the APK on the device and:
npx expo start --dev-client

# A quick local check of the scheduling API is also possible in Expo Go:
npx expo start   # scan the QR with the Expo Go app
```

### The test protocol

1. Open the app; grant notifications when asked.
2. Follow any manufacturer steps shown in **This device**, and/or open the
   battery settings and mark the app unrestricted.
3. Tap **1 min** to confirm the basics work.
4. Tap **30 min**, **1 hour**, **2 hours** — then **lock the phone, swipe the app
   out of recents, and leave it.** This is the real test; a foregrounded app
   never reproduces the failure.
5. When a reminder fires, tap it (auto-recorded) or mark **It arrived** /
   **Never came** by hand.
6. Read the delivery rate and drift. Repeat with battery optimisation both on
   and off to see how much the OEM settings matter.

## What's verified vs not

- ✅ Typechecks against the real Expo SDK 52 types (`npm run typecheck`)
- ✅ 10 unit tests pass (`npm test`) — the ported OEM table and the stats math
- ❌ Not run on a device or emulator here; that is the tester's job, and the
  point

## Layout

```
App.tsx                      The lab UI: schedule, device card, stats, log
src/reminders/
  scheduler.ts               Schedules a test reminder (the operation under test)
  channels.ts                High-importance Android channel
  log.ts                     Durable log, survives the app being killed
  stats.ts                   Delivery rate + drift — the verdict
  types.ts                   ReminderRecord and drift
src/oem/
  guidance.ts                OEM battery steps, ported from the native app
  deviceReliability.ts       Manufacturer detection + open-settings intents
src/__tests__/               Jest tests for the pure logic
```

## If the verdict is "good enough"

The next step is the full Expo rebuild: port the natural-language capture parser
and the gentle-rollover rules (both already pure, both already unit-tested in the
native app) to TypeScript, then rebuild the Today/Later/capture UI in React
Native. The home-screen widget has no first-class Expo equivalent and would be
the one remaining native piece.

## If it isn't

Stay on the native Kotlin app in [`../Trio`](../Trio), which is already built to
the report's Stage 1 spec.
