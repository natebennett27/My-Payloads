/**
 * Manufacturer-specific steps for keeping reminders alive on Android.
 *
 * Ported directly from the native app's OemGuidanceTable. This is the second
 * half of the reliability problem the report describes (section 6): exact
 * alarms get past Android's own Doze, but Samsung's "sleeping apps", Xiaomi's
 * MIUI autostart and the Oppo/Realme/Vivo power managers can still put the app
 * to sleep entirely, and no API can fix that from inside the process. The only
 * honest answer is to detect the brand and walk the user to the right screen.
 *
 * `settingsAction` is an Android settings action string that expo-intent-launcher
 * can open. Component-level OEM screens are undocumented and firmware-specific,
 * so this uses stable, documented settings surfaces (app details, battery
 * optimisation) rather than guessing at internal activities that frequently
 * do not resolve.
 */
export interface OemGuidance {
  brandLabel: string;
  steps: string[];
}

const TABLE: Record<string, OemGuidance> = {
  samsung: {
    brandLabel: 'Samsung',
    steps: [
      'Open Settings > Battery > Background usage limits',
      'Make sure this app is not under "Sleeping apps" or "Deep sleeping apps"',
      'Turn off "Put unused apps to sleep" for this app',
    ],
  },
  xiaomi: {
    brandLabel: 'Xiaomi',
    steps: [
      'Open Settings > Apps > Manage apps > this app',
      'Turn on "Autostart"',
      'Set "Battery saver" to "No restrictions"',
    ],
  },
  huawei: {
    brandLabel: 'Huawei',
    steps: [
      'Open Settings > Battery > App launch',
      'Switch this app to "Manage manually"',
      'Turn on Auto-launch, Secondary launch and Run in background',
    ],
  },
  oppo: {
    brandLabel: 'OPPO',
    steps: [
      'Open Settings > Battery > App battery management',
      'Set this app to "Allow background running"',
      'In Settings > Apps > this app, turn on "Auto-start"',
    ],
  },
  vivo: {
    brandLabel: 'vivo',
    steps: [
      'Open Settings > Battery > High background power consumption',
      'Allow this app to run in the background',
      'In i Manager > App manager > Autostart, turn this app on',
    ],
  },
  oneplus: {
    brandLabel: 'OnePlus',
    steps: [
      'Open Settings > Battery > Battery optimization',
      'Set this app to "Don\'t optimize"',
      'Turn off "Advanced optimization" > "Deep optimization"',
    ],
  },
};

/** Sub-brands that share a parent's power manager. */
const ALIASES: Record<string, string> = {
  redmi: 'xiaomi',
  poco: 'xiaomi',
  honor: 'huawei',
  realme: 'oppo',
  iqoo: 'vivo',
};

/**
 * Returns guidance for [manufacturer], or null when the brand is not known to
 * need extra steps (Google Pixel and most others behave without them).
 */
export function guidanceForManufacturer(
  manufacturer: string | null | undefined,
): OemGuidance | null {
  if (!manufacturer) return null;
  const key = manufacturer.toLowerCase().trim();
  const resolved = ALIASES[key] ?? key;
  const base = TABLE[resolved];
  if (!base) return null;
  // realme keeps its own name while sharing OPPO's steps.
  if (key === 'realme') return { ...base, brandLabel: 'realme' };
  return base;
}
