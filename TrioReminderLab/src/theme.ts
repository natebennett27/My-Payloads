/**
 * The same calm palette as the native app, so the prototype reads as the same
 * product. Deliberately contains no alarm-red: reminder state is never a
 * failure the app shouts about (report section 4).
 */
export const light = {
  bg: '#FBFAF7',
  raised: '#FFFFFF',
  ink: '#1B1C1E',
  inkMuted: '#5C5F66',
  edge: '#E6E3DC',
  accent: '#3F7D6B',
  accentSoft: '#D9EAE3',
  good: '#3F7D6B',
  warn: '#A8695C',
};

export const dark = {
  bg: '#141517',
  raised: '#1E2023',
  ink: '#EDEDEA',
  inkMuted: '#A0A3A8',
  edge: '#32353A',
  accent: '#7FC3A9',
  accentSoft: '#24352F',
  good: '#7FC3A9',
  warn: '#C98A7C',
};

export type Palette = typeof light;
