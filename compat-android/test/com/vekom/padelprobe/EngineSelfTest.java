package com.vekom.padelprobe;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.GameScoring;
import static com.vekom.padelprobe.MatchModel.SetEnding;
import static com.vekom.padelprobe.MatchModel.Settings;
import static com.vekom.padelprobe.MatchModel.State;
import static com.vekom.padelprobe.MatchModel.Team;

public final class EngineSelfTest {
    private static int assertions;
    private static long clock;

    public static void main(String[] args) {
        classicGame();
        classicAdvantageAndUndo();
        starPoint();
        silverPoint();
        classicTieBreak();
        classicServeRotationAndUndo();
        classicTieBreakServeRotation();
        goldenPoint();
        setEndingModes();
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
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gameScoring = GameScoring.ADVANTAGE;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 3);
        points(engine, Team.B, 3);
        point(engine, Team.A);
        eq("AD", engine.score(Team.A), "advantage is shown");
        point(engine, Team.B);
        eq("40", engine.score(Team.A), "opponent returns score to deuce");
        eq("DEUCE", engine.statusLabel(), "advantage mode uses ordinary deuce label");
        point(engine, Team.A);
        point(engine, Team.A);
        eq(1, engine.state().gamesA, "two clear points win from deuce");
        yes(engine.undo(), "undo is available");
        eq("AD", engine.score(Team.A), "undo restores advantage");
    }

    private static void goldenPoint() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gameScoring = GameScoring.GOLDEN;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 3);
        points(engine, Team.B, 3);
        point(engine, Team.B);
        eq(1, engine.state().gamesB, "golden point wins immediately at deuce");
    }

    private static void starPoint() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gameScoring = GameScoring.STAR;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 3);
        points(engine, Team.B, 3);
        eq("DEUCE 1", engine.statusLabel(), "first deuce is identified");
        point(engine, Team.A);
        eq("ADV A", engine.statusLabel(), "first advantage is identified");
        point(engine, Team.B);
        eq("DEUCE 2", engine.statusLabel(), "second deuce is identified");
        point(engine, Team.B);
        point(engine, Team.A);
        yes(engine.isDecidingPoint(), "third deuce becomes Star Point");
        eq("STAR POINT", engine.statusLabel(), "Star Point is visible");
        point(engine, Team.B);
        eq(1, engine.state().gamesB, "Star Point winner receives the game");
    }

    private static void silverPoint() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gameScoring = GameScoring.SILVER;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 3);
        points(engine, Team.B, 3);
        eq("DEUCE 1", engine.statusLabel(), "Silver starts with ordinary deuce");
        point(engine, Team.A);
        eq("ADV A", engine.statusLabel(), "Silver allows one advantage");
        point(engine, Team.B);
        yes(engine.isDecidingPoint(), "second deuce becomes Silver Point");
        eq("SILVER POINT", engine.statusLabel(), "Silver Point is visible");
        point(engine, Team.A);
        eq(1, engine.state().gamesA, "Silver Point winner receives the game");
    }

    private static void classicTieBreak() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gamesPerSet = 1;
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

    private static void setEndingModes() {
        Settings leadSettings = Settings.defaults(Mode.CLASSIC);
        leadSettings.gamesPerSet = 1;
        leadSettings.setsToWin = 1;
        leadSettings.setEnding = SetEnding.TWO_GAME_LEAD;
        MatchEngine lead = new MatchEngine(Mode.CLASSIC, leadSettings);
        points(lead, Team.A, 4);
        points(lead, Team.B, 4);
        no(lead.state().inTieBreak, "two-game lead does not start a tie-break");
        no(lead.state().completed, "one-game margin does not finish two-game lead set");
        points(lead, Team.A, 8);
        yes(lead.state().completed, "two-game margin finishes the set");

        Settings firstSettings = Settings.defaults(Mode.CLASSIC);
        firstSettings.gamesPerSet = 2;
        firstSettings.setsToWin = 1;
        firstSettings.setEnding = SetEnding.FIRST_TO;
        MatchEngine first = new MatchEngine(Mode.CLASSIC, firstSettings);
        points(first, Team.A, 4);
        points(first, Team.B, 4);
        points(first, Team.A, 4);
        yes(first.state().completed, "first-to set can finish with one-game margin");
    }

    private static void singleSetCompletes() {
        Settings settings = Settings.defaults(Mode.SINGLE_SET);
        settings.gamesPerSet = 1;
        settings.setEnding = SetEnding.FIRST_TO;
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
    private static void classicServeRotationAndUndo() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        yes(settings.trackServe, "Classic tracks serve by default");
        yes(Settings.defaults(Mode.SINGLE_SET).trackServe,
                "Single Set tracks serve by default");
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);
        points(engine, Team.A, 4);
        eq(1, engine.state().gamesA, "completed game is recorded");
        eq(Team.B, engine.state().currentServer,
                "serve changes after a completed Classic game");
        yes(engine.undo(), "completed game can be undone");
        eq(0, engine.state().gamesA, "undo restores game count");
        eq(Team.A, engine.state().currentServer, "undo restores server");

        settings.trackServe = false;
        MatchEngine disabled = new MatchEngine(Mode.CLASSIC, settings);
        points(disabled, Team.A, 4);
        eq(Team.A, disabled.state().currentServer,
                "disabled tracking leaves the server unchanged");
    }

    private static void classicTieBreakServeRotation() {
        Settings settings = Settings.defaults(Mode.CLASSIC);
        settings.gamesPerSet = 1;
        settings.setsToWin = 2;
        settings.tieBreakTarget = 3;
        settings.trackServe = true;
        settings.startingServer = Team.A;
        MatchEngine engine = new MatchEngine(Mode.CLASSIC, settings);

        points(engine, Team.A, 4);
        eq(Team.B, engine.state().currentServer, "B serves the second game");
        points(engine, Team.B, 4);
        yes(engine.state().inTieBreak, "tie-break starts at the configured score");
        eq(Team.A, engine.state().currentServer, "A starts the tie-break");
        eq(Team.A, engine.state().tieBreakStartingServer,
                "tie-break starter is remembered");

        engine.changeServer();
        eq(Team.B, engine.state().currentServer,
                "manual correction changes the tie-break starter");
        eq(Team.B, engine.state().tieBreakStartingServer,
                "corrected tie-break starter is remembered");
        yes(engine.undo(), "manual correction can be undone");
        eq(Team.A, engine.state().currentServer,
                "undo restores tie-break current server");
        eq(Team.A, engine.state().tieBreakStartingServer,
                "undo restores tie-break starter");

        point(engine, Team.A);
        eq(Team.B, engine.state().currentServer, "serve changes after tie-break point one");
        point(engine, Team.A);
        eq(Team.B, engine.state().currentServer, "server keeps the second point");
        engine.changeServer();
        eq(Team.A, engine.state().currentServer,
                "mid-tie-break correction changes the current server");
        eq(Team.B, engine.state().tieBreakStartingServer,
                "mid-tie-break correction infers the actual starter");
        yes(engine.undo(), "mid-tie-break correction can be undone");
        eq(Team.B, engine.state().currentServer,
                "undo restores current server after point two");
        eq(Team.A, engine.state().tieBreakStartingServer,
                "undo restores starter after point two");

        point(engine, Team.B);
        eq(Team.A, engine.state().currentServer, "serve changes after point three");
        point(engine, Team.B);
        eq(Team.A, engine.state().currentServer, "server keeps the fourth point");
        point(engine, Team.A);
        eq(Team.B, engine.state().currentServer, "serve changes after point five");
        point(engine, Team.A);

        no(engine.state().inTieBreak, "winning tie-break closes the set");
        eq(1, engine.state().setsA, "tie-break winner receives the set");
        eq(Team.B, engine.state().currentServer,
                "opposite team serves first in the next set");
        yes(engine.undo(), "tie-break completion can be undone");
        yes(engine.state().inTieBreak, "undo returns to the tie-break");
        eq(3, engine.state().tieBreakPointsA, "undo restores tie-break score A");
        eq(2, engine.state().tieBreakPointsB, "undo restores tie-break score B");
        eq(Team.A, engine.state().tieBreakStartingServer,
                "undo restores remembered tie-break starter");
        eq(Team.B, engine.state().currentServer,
                "undo restores current tie-break server");
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
