import { summarize } from '../reminders/stats';
import { ReminderRecord } from '../reminders/types';

function record(partial: Partial<ReminderRecord>): ReminderRecord {
  return {
    id: Math.random().toString(36),
    notificationId: 'n',
    label: 'test',
    createdAt: 0,
    scheduledFor: 1_000_000,
    deliveredAt: null,
    deliveredVia: null,
    missed: false,
    context: { batteryOptimizationIgnored: null, exactAlarmAllowed: null, appState: 'unknown' },
    ...partial,
  };
}

describe('summarize', () => {
  it('reports an empty log without dividing by zero', () => {
    const s = summarize([]);
    expect(s.total).toBe(0);
    expect(s.deliveryRatePct).toBeNull();
    expect(s.medianDriftSeconds).toBeNull();
  });

  it('counts delivered, missed and pending separately', () => {
    const s = summarize([
      record({ deliveredAt: 1_000_000 }),
      record({ missed: true }),
      record({}),
    ]);
    expect(s.delivered).toBe(1);
    expect(s.missed).toBe(1);
    expect(s.pending).toBe(1);
  });

  it('delivery rate ignores still-pending reminders', () => {
    // 1 delivered + 1 missed resolved, 2 pending -> 50%, not 25%.
    const s = summarize([
      record({ deliveredAt: 1_000_000 }),
      record({ missed: true }),
      record({}),
      record({}),
    ]);
    expect(s.deliveryRatePct).toBe(50);
  });

  it('computes median and worst absolute drift in seconds', () => {
    const s = summarize([
      record({ scheduledFor: 0, deliveredAt: 2000 }), // +2s
      record({ scheduledFor: 0, deliveredAt: 10_000 }), // +10s
      record({ scheduledFor: 0, deliveredAt: 60_000 }), // +60s
    ]);
    expect(s.medianDriftSeconds).toBe(10);
    expect(s.worstDriftSeconds).toBe(60);
  });
});
