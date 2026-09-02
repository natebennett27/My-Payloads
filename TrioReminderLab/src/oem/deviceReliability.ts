import * as Device from 'expo-device';
import * as IntentLauncher from 'expo-intent-launcher';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { guidanceForManufacturer, OemGuidance } from './guidance';

/**
 * Everything the reliability screen needs to know about the current device.
 */
export interface DeviceReliability {
  manufacturer: string | null;
  model: string | null;
  osVersion: string | null;
  isDevice: boolean;
  guidance: OemGuidance | null;
  /**
   * expo-notifications cannot read the exact-alarm or battery-optimisation
   * toggles directly, so these are what we can actually determine.
   */
  notificationsGranted: boolean;
}

export async function readDeviceReliability(): Promise<DeviceReliability> {
  const permission = await Notifications.getPermissionsAsync();
  const manufacturer = Device.manufacturer ?? null;
  return {
    manufacturer,
    model: Device.modelName ?? null,
    osVersion: Device.osVersion ?? null,
    // A real reminder test is meaningless on a simulator; the UI warns on this.
    isDevice: Device.isDevice,
    guidance: guidanceForManufacturer(manufacturer),
    notificationsGranted: permission.granted,
  };
}

/**
 * Opens the app's own system settings page.
 *
 * From here the user can reach battery optimisation and, on Android 12+, the
 * "Alarms & reminders" toggle. A generic, always-resolvable target is used on
 * purpose: OEM-specific activities frequently fail to resolve and leave the
 * user staring at nothing.
 */
export async function openAppSettings(): Promise<void> {
  if (Platform.OS !== 'android') return;
  const pkg = 'com.trio.reminderlab';
  await IntentLauncher.startActivityAsync(
    IntentLauncher.ActivityAction.APPLICATION_DETAILS_SETTINGS,
    { data: `package:${pkg}` },
  );
}

/**
 * Opens the system battery-optimisation list, where the app can be marked
 * "Not optimised" / "Unrestricted".
 */
export async function openBatteryOptimizationSettings(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await IntentLauncher.startActivityAsync(
    IntentLauncher.ActivityAction.IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
  );
}
