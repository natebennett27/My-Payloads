import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
  ViewStyle,
} from 'react-native';
import { Palette } from '../theme';

export function Card({
  palette,
  children,
  style,
}: {
  palette: Palette;
  children: React.ReactNode;
  style?: ViewStyle;
}) {
  return (
    <View
      style={[
        styles.card,
        { backgroundColor: palette.raised, borderColor: palette.edge },
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function SectionTitle({ palette, children }: { palette: Palette; children: React.ReactNode }) {
  return <Text style={[styles.sectionTitle, { color: palette.ink }]}>{children}</Text>;
}

export function Muted({ palette, children }: { palette: Palette; children: React.ReactNode }) {
  return <Text style={[styles.muted, { color: palette.inkMuted }]}>{children}</Text>;
}

export function Button({
  palette,
  label,
  onPress,
  variant = 'solid',
  disabled,
  busy,
}: {
  palette: Palette;
  label: string;
  onPress: () => void;
  variant?: 'solid' | 'outline';
  disabled?: boolean;
  busy?: boolean;
}) {
  const solid = variant === 'solid';
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled || busy}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor: solid ? palette.accent : 'transparent',
          borderColor: palette.accent,
          opacity: disabled ? 0.4 : pressed ? 0.8 : 1,
        },
      ]}
    >
      {busy ? (
        <ActivityIndicator color={solid ? palette.bg : palette.accent} />
      ) : (
        <Text
          style={[
            styles.buttonLabel,
            { color: solid ? palette.bg : palette.accent },
          ]}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}

export function Pill({
  palette,
  label,
  tone,
}: {
  palette: Palette;
  label: string;
  tone: 'neutral' | 'good' | 'warn';
}) {
  const color =
    tone === 'good' ? palette.good : tone === 'warn' ? palette.warn : palette.inkMuted;
  return (
    <View style={[styles.pill, { borderColor: color }]}>
      <Text style={[styles.pillLabel, { color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 16,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
  },
  muted: {
    fontSize: 14,
    lineHeight: 20,
  },
  button: {
    borderRadius: 14,
    borderWidth: 1.5,
    paddingVertical: 12,
    paddingHorizontal: 16,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  buttonLabel: {
    fontSize: 15,
    fontWeight: '600',
  },
  pill: {
    borderRadius: 999,
    borderWidth: 1,
    paddingVertical: 3,
    paddingHorizontal: 10,
    alignSelf: 'flex-start',
  },
  pillLabel: {
    fontSize: 12,
    fontWeight: '600',
  },
});
