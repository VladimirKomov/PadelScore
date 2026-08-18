import { createInitialState } from './Defaults';
import type { ScoringStrategy } from './ScoringStrategy';
import {
  AmericanoScoringStrategy,
  ClassicScoringStrategy,
  RaceToNScoringStrategy,
  TieBreakScoringStrategy
} from './Strategies';
import type {
  DispatchResult,
  MatchEvent,
  MatchMode,
  MatchPresentation,
  MatchState,
  ModeSettings,
  PersistedMatch
} from './Types';
import {
  DEFAULT_DEBOUNCE_MS,
  SAVE_FORMAT_VERSION,
  cloneState,
  otherTeam
} from './Types';

export interface RestoreResult {
  engine: MatchEngine;
  restored: boolean;
  reason: string;
}

function strategyFor(mode: MatchMode): ScoringStrategy {
  if (mode === 'classic' || mode === 'single_set') {
    return new ClassicScoringStrategy();
  }
  if (mode === 'tie_break' || mode === 'super_tie_break') {
    return new TieBreakScoringStrategy();
  }
  if (mode === 'race_to_n') {
    return new RaceToNScoringStrategy();
  }
  return new AmericanoScoringStrategy();
}

function isValidState(value: MatchState): boolean {
  return (
    value !== null &&
    typeof value === 'object' &&
    value.formatVersion === SAVE_FORMAT_VERSION &&
    typeof value.mode === 'string' &&
    typeof value.pointsA === 'number' &&
    typeof value.pointsB === 'number' &&
    value.pointsA >= 0 &&
    value.pointsB >= 0 &&
    value.settings !== null &&
    typeof value.settings === 'object'
  );
}

export class MatchEngine {
  private stateValue: MatchState;
  private historyValue: MatchState[];
  private strategy: ScoringStrategy;
  private lastAcceptedPointAt: number;
  private debounceMs: number;

  constructor(
    mode: MatchMode = 'americano',
    settings?: ModeSettings,
    debounceMs: number = DEFAULT_DEBOUNCE_MS
  ) {
    this.stateValue = createInitialState(mode, settings);
    this.historyValue = [];
    this.strategy = strategyFor(mode);
    this.lastAcceptedPointAt = -1;
    this.debounceMs = debounceMs;
  }

  get state(): MatchState {
    return cloneState(this.stateValue);
  }

  get historyDepth(): number {
    return this.historyValue.length;
  }

  get presentation(): MatchPresentation {
    return this.strategy.presentation(this.stateValue, this.historyValue.length > 0);
  }

  dispatch(event: MatchEvent): DispatchResult {
    if (event.type === 'PointWon') {
      return this.point(event.team, event.occurredAt);
    }
    if (event.type === 'Undo') {
      return this.undo();
    }
    if (event.type === 'Reset') {
      this.reset();
      return { accepted: true, reason: 'reset', completedNow: false };
    }
    if (event.type === 'ChangeServer') {
      this.historyValue.push(cloneState(this.stateValue));
      this.stateValue.currentServer = otherTeam(this.stateValue.currentServer);
      this.stateValue.pointsSinceServerChange = 0;
      this.stateValue.revision += 1;
      return { accepted: true, reason: 'server changed', completedNow: false };
    }
    return this.startNextRound();
  }

  point(team: 'A' | 'B', occurredAt: number = Date.now()): DispatchResult {
    if (this.stateValue.completed) {
      return { accepted: false, reason: 'match is complete', completedNow: false };
    }
    if (
      this.lastAcceptedPointAt >= 0 &&
      occurredAt - this.lastAcceptedPointAt >= 0 &&
      occurredAt - this.lastAcceptedPointAt < this.debounceMs
    ) {
      return { accepted: false, reason: 'debounced duplicate tap', completedNow: false };
    }
    this.historyValue.push(cloneState(this.stateValue));
    this.strategy.addPoint(this.stateValue, team);
    this.stateValue.revision += 1;
    this.lastAcceptedPointAt = occurredAt;
    return {
      accepted: true,
      reason: 'point added',
      completedNow: this.stateValue.completed
    };
  }

  undo(): DispatchResult {
    const previous = this.historyValue.pop();
    if (previous === undefined) {
      return { accepted: false, reason: 'nothing to undo', completedNow: false };
    }
    this.stateValue = previous;
    this.strategy = strategyFor(this.stateValue.mode);
    this.lastAcceptedPointAt = -1;
    return { accepted: true, reason: 'undone', completedNow: false };
  }

  reset(): void {
    const mode = this.stateValue.mode;
    const settings = this.stateValue.settings;
    const roundHistory = cloneState(this.stateValue).roundHistory;
    const sessionA = this.stateValue.sessionPointsA;
    const sessionB = this.stateValue.sessionPointsB;
    const roundNumber = this.stateValue.roundNumber;
    this.stateValue = createInitialState(mode, settings);
    if (mode === 'americano') {
      this.stateValue.roundHistory = roundHistory;
      this.stateValue.sessionPointsA = sessionA;
      this.stateValue.sessionPointsB = sessionB;
      this.stateValue.roundNumber = roundNumber;
    }
    this.historyValue = [];
    this.lastAcceptedPointAt = -1;
  }

  startNextRound(): DispatchResult {
    if (this.stateValue.mode !== 'americano' || !this.stateValue.completed) {
      return { accepted: false, reason: 'completed Americano round required', completedNow: false };
    }
    const previous = this.stateValue;
    const next = createInitialState('americano', previous.settings);
    next.roundNumber = previous.roundNumber + 1;
    next.roundHistory = cloneState(previous).roundHistory;
    next.sessionPointsA = previous.sessionPointsA;
    next.sessionPointsB = previous.sessionPointsB;
    next.currentServer = previous.currentServer;
    next.revision = previous.revision + 1;
    this.stateValue = next;
    this.historyValue = [];
    this.lastAcceptedPointAt = -1;
    return { accepted: true, reason: 'next round started', completedNow: false };
  }

  serialize(): string {
    const data: PersistedMatch = {
      formatVersion: SAVE_FORMAT_VERSION,
      state: cloneState(this.stateValue),
      history: this.historyValue.map((item) => cloneState(item)),
      lastAcceptedPointAt: this.lastAcceptedPointAt
    };
    return JSON.stringify(data);
  }

  static restore(raw: string, fallbackMode: MatchMode = 'americano'): RestoreResult {
    try {
      const parsed = JSON.parse(raw) as PersistedMatch;
      if (
        parsed.formatVersion !== SAVE_FORMAT_VERSION ||
        !isValidState(parsed.state) ||
        !Array.isArray(parsed.history)
      ) {
        return {
          engine: new MatchEngine(fallbackMode),
          restored: false,
          reason: 'unsupported or invalid save format'
        };
      }
      const engine = new MatchEngine(parsed.state.mode, parsed.state.settings);
      engine.stateValue = cloneState(parsed.state);
      engine.historyValue = parsed.history
        .filter((state) => isValidState(state))
        .map((state) => cloneState(state));
      engine.lastAcceptedPointAt =
        typeof parsed.lastAcceptedPointAt === 'number' ? parsed.lastAcceptedPointAt : -1;
      engine.strategy = strategyFor(parsed.state.mode);
      return { engine, restored: true, reason: 'restored' };
    } catch (_error) {
      return {
        engine: new MatchEngine(fallbackMode),
        restored: false,
        reason: 'corrupt save data'
      };
    }
  }
}
