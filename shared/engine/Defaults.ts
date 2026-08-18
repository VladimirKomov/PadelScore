import type {
  AmericanoSettings,
  ClassicSettings,
  MatchMode,
  MatchState,
  ModeSettings,
  RaceSettings,
  TieBreakSettings
} from './Types';
import { SAVE_FORMAT_VERSION } from './Types';

export function defaultSettings(mode: MatchMode): ModeSettings {
  if (mode === 'classic' || mode === 'single_set') {
    const settings: ClassicSettings = {
      mode,
      gamesPerSet: 6,
      setsToWin: mode === 'single_set' ? 1 : 2,
      winSetByTwo: true,
      tieBreakEnabled: true,
      tieBreakAt: 6,
      tieBreakTarget: 7,
      advantageMode: 'advantage',
      trackServe: false,
      startingServer: 'A'
    };
    return settings;
  }
  if (mode === 'tie_break' || mode === 'super_tie_break') {
    const settings: TieBreakSettings = {
      mode,
      target: mode === 'super_tie_break' ? 10 : 7,
      winByTwo: true,
      trackServe: false,
      startingServer: 'A'
    };
    return settings;
  }
  if (mode === 'race_to_n') {
    const settings: RaceSettings = {
      mode,
      target: 21,
      winByTwo: false,
      trackServe: false,
      startingServer: 'A'
    };
    return settings;
  }
  const settings: AmericanoSettings = {
    mode: 'americano',
    totalPoints: 24,
    serveEvery: 4,
    trackServe: true,
    startingServer: 'A'
  };
  return settings;
}

export function createInitialState(mode: MatchMode, supplied?: ModeSettings): MatchState {
  const settings = supplied ?? defaultSettings(mode);
  return {
    formatVersion: SAVE_FORMAT_VERSION,
    mode,
    settings,
    pointsA: 0,
    pointsB: 0,
    gamesA: 0,
    gamesB: 0,
    setsA: 0,
    setsB: 0,
    tieBreakPointsA: 0,
    tieBreakPointsB: 0,
    inTieBreak: false,
    completed: false,
    winner: null,
    currentServer: settings.startingServer,
    pointsSinceServerChange: 0,
    roundNumber: 1,
    roundHistory: [],
    sessionPointsA: 0,
    sessionPointsB: 0,
    revision: 0
  };
}
