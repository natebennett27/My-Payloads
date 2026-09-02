import AsyncStorage from '@react-native-async-storage/async-storage';
import { ReminderRecord } from './types';

/**
 * Durable storage for the test log.
 *
 * It has to survive the app being killed -- that is the most important test
 * case -- so the log lives in AsyncStorage, not memory. Every mutation rewrites
 * the whole list; at test-harness scale (tens of records) that is simpler and
 * safer than partial updates, and there is no concurrent writer.
 */
const STORAGE_KEY = 'trio.reminderlab.log.v1';

export async function loadLog(): Promise<ReminderRecord[]> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as ReminderRecord[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    // A corrupt log must not brick the harness; start clean.
    return [];
  }
}

async function saveLog(records: ReminderRecord[]): Promise<void> {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(records));
}

export async function addRecord(record: ReminderRecord): Promise<ReminderRecord[]> {
  const records = await loadLog();
  const next = [record, ...records];
  await saveLog(next);
  return next;
}

export async function updateRecord(
  id: string,
  patch: Partial<ReminderRecord>,
): Promise<ReminderRecord[]> {
  const records = await loadLog();
  const next = records.map((r) => (r.id === id ? { ...r, ...patch } : r));
  await saveLog(next);
  return next;
}

/**
 * Marks the first still-pending reminder whose target time has passed as
 * delivered. Used by the foreground/tap listeners, which know a notification
 * fired but not which record it belongs to when the payload is missing.
 */
export async function markDelivered(
  deliveredAt: number,
  via: ReminderRecord['deliveredVia'],
  preferId?: string,
): Promise<ReminderRecord[]> {
  const records = await loadLog();
  let targetId = preferId;
  if (!targetId) {
    const candidate = records
      .filter((r) => r.deliveredAt == null && !r.missed)
      .sort((a, b) => a.scheduledFor - b.scheduledFor)[0];
    targetId = candidate?.id;
  }
  if (!targetId) return records;
  const next = records.map((r) =>
    r.id === targetId && r.deliveredAt == null
      ? { ...r, deliveredAt, deliveredVia: via }
      : r,
  );
  await saveLog(next);
  return next;
}

export async function clearLog(): Promise<ReminderRecord[]> {
  await AsyncStorage.removeItem(STORAGE_KEY);
  return [];
}
