import { otherTeam } from './Types';
function pointLabel(own, opponent) {
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
function numericWinner(a, b, target, winByTwo) {
    const high = Math.max(a, b);
    if (high < target) {
        return null;
    }
    if (winByTwo && Math.abs(a - b) < 2) {
        return null;
    }
    return a > b ? 'A' : 'B';
}
function winnerStatus(winner) {
    if (winner === 'draw') {
        return 'ROUND DRAW';
    }
    return winner === null ? 'IN PLAY' : `TEAM ${winner} WINS`;
}
function classicGameStatus(state, settings) {
    if (state.inTieBreak) {
        return 'TIE-BREAK';
    }
    if (state.pointsA === state.pointsB && state.pointsA >= 3) {
        if (settings.gameScoring === 'star') {
            return state.pointsA >= 5 ? 'STAR POINT' : `DEUCE ${state.pointsA - 2}`;
        }
        if (settings.gameScoring === 'silver') {
            return state.pointsA >= 4 ? 'SILVER POINT' : 'DEUCE 1';
        }
        return 'DEUCE';
    }
    if (Math.abs(state.pointsA - state.pointsB) === 1 && Math.min(state.pointsA, state.pointsB) >= 3) {
        return `ADV ${state.pointsA > state.pointsB ? 'A' : 'B'}`;
    }
    return 'IN PLAY';
}
function basePresentation(state, canUndo, modeLabel, scoreA, scoreB) {
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
export class ClassicScoringStrategy {
    addPoint(state, team) {
        const settings = state.settings;
        if (state.inTieBreak) {
            if (team === 'A') {
                state.tieBreakPointsA += 1;
            }
            else {
                state.tieBreakPointsB += 1;
            }
            const winner = numericWinner(state.tieBreakPointsA, state.tieBreakPointsB, settings.tieBreakTarget, true);
            if (winner !== null && winner !== 'draw') {
                if (settings.trackServe && state.tieBreakStartingServer !== null) {
                    state.currentServer = otherTeam(state.tieBreakStartingServer);
                }
                this.awardSet(state, winner, settings);
            }
            else if (settings.trackServe) {
                const played = state.tieBreakPointsA + state.tieBreakPointsB;
                if (played === 1 || played % 2 === 1) {
                    state.currentServer = otherTeam(state.currentServer);
                }
            }
            return;
        }
        const beforeA = state.pointsA;
        const beforeB = state.pointsB;
        if (team === 'A') {
            state.pointsA += 1;
        }
        else {
            state.pointsB += 1;
        }
        const tiedBeforePoint = beforeA === beforeB;
        const goldenDeuce = settings.gameScoring === 'golden' && tiedBeforePoint && beforeA >= 3;
        const silverPoint = settings.gameScoring === 'silver' && tiedBeforePoint && beforeA >= 4;
        const starPoint = settings.gameScoring === 'star' && tiedBeforePoint && beforeA >= 5;
        const own = team === 'A' ? state.pointsA : state.pointsB;
        const opponent = team === 'A' ? state.pointsB : state.pointsA;
        const gameWon = goldenDeuce || silverPoint || starPoint || (own >= 4 && own - opponent >= 2);
        if (gameWon) {
            this.awardGame(state, team, settings);
        }
    }
    awardGame(state, team, settings) {
        state.pointsA = 0;
        state.pointsB = 0;
        if (team === 'A') {
            state.gamesA += 1;
        }
        else {
            state.gamesB += 1;
        }
        if (settings.trackServe) {
            state.currentServer = otherTeam(state.currentServer);
            state.pointsSinceServerChange = 0;
        }
        if (settings.setEnding === 'tie_break' &&
            state.gamesA === settings.gamesPerSet &&
            state.gamesB === settings.gamesPerSet) {
            state.inTieBreak = true;
            state.tieBreakPointsA = 0;
            state.tieBreakPointsB = 0;
            state.tieBreakStartingServer = state.currentServer;
            return;
        }
        const ownGames = team === 'A' ? state.gamesA : state.gamesB;
        const opponentGames = team === 'A' ? state.gamesB : state.gamesA;
        const enoughGames = ownGames >= settings.gamesPerSet;
        const enoughMargin = settings.setEnding === 'first_to' || ownGames - opponentGames >= 2;
        if (enoughGames && enoughMargin) {
            this.awardSet(state, team, settings);
        }
    }
    awardSet(state, team, settings) {
        if (team === 'A') {
            state.setsA += 1;
        }
        else {
            state.setsB += 1;
        }
        state.pointsA = 0;
        state.pointsB = 0;
        state.gamesA = 0;
        state.gamesB = 0;
        state.tieBreakPointsA = 0;
        state.tieBreakPointsB = 0;
        state.inTieBreak = false;
        state.tieBreakStartingServer = null;
        const wonSets = team === 'A' ? state.setsA : state.setsB;
        if (wonSets >= settings.setsToWin) {
            state.completed = true;
            state.winner = team;
        }
    }
    presentation(state, canUndo) {
        const label = state.mode === 'single_set' ? 'SINGLE SET' : 'CLASSIC';
        const scoreA = state.inTieBreak
            ? String(state.tieBreakPointsA)
            : pointLabel(state.pointsA, state.pointsB);
        const scoreB = state.inTieBreak
            ? String(state.tieBreakPointsB)
            : pointLabel(state.pointsB, state.pointsA);
        const result = basePresentation(state, canUndo, label, scoreA, scoreB);
        if (!state.completed) {
            result.status = classicGameStatus(state, state.settings);
        }
        return result;
    }
}
export class TieBreakScoringStrategy {
    addPoint(state, team) {
        const settings = state.settings;
        if (team === 'A') {
            state.pointsA += 1;
        }
        else {
            state.pointsB += 1;
        }
        const winner = numericWinner(state.pointsA, state.pointsB, settings.target, settings.winByTwo);
        if (winner !== null && winner !== 'draw') {
            state.completed = true;
            state.winner = winner;
        }
    }
    presentation(state, canUndo) {
        const label = state.mode === 'super_tie_break' ? 'SUPER TIE-BREAK' : 'TIE-BREAK';
        return basePresentation(state, canUndo, label, String(state.pointsA), String(state.pointsB));
    }
}
export class RaceToNScoringStrategy {
    addPoint(state, team) {
        const settings = state.settings;
        if (team === 'A') {
            state.pointsA += 1;
        }
        else {
            state.pointsB += 1;
        }
        const winner = numericWinner(state.pointsA, state.pointsB, settings.target, settings.winByTwo);
        if (winner !== null && winner !== 'draw') {
            state.completed = true;
            state.winner = winner;
        }
    }
    presentation(state, canUndo) {
        const settings = state.settings;
        const result = basePresentation(state, canUndo, 'RACE TO ' + String(settings.target), String(state.pointsA), String(state.pointsB));
        result.remainingPoints = Math.max(0, settings.target - Math.max(state.pointsA, state.pointsB));
        return result;
    }
}
export class AmericanoScoringStrategy {
    addPoint(state, team) {
        const settings = state.settings;
        if (team === 'A') {
            state.pointsA += 1;
        }
        else {
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
    presentation(state, canUndo) {
        const settings = state.settings;
        const played = state.pointsA + state.pointsB;
        const result = basePresentation(state, canUndo, 'AMERICANO ' + String(settings.totalPoints), String(state.pointsA), String(state.pointsB));
        result.playedPoints = played;
        result.remainingPoints = Math.max(0, settings.totalPoints - played);
        result.progressPercent = Math.round((played * 100) / settings.totalPoints);
        result.canStartNextRound = state.completed;
        return result;
    }
}
