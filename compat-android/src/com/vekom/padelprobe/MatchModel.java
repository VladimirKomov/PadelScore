package com.vekom.padelprobe;

import java.util.ArrayList;
import java.util.List;

final class MatchModel {
    static final int FORMAT_VERSION = 1;

    enum Team {
        A, B;

        Team other() {
            return this == A ? B : A;
        }
    }

    enum Mode {
        CLASSIC("Classic Match"),
        SINGLE_SET("Single Set"),
        TIE_BREAK("Tie-break"),
        SUPER_TIE_BREAK("Super Tie-break"),
        RACE_TO_N("Race to N"),
        AMERICANO("Americano");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    static final class Settings {
        Mode mode;
        int target;
        int gamesPerSet;
        int setsToWin;
        int tieBreakAt;
        int tieBreakTarget;
        boolean winByTwo;
        boolean winSetByTwo;
        boolean tieBreakEnabled;
        boolean goldenPoint;
        boolean trackServe;
        int serveEvery;
        Team startingServer;

        static Settings defaults(Mode mode) {
            Settings value = new Settings();
            value.mode = mode;
            value.target = mode == Mode.SUPER_TIE_BREAK ? 10
                    : mode == Mode.TIE_BREAK ? 7
                    : mode == Mode.RACE_TO_N ? 21 : 24;
            value.gamesPerSet = 6;
            value.setsToWin = mode == Mode.SINGLE_SET ? 1 : 2;
            value.tieBreakAt = 6;
            value.tieBreakTarget = 7;
            value.winByTwo = mode != Mode.RACE_TO_N;
            value.winSetByTwo = true;
            value.tieBreakEnabled = true;
            value.goldenPoint = false;
            value.trackServe = mode == Mode.AMERICANO;
            value.serveEvery = 4;
            value.startingServer = Team.A;
            return value;
        }

        Settings copy() {
            Settings value = new Settings();
            value.mode = mode;
            value.target = target;
            value.gamesPerSet = gamesPerSet;
            value.setsToWin = setsToWin;
            value.tieBreakAt = tieBreakAt;
            value.tieBreakTarget = tieBreakTarget;
            value.winByTwo = winByTwo;
            value.winSetByTwo = winSetByTwo;
            value.tieBreakEnabled = tieBreakEnabled;
            value.goldenPoint = goldenPoint;
            value.trackServe = trackServe;
            value.serveEvery = serveEvery;
            value.startingServer = startingServer;
            return value;
        }
    }

    static final class RoundResult {
        int roundNumber;
        int teamA;
        int teamB;
        int totalPoints;
        String winner;

        RoundResult copy() {
            RoundResult value = new RoundResult();
            value.roundNumber = roundNumber;
            value.teamA = teamA;
            value.teamB = teamB;
            value.totalPoints = totalPoints;
            value.winner = winner;
            return value;
        }
    }

    static final class State {
        int formatVersion = FORMAT_VERSION;
        Mode mode;
        Settings settings;
        int pointsA;
        int pointsB;
        int gamesA;
        int gamesB;
        int setsA;
        int setsB;
        int tieBreakPointsA;
        int tieBreakPointsB;
        boolean inTieBreak;
        boolean completed;
        String winner;
        Team currentServer;
        int pointsSinceServerChange;
        int roundNumber;
        List<RoundResult> roundHistory;
        int sessionPointsA;
        int sessionPointsB;
        int revision;

        static State initial(Mode mode, Settings supplied) {
            State value = new State();
            value.mode = mode;
            value.settings = supplied.copy();
            value.currentServer = supplied.startingServer;
            value.roundNumber = 1;
            value.roundHistory = new ArrayList<>();
            return value;
        }

        State copy() {
            State value = new State();
            value.formatVersion = formatVersion;
            value.mode = mode;
            value.settings = settings.copy();
            value.pointsA = pointsA;
            value.pointsB = pointsB;
            value.gamesA = gamesA;
            value.gamesB = gamesB;
            value.setsA = setsA;
            value.setsB = setsB;
            value.tieBreakPointsA = tieBreakPointsA;
            value.tieBreakPointsB = tieBreakPointsB;
            value.inTieBreak = inTieBreak;
            value.completed = completed;
            value.winner = winner;
            value.currentServer = currentServer;
            value.pointsSinceServerChange = pointsSinceServerChange;
            value.roundNumber = roundNumber;
            value.roundHistory = new ArrayList<>();
            for (RoundResult result : roundHistory) {
                value.roundHistory.add(result.copy());
            }
            value.sessionPointsA = sessionPointsA;
            value.sessionPointsB = sessionPointsB;
            value.revision = revision;
            return value;
        }
    }

    private MatchModel() {
    }
}
