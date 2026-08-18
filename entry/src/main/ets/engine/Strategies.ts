import type { ScoringStrategy } from './ScoringStrategy';
import type {
  AmericanoSettings,
  ClassicSettings,
  MatchPresentation,
  MatchState,
  RaceSettings,
  Team,
  TieBreakSettings,
  Winner
} from './Types';
import { otherTeam } from './Types';

function pointLabel(own: number, opponent: number): string {
  const normal = ['0', '15', '30', '40'];
  if (own <= 3 && opponent <= 3) {
    return normal[own];
  }
  if (own === opponent) {
    return '40';
  }
  if (own > opponent) {
    return 'AD';
  }
  return '40';
}

function numericWinner(a: number, b: number, target: number, winByTwo: boolean): Winner {
  const high = Math.max(a, b);
  if (high < target) {
    return null;
  }
  if (winByTwo && Math.abs(a - b) < 2) {
    return null;
  }
  return a > b ? 'A' : 'B';
}

function winnerStatus(winner: Winner): string {
  if (winner === 'draw') {
    return 'ROUND DRAW';
  }
  return winner === null ? 'IN PLAY' : `TEAM ${winner} WINS`;
}

function basePresentation(
  state: MatchState,
  canUndo: boolean,
  modeLabel: string,
  scoreA: string,
  scoreB: string
): MatchPresentation {
  return {
    modeLabel,
    scoreA,
    scoreB,
    gamesA: state.gamesA,
    gamesB: state.gamesB,
    setsA: state.setsA,
    setsB: state.setsB,
    status: winnerStatus(state.winner),
    completed: state.completed,
    winner: state.winner,
    tieBreak: state.inTieBreak,
    remainingPoints: null,
    playedPoints: null,
    progressPercent: null,
    currentServer: state.currentServer,
    canAddPoint: !state.completed,
    canUndo,
    canStartNextRound: false
  };
}

export class ClassicScoringStrategy implements ScoringStrategy {
  addPoint(state: MatchState, team: Team): void {
    const settings = state.settings as ClassicSettings;
    if (state.inTieBreak) {
      if (team === 'A') {
        state.tieBreakPointsA += 1;
      } else {
        state.tieBreakPointsB += 1;
      }
      const winner = numericWinner(
        state.tieBreakPointsA,
        state.tieBreakPointsB,
        settings.tieBreakTarget,
        true
      );
      if (winner !== null && winner !== 'draw') {
        this.awardSet(state, winner, settings);
      }
      return;
    }

    const beforeA = state.pointsA;
    const beforeB = state.pointsB;
    if (team === 'A') {
      state.pointsA += 1;
    } else {
      state.pointsB += 1;
    }

    const goldenDeuce =
      settings.advantageMode === 'golden' && beforeA >= 3 && beforeB >= 3;
    const own = team === 'A' ? state.pointsA : state.pointsB;
    const opponent = team === 'A' ? state.pointsB : state.pointsA;
    const gameWon = goldenDeuce || (own >= 4 && own - opponent >= 2);
    if (gameWon) {
      this.awardGame(state, team, settings);
    }
  }

  private awardGame(state: MatchState, team: Team, settings: ClassicSettings): void {
    state.pointsA = 0;
    state.pointsB = 0;
    if (team === 'A') {
      state.gamesA += 1;
    } else {
      state.gamesB += 1;
    }

    if (
      settings.tieBreakEnabled &&
      state.gamesA === settings.tieBreakAt &&
      state.gamesB === settings.tieBreakAt
    ) {
      state.inTieBreak = true;
      state.tieBreakPointsA = 0;
      state.tieBreakPointsB = 0;
      return;
    }

    const ownGames = team === 'A' ? state.gamesA : state.gamesB;
    const opponentGames = team === 'A' ? state.gamesB : state.gamesA;
    const enoughGames = ownGames >= settings.gamesPerSet;
    const enoughMargin = !settings.winSetByTwo || ownGames - opponentGames >= 2;
    if (enoughGames && enoughMargin) {
      this.awardSet(state, team, settings);
    }
  }

  private awardSet(state: MatchState, team: Team, settings: ClassicSettings): void {
    if (team === 'A') {
      state.setsA += 1;
    } else {
      state.setsB += 1;
    }
    state.pointsA = 0;
    state.pointsB = 0;
    state.gamesA = 0;
    state.gamesB = 0;
    state.tieBreakPointsA = 0;
    state.tieBreakPointsB = 0;
    state.inTieBreak = false;

    const wonSets = team === 'A' ? state.setsA : state.setsB;
    if (wonSets >= settings.setsToWin) {
      state.completed = true;
      state.winner = team;
    }
  }

