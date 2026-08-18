package com.vekom.padelprobe;

import java.util.ArrayList;
import java.util.List;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.Settings;
import static com.vekom.padelprobe.MatchModel.State;
import static com.vekom.padelprobe.MatchModel.Team;

final class MatchEngine {
    static final long DEBOUNCE_MS = 350L;

    static final class Result {
        final boolean accepted;
        final boolean completedNow;

        Result(boolean accepted, boolean completedNow) {
            this.accepted = accepted;
            this.completedNow = completedNow;
        }
    }

    private State state;
    private final List<State> history;
    private ScoringStrategy strategy;
    private long lastAcceptedPointAt = -1L;

    MatchEngine(Mode mode, Settings settings) {
        state = State.initial(mode, settings);
        history = new ArrayList<>();
        strategy = ScoringStrategy.Factory.forMode(mode);
    }

    static MatchEngine restore(State restored, List<State> restoredHistory) {
        MatchEngine engine = new MatchEngine(restored.mode, restored.settings);
        engine.state = restored.copy();
        engine.history.clear();
        for (State previous : restoredHistory) {
            engine.history.add(previous.copy());
        }
        engine.strategy = ScoringStrategy.Factory.forMode(restored.mode);
        return engine;
    }

    State state() {
        return state.copy();
    }

    List<State> history() {
        List<State> result = new ArrayList<>();
        for (State previous : history) {
            result.add(previous.copy());
        }
        return result;
    }

    boolean canUndo() {
        return !history.isEmpty();
    }

    Result point(Team team, long occurredAt) {
        if (state.completed) {
            return new Result(false, false);
        }
        if (lastAcceptedPointAt >= 0
                && occurredAt >= lastAcceptedPointAt
                && occurredAt - lastAcceptedPointAt < DEBOUNCE_MS) {
            return new Result(false, false);
        }
        history.add(state.copy());
        strategy.addPoint(state, team);
        state.revision++;
        lastAcceptedPointAt = occurredAt;
        return new Result(true, state.completed);
    }

    Result point(Team team) {
        return point(team, System.currentTimeMillis());
    }

    boolean undo() {
        if (history.isEmpty()) {
            return false;
        }
        state = history.remove(history.size() - 1);
        strategy = ScoringStrategy.Factory.forMode(state.mode);
        lastAcceptedPointAt = -1L;
        return true;
    }

    void changeServer() {
        history.add(state.copy());
        state.currentServer = state.currentServer.other();
        state.pointsSinceServerChange = 0;
        state.revision++;
    }

    void resetCurrent() {
        State previous = state;
        state = State.initial(previous.mode, previous.settings);
        if (previous.mode == Mode.AMERICANO) {
            state.roundNumber = previous.roundNumber;
            state.sessionPointsA = previous.sessionPointsA;
            state.sessionPointsB = previous.sessionPointsB;
            for (MatchModel.RoundResult result : previous.roundHistory) {
                state.roundHistory.add(result.copy());
            }
        }
        history.clear();
        lastAcceptedPointAt = -1L;
    }

    void clearSession() {
        state = State.initial(state.mode, state.settings);
        history.clear();
        lastAcceptedPointAt = -1L;
    }

    boolean startNextRound() {
        if (state.mode != Mode.AMERICANO || !state.completed) {
            return false;
        }
        State previous = state;
        State next = State.initial(Mode.AMERICANO, previous.settings);
        next.roundNumber = previous.roundNumber + 1;
        next.sessionPointsA = previous.sessionPointsA;
        next.sessionPointsB = previous.sessionPointsB;
        next.currentServer = previous.currentServer;
        next.revision = previous.revision + 1;
        for (MatchModel.RoundResult result : previous.roundHistory) {
            next.roundHistory.add(result.copy());
        }
        state = next;
        history.clear();
        lastAcceptedPointAt = -1L;
        return true;
    }

    String score(Team team) {
        return strategy.scoreLabel(state, team);
    }

    String modeLabel() {
        return strategy.modeLabel(state);
    }

    String statusLabel() {
        if (!state.completed) {
            return state.inTieBreak ? "TIE-BREAK" : "IN PLAY";
        }
        return "DRAW".equals(state.winner) ? "ROUND DRAW" : "TEAM " + state.winner + " WINS";
    }

    String detailLabel() {
        if (state.mode == Mode.CLASSIC || state.mode == Mode.SINGLE_SET) {
            return "SETS " + state.setsA + ":" + state.setsB
                    + "   GAMES " + state.gamesA + ":" + state.gamesB;
        }
        if (state.mode == Mode.AMERICANO) {
            int played = state.pointsA + state.pointsB;
            int remaining = Math.max(0, state.settings.target - played);
            return "ROUND " + state.roundNumber + "   LEFT " + remaining
                    + (state.settings.trackServe ? "   SERVE " + state.currentServer : "");
        }
        int remaining = Math.max(0, state.settings.target - Math.max(state.pointsA, state.pointsB));
        return "LEFT " + remaining;
    }
}
