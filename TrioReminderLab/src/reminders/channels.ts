import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

/**
 * The high-importance channel every test reminder posts to.
 *
 * On Android 8+ the channel -- not the notification -- owns importance, sound
 * and vibration. IMPORTANCE_HIGH is what makes a reminder heads-up and audible;
 * anything lower is exactly the "notification that never registers" the report
 * warns about (section 3, notification blindness). Recreating the channel with
 * changed importance is ignored by Android once created, so this is set right
 * the first time.
 */
export const REMINDER_CHANNEL_ID = 'reminders-test';

export async function ensureReminderChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(REMINDER_CHANNEL_ID, {
    name: 'Reminder reliability test',
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 120, 200],
    enableVibrate: true,
    // No badge: the report is explicit that count badges drive "list guilt".
    showBadge: false,
    bypassDnd: false,
  });
}