  presentation(state: MatchState, canUndo: boolean): MatchPresentation {
    const label = state.mode === 'single_set' ? 'SINGLE SET' : 'CLASSIC';
    const scoreA = state.inTieBreak
      ? String(state.tieBreakPointsA)
      : pointLabel(state.pointsA, state.pointsB);
    const scoreB = state.inTieBreak
      ? String(state.tieBreakPointsB)
      : pointLabel(state.pointsB, state.pointsA);
    const result = basePresentation(state, canUndo, label, scoreA, scoreB);
    if (state.inTieBreak && !state.completed) {
      result.status = 'TIE-BREAK';
    }
    return result;
  }
}

export class TieBreakScoringStrategy implements ScoringStrategy {
  addPoint(state: MatchState, team: Team): void {
    const settings = state.settings as TieBreakSettings;
    if (team === 'A') {
      state.pointsA += 1;
    } else {
      state.pointsB += 1;
    }
    const winner = numericWinner(state.pointsA, state.pointsB, settings.target, settings.winByTwo);
    if (winner !== null && winner !== 'draw') {
      state.completed = true;
      state.winner = winner;
    }
  }

  presentation(state: MatchState, canUndo: boolean): MatchPresentation {
    const label = state.mode === 'super_tie_break' ? 'SUPER TIE-BREAK' : 'TIE-BREAK';
    return basePresentation(
      state,
      canUndo,
      label,
      String(state.pointsA),
      String(state.pointsB)
    );
  }
}

export class RaceToNScoringStrategy implements ScoringStrategy {
  addPoint(state: MatchState, team: Team): void {
    const settings = state.settings as RaceSettings;
    if (team === 'A') {
      state.pointsA += 1;
    } else {
      state.pointsB += 1;
    }
    const winner = numericWinner(state.pointsA, state.pointsB, settings.target, settings.winByTwo);
    if (winner !== null && winner !== 'draw') {
      state.completed = true;
      state.winner = winner;
    }
  }

  presentation(state: MatchState, canUndo: boolean): MatchPresentation {
    const settings = state.settings as RaceSettings;
    const result = basePresentation(
      state,
      canUndo,
      'RACE TO ' + String(settings.target),
      String(state.pointsA),
      String(state.pointsB)
    );
    result.remainingPoints = Math.max(0, settings.target - Math.max(state.pointsA, state.pointsB));
    return result;
  }
}

export class AmericanoScoringStrategy implements ScoringStrategy {
  addPoint(state: MatchState, team: Team): void {
    const settings = state.settings as AmericanoSettings;
    if (team === 'A') {
      state.pointsA += 1;
    } else {
      state.pointsB += 1;
    }

    if (settings.trackServe) {
      state.pointsSinceServerChange += 1;
      if (state.pointsSinceServerChange >= settings.serveEvery) {
        state.currentServer = otherTeam(state.currentServer);
        state.pointsSinceServerChange = 0;
      }
    }

    const played = state.pointsA + state.pointsB;
    if (played === settings.totalPoints) {
      state.completed = true;
      state.winner =
        state.pointsA === state.pointsB ? 'draw' : state.pointsA > state.pointsB ? 'A' : 'B';
      state.sessionPointsA += state.pointsA;
      state.sessionPointsB += state.pointsB;
      state.roundHistory.push({
        roundNumber: state.roundNumber,
        teamA: state.pointsA,
        teamB: state.pointsB,
        totalPoints: settings.totalPoints,
        winner: state.winner
      });
    }
  }

  presentation(state: MatchState, canUndo: boolean): MatchPresentation {
    const settings = state.settings as AmericanoSettings;
    const played = state.pointsA + state.pointsB;
    const result = basePresentation(
      state,
      canUndo,
      'AMERICANO ' + String(settings.totalPoints),
      String(state.pointsA),
      String(state.pointsB)
    );
    result.playedPoints = played;
    result.remainingPoints = Math.max(0, settings.totalPoints - played);
    result.progressPercent = Math.round((played * 100) / settings.totalPoints);
    result.canStartNextRound = state.completed;
    return result;
  }
}
