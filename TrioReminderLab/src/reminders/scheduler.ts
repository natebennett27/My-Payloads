import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { REMINDER_CHANNEL_ID, ensureReminderChannel } from './channels';
import { addRecord } from './log';
import { DeliveryContext, ReminderRecord } from './types';

/**
 * Configures how notifications behave while the app is foregrounded.
 *
 * Without this, a notification that fires while the app is open is swallowed by
 * the OS and never shown -- which would look exactly like a delivery failure
 * during testing and give a false negative. Showing it (banner + sound) makes
 * the foreground case observable.
 */
export function configureForegroundPresentation(): void {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      // shouldShowAlert is the pre-SDK-52 field; the banner/list pair is the
      // newer split. Both are set so the handler is correct across versions.
      shouldShowAlert: true,
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

/**
 * Requests notification permission (Android 13+ / iOS).
 *
 * Returns whether it was granted. Reminders can still be scheduled without it,
 * but nothing visible will arrive, so the UI uses this to warn clearly rather
 * than let the user run a test that cannot possibly pass.
 */
export async function requestNotificationPermission(): Promise<boolean> {
  const settings = await Notifications.getPermissionsAsync();
  if (settings.granted) return true;
  const requested = await Notifications.requestPermissionsAsync({
    ios: { allowAlert: true, allowSound: true, allowBadge: false },
  });
  return requested.granted;
}

let idCounter = 0;
function makeId(): string {
  idCounter += 1;
  return `${Date.now().toString(36)}-${idCounter}`;
}

/**
 * Schedules a single test reminder [offsetMs] from now and logs it.
 *
 * This is the exact operation under test. On Android, expo-notifications backs
 * a date/time trigger with AlarmManager; with the SCHEDULE_EXACT_ALARM
 * permission declared (see app.json) it can use an exact alarm, which is the
 * closest React Native gets to the native setAlarmClock() the report calls a P0
 * requirement. Whether that is actually good enough on a given OEM is precisely
 * what this harness measures.
 */
export async function scheduleTestReminder(
  label: string,
  offsetMs: number,
  context: DeliveryContext,
): Promise<ReminderRecord> {
  await ensureReminderChannel();

  const scheduledFor = Date.now() + offsetMs;
  const id = makeId();

  const notificationId = await Notifications.scheduleNotificationAsync({
    content: {
      title: label,
      body: reminderBody(offsetMs),
      sound: true,
      priority: Notifications.AndroidNotificationPriority.MAX,
      // The record id travels with the notification so listeners can match the
      // exact reminder that fired rather than guessing by time.
      data: { recordId: id, scheduledFor },
      ...(Platform.OS === 'android' ? { channelId: REMINDER_CHANNEL_ID } : {}),
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.DATE,
      date: scheduledFor,
      ...(Platform.OS === 'android' ? { channelId: REMINDER_CHANNEL_ID } : {}),
    },
  });

  const record: ReminderRecord = {
    id,
    notificationId,
    label,
    createdAt: Date.now(),
    scheduledFor,
    deliveredAt: null,
    deliveredVia: null,
    missed: false,
    context,
  };

  await addRecord(record);
  return record;
}

function reminderBody(offsetMs: number): string {
  const mins = Math.round(offsetMs / 60000);
  if (mins <= 0) return 'This should arrive right about now.';
  if (mins === 1) return 'Scheduled one minute out. Did it land on time?';
  if (mins < 60) return `Scheduled ${mins} minutes out. Put the phone down and wait.`;
  const hours = Math.round(mins / 60);
  return `Scheduled about ${hours} hour${hours === 1 ? '' : 's'} out -- the real test. Lock the phone and leave it.`;
}

export async function cancelReminder(notificationId: string): Promise<void> {
  await Notifications.cancelScheduledNotificationAsync(notificationId);
}

export async function cancelAll(): Promise<void> {
  await Notifications.cancelAllScheduledNotificationsAsync();
}
