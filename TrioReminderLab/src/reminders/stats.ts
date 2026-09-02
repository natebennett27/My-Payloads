import { ReminderRecord, driftMillis } from './types';

/**
 * The verdict the whole harness exists to produce.
 *
 * These are the numbers that decide whether the real app can be built on
 * expo-notifications: how many reminders arrived at all, and how close to on
 * time. The report's implicit bar is high -- for an ADHD reminder app a missed
 * reminder reads as "the app stopped working" -- so anything below near-total
 * delivery, or drift beyond a minute or two, is a signal to stay native.
 */
export interface ReliabilityStats {
  total: number;
  delivered: number;
  missed: number;
  pending: number;
  deliveryRatePct: number | null;
  medianDriftSeconds: number | null;
  worstDriftSeconds: number | null;
}

export function summarize(records: ReminderRecord[]): ReliabilityStats {
  const total = records.length;
  const delivered = records.filter((r) => r.deliveredAt != null).length;
  const missed = records.filter((r) => r.missed).length;
  const pending = total - delivered - missed;

  const resolved = delivered + missed;
  const deliveryRatePct = resolved === 0 ? null : Math.round((delivered / resolved) * 100);

  const drifts = records
    .map(driftMillis)
    .filter((d): d is number => d != null)
    .map((d) => d / 1000)
    .sort((a, b) => a - b);

  const medianDriftSeconds =
    drifts.length === 0 ? null : Math.round(medianOf(drifts));
  const worstDriftSeconds =
    drifts.length === 0 ? null : Math.round(Math.max(...drifts.map(Math.abs)));

  return {
    total,
    delivered,
    missed,
    pending,
    deliveryRatePct,
    medianDriftSeconds,
    worstDriftSeconds,
  };
}

function medianOf(sorted: number[]): number {
  const mid = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 1) return sorted[mid] as number;
  return ((sorted[mid - 1] as number) + (sorted[mid] as number)) / 2;
}
