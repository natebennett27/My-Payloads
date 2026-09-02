import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  AppStateStatus,
  Linking,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';
import * as Notifications from 'expo-notifications';
import { Button, Card, Muted, Pill, SectionTitle } from './src/components/ui';
import {
  DeviceReliability,
  openAppSettings,
  openBatteryOptimizationSettings,
  readDeviceReliability,
} from './src/oem/deviceReliability';
import { clearLog, loadLog, markDelivered, updateRecord } from './src/reminders/log';
import {
  cancelAll,
  configureForegroundPresentation,
  requestNotificationPermission,
  scheduleTestReminder,
} from './src/reminders/scheduler';
import { summarize } from './src/reminders/stats';
import { ReminderRecord, driftMillis } from './src/reminders/types';
import { dark, light } from './src/theme';

configureForegroundPresentation();

/** The offsets a tester actually wants: a quick sanity check, then the real ones. */
const OFFSETS: { label: string; ms: number }[] = [
  { label: '1 min', ms: 60_000 },
  { label: '5 min', ms: 5 * 60_000 },
  { label: '15 min', ms: 15 * 60_000 },
  { label: '30 min', ms: 30 * 60_000 },
  { label: '1 hour', ms: 60 * 60_000 },
  { label: '2 hours', ms: 2 * 60 * 60_000 },
];

export default function App() {
  const scheme = useColorScheme();
  const palette = scheme === 'dark' ? dark : light;

  const [log, setLog] = useState<ReminderRecord[]>([]);
  const [device, setDevice] = useState<DeviceReliability | null>(null);
  const [granted, setGranted] = useState<boolean | null>(null);
  const [busyOffset, setBusyOffset] = useState<number | null>(null);
  const appState = useRef<AppStateStatus>(AppState.currentState);

  const refresh = useCallback(async () => {
    setLog(await loadLog());
    setDevice(await readDeviceReliability());
  }, []);

  useEffect(() => {
    void (async () => {
      setGranted(await requestNotificationPermission());
      await refresh();
    })();
  }, [refresh]);

  // A reminder that fires while the app is open is recorded automatically.
  useEffect(() => {
    const received = Notifications.addNotificationReceivedListener((n) => {
      const recordId = n.request.content.data?.recordId as string | undefined;
      void markDelivered(Date.now(), 'foreground', recordId).then(setLog);
    });
    // A reminder tapped from the tray (app was backgrounded) is recorded too.
    const response = Notifications.addNotificationResponseReceivedListener((r) => {
      const recordId = r.notification.request.content.data?.recordId as string | undefined;
      void markDelivered(Date.now(), 'tapped', recordId).then(setLog);
    });
    return () => {
      received.remove();
      response.remove();
    };
  }, []);

  // Re-read the log whenever the app returns to the foreground, so results
  // recorded by hand on another launch show up.
  useEffect(() => {
    const sub = AppState.addEventListener('change', (next) => {
      if (appState.current.match(/inactive|background/) && next === 'active') {
        void refresh();
      }
      appState.current = next;
    });
    return () => sub.remove();
  }, [refresh]);

  const onSchedule = useCallback(
    async (offsetMs: number) => {
      setBusyOffset(offsetMs);
      try {
        const ctx = {
          batteryOptimizationIgnored: null,
          exactAlarmAllowed: null,
          appState:
            AppState.currentState === 'active'
              ? ('active' as const)
              : ('background' as const),
        };
        await scheduleTestReminder(`Test reminder (${labelFor(offsetMs)})`, offsetMs, ctx);
        setLog(await loadLog());
      } finally {
        setBusyOffset(null);
      }
    },
    [],
  );

  const stats = summarize(log);

  return (
    <View style={[styles.root, { backgroundColor: palette.bg }]}>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.h1, { color: palette.ink }]}>Reminder reliability lab</Text>
        <Muted palette={palette}>
          Schedule a reminder, lock the phone, and see if it actually arrives. The
          number that matters is delivery rate on your real device.
        </Muted>

        <View style={{ height: 16 }} />

        {granted === false && (
          <Card palette={palette} style={{ borderColor: palette.warn }}>
            <SectionTitle palette={palette}>Notifications are off</SectionTitle>
            <Muted palette={palette}>
              Nothing can arrive until you allow notifications. Grant them, then
              reopen this screen.
            </Muted>
            <View style={{ height: 10 }} />
            <Button
              palette={palette}
              label="Open app settings"
              onPress={() => void openAppSettings()}
            />
          </Card>
        )}

        <DeviceCard device={device} palette={palette} />

        <Card palette={palette}>
          <SectionTitle palette={palette}>Schedule a test</SectionTitle>
          <Muted palette={palette}>
            Start with 1 min to confirm the basics, then use 30 min – 2 hours with
            the phone locked and set aside. That is where OEM battery managers
            drop reminders.
          </Muted>
          <View style={styles.grid}>
            {OFFSETS.map((o) => (
              <View key={o.label} style={styles.gridItem}>
                <Button
                  palette={palette}
                  label={o.label}
                  variant="outline"
                  busy={busyOffset === o.ms}
                  onPress={() => void onSchedule(o.ms)}
                />
              </View>
            ))}
          </View>
        </Card>

        <StatsCard stats={stats} palette={palette} />

        <View style={styles.logHeader}>
          <SectionTitle palette={palette}>Log</SectionTitle>
          {log.length > 0 && (
            <Button
              palette={palette}
              label="Clear"
              variant="outline"
              onPress={() =>
                void (async () => {
                  await cancelAll();
                  setLog(await clearLog());
                })()
              }
            />
          )}
        </View>

        {log.length === 0 ? (
          <Muted palette={palette}>No reminders scheduled yet.</Muted>
        ) : (
          log.map((r) => (
            <ReminderRow
              key={r.id}
              record={r}
              palette={palette}
              onArrived={() =>
                void markDelivered(Date.now(), 'manual', r.id).then(setLog)
              }
              onMissed={() =>
                void updateRecord(r.id, { missed: true }).then(setLog)
              }
            />
          ))
        )}

        <View style={{ height: 12 }} />
        <Muted palette={palette}>
          Tip: for the honest killed-app test, schedule 30+ min, swipe the app
          away from recents, and leave the phone. When it fires, tap the
          notification — or mark it by hand here.
        </Muted>
        <View style={{ height: 40 }} />
      </ScrollView>
    </View>
  );
}

