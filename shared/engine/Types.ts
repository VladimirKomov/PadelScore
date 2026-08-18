export type Team = 'A' | 'B';
export type Winner = Team | 'draw' | null;
export type MatchMode =
  | 'classic'
  | 'single_set'
  | 'tie_break'
  | 'super_tie_break'
  | 'race_to_n'
  | 'americano';

export type AdvantageMode = 'advantage' | 'golden';

export interface BaseSettings {
  trackServe: boolean;
  startingServer: Team;
}

export interface ClassicSettings extends BaseSettings {
  mode: 'classic' | 'single_set';
  gamesPerSet: number;
  setsToWin: number;
  winSetByTwo: boolean;
  tieBreakEnabled: boolean;
  tieBreakAt: number;
  tieBreakTarget: number;
  advantageMode: AdvantageMode;
}

export interface TieBreakSettings extends BaseSettings {
  mode: 'tie_break' | 'super_tie_break';
  target: number;
  winByTwo: boolean;
}

export interface RaceSettings extends BaseSettings {
  mode: 'race_to_n';
  target: number;
  winByTwo: boolean;
}

export interface AmericanoSettings extends BaseSettings {
  mode: 'americano';
  totalPoints: number;
  serveEvery: number;
}

export type ModeSettings =
  | ClassicSettings
  | TieBreakSettings
  | RaceSettings
  | AmericanoSettings;

export interface RoundResult {
  roundNumber: number;
  teamA: number;
  teamB: number;
  totalPoints: number;
  winner: Winner;
}

export interface MatchState {
  formatVersion: number;
  mode: MatchMode;
  settings: ModeSettings;
  pointsA: number;
  pointsB: number;
  gamesA: number;
  gamesB: number;
  setsA: number;
  setsB: number;
  tieBreakPointsA: number;
  tieBreakPointsB: number;
  inTieBreak: boolean;
  completed: boolean;
  winner: Winner;
  currentServer: Team;
  pointsSinceServerChange: number;
  roundNumber: number;
  roundHistory: RoundResult[];
  sessionPointsA: number;
  sessionPointsB: number;
  revision: number;
}

export interface MatchPresentation {
  modeLabel: string;
  scoreA: string;
  scoreB: string;
  gamesA: number;
  gamesB: number;
  setsA: number;
  setsB: number;
  status: string;
  completed: boolean;
  winner: Winner;
  tieBreak: boolean;
  remainingPoints: number | null;
  playedPoints: number | null;
  progressPercent: number | null;
  currentServer: Team;
  canAddPoint: boolean;
  canUndo: boolean;
  canStartNextRound: boolean;
}

export interface PersistedMatch {
  formatVersion: number;
  state: MatchState;
  history: MatchState[];
  lastAcceptedPointAt: number;
}

export type MatchEvent =
  | { type: 'PointWon'; team: Team; occurredAt?: number }
  | { type: 'Undo' }
  | { type: 'Reset' }
  | { type: 'ClearSession' }
  | { type: 'ChangeServer' }
  | { type: 'StartNextRound' };

export interface DispatchResult {
  accepted: boolean;
  reason: string;
  completedNow: boolean;
}

export const SAVE_FORMAT_VERSION = 1;
export const DEFAULT_DEBOUNCE_MS = 350;

export function otherTeam(team: Team): Team {
  return team === 'A' ? 'B' : 'A';
}

export function cloneState(state: MatchState): MatchState {
  return JSON.parse(JSON.stringify(state)) as MatchState;
}
