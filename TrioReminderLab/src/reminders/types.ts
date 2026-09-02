/**
 * One scheduled test reminder and, once it arrives, how it actually behaved.
 *
 * The whole point of this app is the gap between `scheduledFor` and
 * `deliveredAt`: if expo-notifications is reliable on a device, that gap is a
 * second or two; if an OEM battery manager is interfering, reminders arrive
 * late, batched, or not at all -- and this record is what makes that visible.
 */
export interface ReminderRecord {
  id: string;
  /** The Expo notification identifier, so it can be cancelled. */
  notificationId: string;
  label: string;
  /** Epoch millis the reminder was created. */
  createdAt: number;
  /** Epoch millis the reminder was asked to fire. */
  scheduledFor: number;
  /**
   * Epoch millis the notification actually fired, filled in by a listener when
   * the app sees it, or by the user tapping "it arrived". Null while pending.
   */
  deliveredAt: number | null;
  /**
   * How the delivery was recorded:
   *  - 'foreground': a received-listener caught it while the app was open
   *  - 'tapped':     the user tapped the notification (app was backgrounded)
   *  - 'manual':     the user pressed "arrived" / "missed" by hand -- the only
   *                  honest option for the killed-app test
   */
  deliveredVia: 'foreground' | 'tapped' | 'manual' | null;
  /** Set true when the user reports a reminder never came. */
  missed: boolean;
  /** Device conditions at schedule time, so results are comparable across runs. */
  context: DeliveryContext;
}

export interface DeliveryContext {
  batteryOptimizationIgnored: boolean | null;
  exactAlarmAllowed: boolean | null;
  appState: 'active' | 'background' | 'unknown';
}

/** Signed drift in milliseconds (positive = late), or null if not yet delivered. */
export function driftMillis(r: ReminderRecord): number | null {
  if (r.deliveredAt == null) return null;
  return r.deliveredAt - r.scheduledFor;
}
