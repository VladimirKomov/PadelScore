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

type UnknownSettings = Partial<ModeSettings> & Record<string, unknown>;

function integer(value: unknown, fallback: number, minimum: number, maximum: number): number {
  return typeof value === 'number' && Number.isFinite(value)
    ? Math.max(minimum, Math.min(maximum, Math.round(value)))
    : fallback;
}

function team(value: unknown, fallback: 'A' | 'B'): 'A' | 'B' {
  return value === 'A' || value === 'B' ? value : fallback;
}

export function defaultSettings(mode: MatchMode): ModeSettings {
  if (mode === 'classic' || mode === 'single_set') {
    const settings: ClassicSettings = {
      mode,
      gamesPerSet: 6,
      setsToWin: mode === 'single_set' ? 1 : 2,
      tieBreakTarget: 7,
      gameScoring: 'star',
      setEnding: 'tie_break',
      trackServe: true,
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

export function normalizeSettings(mode: MatchMode, supplied?: unknown): ModeSettings {
  const fallback = defaultSettings(mode);
  if (supplied === null || typeof supplied !== 'object') {
    return fallback;
  }
  const source = supplied as UnknownSettings;
  if (mode === 'classic' || mode === 'single_set') {
    const defaults = fallback as ClassicSettings;
    const gameScoring = source.gameScoring === 'star' || source.gameScoring === 'advantage' ||
      source.gameScoring === 'golden'
      ? source.gameScoring
      : source.advantageMode === 'golden' || source.goldenPoint === true
        ? 'golden'
        : 'advantage';
    const setEnding = source.setEnding === 'tie_break' || source.setEnding === 'two_game_lead' ||
      source.setEnding === 'first_to'
      ? source.setEnding
      : source.winSetByTwo === false
        ? 'first_to'
        : source.tieBreakEnabled === false
          ? 'two_game_lead'
          : 'tie_break';
    return {
      mode,
      gamesPerSet: integer(source.gamesPerSet, defaults.gamesPerSet, 1, 12),
      setsToWin: mode === 'single_set'
        ? 1
        : integer(source.setsToWin, defaults.setsToWin, 1, 3),
      tieBreakTarget: integer(source.tieBreakTarget, defaults.tieBreakTarget, 1, 30),
      gameScoring,
      setEnding,
      trackServe: typeof source.trackServe === 'boolean' ? source.trackServe : defaults.trackServe,
      startingServer: team(source.startingServer, defaults.startingServer)
    };
  }
  if (mode === 'americano') {
    const defaults = fallback as AmericanoSettings;
    return {
      mode,
      totalPoints: integer(source.totalPoints, defaults.totalPoints, 1, 99),
      serveEvery: integer(source.serveEvery, defaults.serveEvery, 1, 16),
      trackServe: typeof source.trackServe === 'boolean' ? source.trackServe : defaults.trackServe,
      startingServer: team(source.startingServer, defaults.startingServer)
    };
  }
  if (mode === 'tie_break' || mode === 'super_tie_break') {
    const defaults = fallback as TieBreakSettings;
    return {
      mode,
      target: integer(source.target, defaults.target, 1, 99),
      winByTwo: typeof source.winByTwo === 'boolean' ? source.winByTwo : defaults.winByTwo,
      trackServe: false,
      startingServer: team(source.startingServer, defaults.startingServer)
    };
  }
  const defaults = fallback as RaceSettings;
  return {
    mode,
    target: integer(source.target, defaults.target, 1, 99),
    winByTwo: typeof source.winByTwo === 'boolean' ? source.winByTwo : defaults.winByTwo,
    trackServe: false,
    startingServer: team(source.startingServer, defaults.startingServer)
  };
}

export function createInitialState(mode: MatchMode, supplied?: ModeSettings): MatchState {
  const settings = normalizeSettings(mode, supplied);
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
    tieBreakStartingServer: null,
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