function labelFor(offsetMs: number): string {
  const found = OFFSETS.find((o) => o.ms === offsetMs);
  return found ? found.label : `${Math.round(offsetMs / 60000)} min`;
}

function DeviceCard({
  device,
  palette,
}: {
  device: DeviceReliability | null;
  palette: typeof light;
}) {
  if (!device) return null;
  return (
    <Card palette={palette}>
      <SectionTitle palette={palette}>This device</SectionTitle>
      <Muted palette={palette}>
        {device.manufacturer ?? 'Unknown'} {device.model ?? ''} · Android/iOS{' '}
        {device.osVersion ?? '?'}
      </Muted>
      {!device.isDevice && (
        <>
          <View style={{ height: 8 }} />
          <Pill palette={palette} tone="warn" label="Emulator — results are not meaningful" />
        </>
      )}

      {device.guidance && (
        <>
          <View style={{ height: 12 }} />
          <Text style={[styles.subhead, { color: palette.ink }]}>
            {device.guidance.brandLabel} needs extra steps
          </Text>
          <Muted palette={palette}>
            {device.guidance.brandLabel} runs its own power manager that can
            override Android and stop reminders. If delivery is poor, do these:
          </Muted>
          <View style={{ height: 6 }} />
          {device.guidance.steps.map((step, i) => (
            <Text key={i} style={[styles.step, { color: palette.inkMuted }]}>
              {i + 1}. {step}
            </Text>
          ))}
        </>
      )}

      <View style={{ height: 12 }} />
      <View style={styles.row}>
        <View style={styles.rowItem}>
          <Button
            palette={palette}
            label="Battery settings"
            variant="outline"
            onPress={() => void openBatteryOptimizationSettings()}
          />
        </View>
        <View style={styles.rowItem}>
          <Button
            palette={palette}
            label="App settings"
            variant="outline"
            onPress={() =>
              Platform.OS === 'android'
                ? void openAppSettings()
                : void Linking.openSettings()
            }
          />
        </View>
      </View>
    </Card>
  );
}

