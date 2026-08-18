package com.vekom.padelprobe;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.Settings;
import static com.vekom.padelprobe.MatchModel.State;
import static com.vekom.padelprobe.MatchModel.Team;

public final class EngineSelfTest {
    private static int assertions;
    private static long clock;

    public static void main(String[] args) {
        classicGame();
        classicAdvantageAndUndo();
        classicTieBreak();
        goldenPoint();
        singleSetCompletes();
        tieBreakWinByTwo();
        superTieBreak();
        raceToTarget();
        debounce();
        americanoRoundAndSession();
        changeServerUndo();
        System.out.println("EngineSelfTest: " + assertions + " assertions passed");
    }

    private static void classicGame() {
        MatchEngine engine = engine(Mode.CLASSIC);
        points(engine, Team.A, 4);
        State state = engine.state();
        eq(1, state.gamesA, "four tennis points award a game");
        eq(0, state.pointsA, "point score resets after game");
    }

    private static void classicAdvantageAndUndo() {
        MatchEngine engine = engine(Mode.CLASSIC);
        points(engine, Team.A, 3);
        points(engine, Team.B, 3);
        point(engine, Team.A);
        eq("AD", engine.score(Team.A), "advantage is shown");
        point(engine, Team.B);
        eq("40", engine.score(Team.A), "opponent returns score to deuce");
        point(engine, Team.A);
        point(engine, Team.A);
        eq(1, engine.state().gamesA, "two clear points win from deuce");
        yes(engine.undo(), "undo is available");
        eq("AD", engine.score(Team.A), "undo restores advantage");
    }

    private static void goldenPoint() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.goldenPoint = true;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 3);
        points(engine, Team.B, 3);
        point(engine, Team.B);
        eq(1, engine.state().gamesB, "golden point wins immediately at deuce");
    }

    private static void classicTieBreak() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gamesPerSet = 1;
        settings.tieBreakAt = 1;
        settings.setsToWin = 1;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 4);
        points(engine, Team.B, 4);
        yes(engine.state().inTieBreak, "classic enters tie-break at configured score");
        points(engine, Team.A, 5);
        points(engine, Team.B, 5);
        point(engine, Team.A);
        point(engine, Team.B);
        point(engine, Team.A);
        point(engine, Team.A);
        yes(engine.state().completed, "classic match completes after tie-break");
        eq("A", engine.state().winner, "classic tie-break awards the set");
    }

    private static void singleSetCompletes() {
        Settings settings = Settings.defaults(Mode.SINGLE_SET);
        settings.gamesPerSet = 1;
        settings.winSetByTwo = false;
        MatchEngine engine = new MatchEngine(Mode.SINGLE_SET, settings);
        points(engine, Team.B, 4);
        yes(engine.state().completed, "single set completes after one configured game");
        eq(1, engine.state().setsB, "single set winner receives the set");
        eq("SINGLE SET", engine.modeLabel(), "single set has distinct mode label");
    }

    private static void tieBreakWinByTwo() {
        MatchEngine engine = engine(Mode.TIE_BREAK);
        points(engine, Team.A, 6);
        points(engine, Team.B, 6);
        point(engine, Team.A);
        no(engine.state().completed, "7:6 is not enough with win-by-two");
        point(engine, Team.A);
        yes(engine.state().completed, "8:6 completes tie-break");
        eq("A", engine.state().winner, "tie-break winner recorded");
    }

    private static void raceToTarget() {
        Settings settings = Settings.defaults(Mode.RACE_TO_N);
        settings.target = 7;
        settings.winByTwo = false;
        MatchEngine engine = new MatchEngine(Mode.RACE_TO_N, settings);
        points(engine, Team.B, 7);
        yes(engine.state().completed, "race completes at target");
        no(engine.point(Team.A, nextTime()).accepted, "points blocked after completion");
        eq(0, engine.state().pointsA, "blocked point does not mutate score");
    }

    private static void superTieBreak() {
        MatchEngine engine = engine(Mode.SUPER_TIE_BREAK);
        points(engine, Team.A, 9);
        points(engine, Team.B, 8);
        no(engine.state().completed, "9:8 does not complete super tie-break");
        point(engine, Team.A);
        yes(engine.state().completed, "10:8 completes super tie-break");
        eq("A", engine.state().winner, "super tie-break winner recorded");
    }

    private static void debounce() {
        MatchEngine engine = engine(Mode.TIE_BREAK);
        long now = nextTime();
        yes(engine.point(Team.A, now).accepted, "first tap accepted");
        no(engine.point(Team.A, now + 100).accepted, "rapid duplicate rejected");
        yes(engine.point(Team.A, now + 351).accepted, "later tap accepted");
        eq(2, engine.state().pointsA, "debounce accepts exactly two points");
        clock = now + 351;
    }

    private static void americanoRoundAndSession() {
        Settings settings = Settings.defaults(Mode.AMERICANO);
        settings.target = 10;
        settings.serveEvery = 2;
        MatchEngine engine = new MatchEngine(Mode.AMERICANO, settings);
        points(engine, Team.A, 6);
        points(engine, Team.B, 4);
        State completed = engine.state();
        yes(completed.completed, "Americano completes at fixed total");
        eq(10, completed.pointsA + completed.pointsB, "Americano total is exact");
        eq(1, completed.roundHistory.size(), "round appended to history");
        eq(6, completed.sessionPointsA, "session A total updated");
        yes(engine.undo(), "completion can be undone");
        eq(0, engine.state().roundHistory.size(), "undo removes completed history entry");
        point(engine, Team.B);
        yes(engine.startNextRound(), "next round starts after completion");
        eq(2, engine.state().roundNumber, "round number advances");
        eq(10, engine.state().sessionPointsA + engine.state().sessionPointsB,
                "session total carries to next round");
        eq(0, engine.state().pointsA + engine.state().pointsB,
                "next round begins at zero");
    }

    private static void changeServerUndo() {
        MatchEngine engine = engine(Mode.AMERICANO);
        eq(Team.A, engine.state().currentServer, "A serves first by default");
        engine.changeServer();
        eq(Team.B, engine.state().currentServer, "manual serve change works");
        yes(engine.undo(), "serve change is undoable");
        eq(Team.A, engine.state().currentServer, "undo restores server");
    }

    private static MatchEngine engine(Mode mode) {
        return new MatchEngine(mode, Settings.defaults(mode));
    }

    private static void points(MatchEngine engine, Team team, int count) {
        for (int i = 0; i < count; i++) {
            point(engine, team);
        }
    }

    private static void point(MatchEngine engine, Team team) {
        yes(engine.point(team, nextTime()).accepted, "point accepted");
    }

    private static long nextTime() {
        clock += MatchEngine.DEBOUNCE_MS + 1;
        return clock;
    }

    private static void yes(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void no(boolean condition, String message) {
        yes(!condition, message);
    }

    private static void eq(int expected, int actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void eq(String expected, String actual, String message) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void eq(Team expected, Team actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