function StatsCard({
  stats,
  palette,
}: {
  stats: ReturnType<typeof summarize>;
  palette: typeof light;
}) {
  return (
    <Card palette={palette}>
      <SectionTitle palette={palette}>Results so far</SectionTitle>
      <View style={styles.statsRow}>
        <Stat palette={palette} value={`${stats.delivered}/${stats.delivered + stats.missed}`} label="delivered" />
        <Stat
          palette={palette}
          value={stats.deliveryRatePct == null ? '—' : `${stats.deliveryRatePct}%`}
          label="rate"
          tone={
            stats.deliveryRatePct == null
              ? 'neutral'
              : stats.deliveryRatePct >= 95
                ? 'good'
                : 'warn'
          }
        />
        <Stat
          palette={palette}
          value={stats.medianDriftSeconds == null ? '—' : `${stats.medianDriftSeconds}s`}
          label="median drift"
        />
        <Stat
          palette={palette}
          value={stats.worstDriftSeconds == null ? '—' : `${stats.worstDriftSeconds}s`}
          label="worst drift"
          tone={
            stats.worstDriftSeconds == null
              ? 'neutral'
              : stats.worstDriftSeconds <= 120
                ? 'good'
                : 'warn'
          }
        />
      </View>
      {stats.pending > 0 && (
        <>
          <View style={{ height: 8 }} />
          <Muted palette={palette}>{stats.pending} still pending.</Muted>
        </>
      )}
    </Card>
  );
}

function Stat({
  palette,
  value,
  label,
  tone = 'neutral',
}: {
  palette: typeof light;
  value: string;
  label: string;
  tone?: 'neutral' | 'good' | 'warn';
}) {
  const color = tone === 'good' ? palette.good : tone === 'warn' ? palette.warn : palette.ink;
  return (
    <View style={styles.stat}>
      <Text style={[styles.statValue, { color }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: palette.inkMuted }]}>{label}</Text>
    </View>
  );
}

function ReminderRow({
  record,
  palette,
  onArrived,
  onMissed,
}: {
  record: ReminderRecord;
  palette: typeof light;
  onArrived: () => void;
  onMissed: () => void;
}) {
  const drift = driftMillis(record);
  const status: { label: string; tone: 'neutral' | 'good' | 'warn' } = record.missed
    ? { label: 'missed', tone: 'warn' }
    : record.deliveredAt != null
      ? {
          label:
            drift == null
              ? 'delivered'
              : `+${Math.round(drift / 1000)}s (${record.deliveredVia})`,
          tone: drift != null && Math.abs(drift) <= 120_000 ? 'good' : 'warn',
        }
      : { label: 'pending', tone: 'neutral' };

  return (
    <Card palette={palette}>
      <View style={styles.logRowTop}>
        <Text style={[styles.logLabel, { color: palette.ink }]}>{record.label}</Text>
        <Pill palette={palette} tone={status.tone} label={status.label} />
      </View>
      <Muted palette={palette}>
        fires {new Date(record.scheduledFor).toLocaleTimeString()}
        {record.deliveredAt != null
          ? ` · arrived ${new Date(record.deliveredAt).toLocaleTimeString()}`
          : ''}
      </Muted>
      {record.deliveredAt == null && !record.missed && (
        <>
          <View style={{ height: 10 }} />
          <View style={styles.row}>
            <View style={styles.rowItem}>
              <Button palette={palette} label="It arrived" variant="outline" onPress={onArrived} />
            </View>
            <View style={styles.rowItem}>
              <Button palette={palette} label="Never came" variant="outline" onPress={onMissed} />
            </View>
          </View>
        </>
      )}
    </Card>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 20, paddingTop: 64 },
  h1: { fontSize: 30, fontWeight: '700', marginBottom: 8 },
  subhead: { fontSize: 15, fontWeight: '600', marginBottom: 4 },
  step: { fontSize: 14, lineHeight: 22 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 12, marginHorizontal: -4 },
  gridItem: { width: '33.33%', paddingHorizontal: 4, paddingVertical: 4 },
  row: { flexDirection: 'row', marginHorizontal: -5 },
  rowItem: { flex: 1, paddingHorizontal: 5 },
  statsRow: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 },
  stat: { alignItems: 'center', flex: 1 },
  statValue: { fontSize: 22, fontWeight: '700' },
  statLabel: { fontSize: 12, marginTop: 2 },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
    marginBottom: 8,
  },
  logRowTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  logLabel: { fontSize: 16, fontWeight: '600', flex: 1, marginRight: 8 },
});
