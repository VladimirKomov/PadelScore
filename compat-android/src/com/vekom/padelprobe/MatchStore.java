package com.vekom.padelprobe;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.GameScoring;
import static com.vekom.padelprobe.MatchModel.SetEnding;
import static com.vekom.padelprobe.MatchModel.Settings;
import static com.vekom.padelprobe.MatchModel.State;
import static com.vekom.padelprobe.MatchModel.Team;

final class MatchStore {
    private static final String CURRENT_KEY = "current_match_v1";
    private final SharedPreferences preferences;

    MatchStore(Context context) {
        preferences = context.getSharedPreferences("padelscore", Context.MODE_PRIVATE);
    }

    MatchEngine load() {
        String raw = preferences.getString(CURRENT_KEY, null);
        if (raw == null) {
            Settings settings = loadLastSettings(Mode.AMERICANO);
            return new MatchEngine(Mode.AMERICANO, settings);
        }
        try {
            JSONObject root = new JSONObject(raw);
            if (root.optInt("formatVersion", -1) != MatchModel.FORMAT_VERSION) {
                throw new JSONException("Unsupported format");
            }
            State state = stateFromJson(root.getJSONObject("state"));
            JSONArray savedHistory = root.optJSONArray("history");
            List<State> history = new ArrayList<>();
            if (savedHistory != null) {
                for (int i = 0; i < savedHistory.length(); i++) {
                    try {
                        history.add(stateFromJson(savedHistory.getJSONObject(i)));
                    } catch (JSONException ignored) {
                    }
                }
            }
            return MatchEngine.restore(state, history);
        } catch (Exception ignored) {
            return new MatchEngine(Mode.AMERICANO, loadLastSettings(Mode.AMERICANO));
        }
    }

    void save(MatchEngine engine) {
        try {
            JSONObject root = new JSONObject();
            root.put("formatVersion", MatchModel.FORMAT_VERSION);
            root.put("state", stateToJson(engine.state()));
            JSONArray history = new JSONArray();
            List<State> entries = engine.history();
            int first = Math.max(0, entries.size() - 256);
            for (int i = first; i < entries.size(); i++) {
                history.put(stateToJson(entries.get(i)));
            }
            root.put("history", history);
            preferences.edit().putString(CURRENT_KEY, root.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    Settings loadLastSettings(Mode mode) {
        String raw = preferences.getString("settings_" + mode.name(), null);
        if (raw == null) {
            return Settings.defaults(mode);
        }
        try {
            return settingsFromJson(new JSONObject(raw), mode);
        } catch (Exception ignored) {
            return Settings.defaults(mode);
        }
    }

    void saveLastSettings(Settings settings) {
        try {
            preferences.edit()
                    .putString("settings_" + settings.mode.name(), settingsToJson(settings).toString())
                    .apply();
        } catch (JSONException ignored) {
        }
    }

    private static JSONObject settingsToJson(Settings settings) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("mode", settings.mode.name());
        json.put("target", settings.target);
        json.put("gamesPerSet", settings.gamesPerSet);
        json.put("setsToWin", settings.setsToWin);
        json.put("tieBreakTarget", settings.tieBreakTarget);
        json.put("winByTwo", settings.winByTwo);
        json.put("gameScoring", settings.gameScoring.name());
        json.put("setEnding", settings.setEnding.name());
        json.put("trackServe", settings.trackServe);
        json.put("serveEvery", settings.serveEvery);
        json.put("startingServer", settings.startingServer.name());
        return json;
    }

    private static Settings settingsFromJson(JSONObject json, Mode expectedMode) throws JSONException {
        Mode mode = Mode.valueOf(json.optString("mode", expectedMode.name()));
        if (mode != expectedMode) {
            throw new JSONException("Mode mismatch");
        }
        Settings settings = Settings.defaults(mode);
        settings.target = clamp(json.optInt("target", settings.target), 1, 99);
        settings.gamesPerSet = clamp(json.optInt("gamesPerSet", settings.gamesPerSet), 1, 12);
        settings.setsToWin = clamp(json.optInt("setsToWin", settings.setsToWin), 1, 3);
        settings.tieBreakTarget = clamp(json.optInt("tieBreakTarget", settings.tieBreakTarget), 1, 30);
        settings.winByTwo = json.optBoolean("winByTwo", settings.winByTwo);
        if (json.has("gameScoring")) {
            settings.gameScoring = GameScoring.valueOf(json.getString("gameScoring"));
        } else {
            settings.gameScoring = json.optBoolean("goldenPoint", false)
                    ? GameScoring.GOLDEN : GameScoring.ADVANTAGE;
        }
        if (json.has("setEnding")) {
            settings.setEnding = SetEnding.valueOf(json.getString("setEnding"));
        } else if (!json.optBoolean("winSetByTwo", true)) {
            settings.setEnding = SetEnding.FIRST_TO;
        } else if (!json.optBoolean("tieBreakEnabled", true)) {
            settings.setEnding = SetEnding.TWO_GAME_LEAD;
        } else {
            settings.setEnding = SetEnding.TIE_BREAK;
        }
        settings.trackServe = json.optBoolean("trackServe", settings.trackServe);
        settings.serveEvery = clamp(json.optInt("serveEvery", settings.serveEvery), 1, 16);
        settings.startingServer = Team.valueOf(
                json.optString("startingServer", settings.startingServer.name()));
        return settings;
    }

    private static JSONObject stateToJson(State state) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("formatVersion", state.formatVersion);
        json.put("mode", state.mode.name());
        json.put("settings", settingsToJson(state.settings));
        json.put("pointsA", state.pointsA);
        json.put("pointsB", state.pointsB);
        json.put("gamesA", state.gamesA);
        json.put("gamesB", state.gamesB);
        json.put("setsA", state.setsA);
        json.put("setsB", state.setsB);
        json.put("tieBreakPointsA", state.tieBreakPointsA);
        json.put("tieBreakPointsB", state.tieBreakPointsB);
        json.put("inTieBreak", state.inTieBreak);
        json.put("tieBreakStartingServer", state.tieBreakStartingServer == null
                ? JSONObject.NULL : state.tieBreakStartingServer.name());
        json.put("completed", state.completed);
        json.put("winner", state.winner == null ? JSONObject.NULL : state.winner);
        json.put("currentServer", state.currentServer.name());
        json.put("pointsSinceServerChange", state.pointsSinceServerChange);
        json.put("roundNumber", state.roundNumber);
        json.put("sessionPointsA", state.sessionPointsA);
        json.put("sessionPointsB", state.sessionPointsB);
        json.put("revision", state.revision);
        JSONArray rounds = new JSONArray();
        for (MatchModel.RoundResult result : state.roundHistory) {
            JSONObject round = new JSONObject();
            round.put("roundNumber", result.roundNumber);
            round.put("teamA", result.teamA);
            round.put("teamB", result.teamB);
            round.put("totalPoints", result.totalPoints);
            round.put("winner", result.winner);
            rounds.put(round);
        }
        json.put("roundHistory", rounds);
        return json;
    }

    private static State stateFromJson(JSONObject json) throws JSONException {
        if (json.optInt("formatVersion", -1) != MatchModel.FORMAT_VERSION) {
            throw new JSONException("Unsupported state");
        }
        Mode mode = Mode.valueOf(json.getString("mode"));
        Settings settings = settingsFromJson(json.getJSONObject("settings"), mode);
        State state = State.initial(mode, settings);
        state.pointsA = nonNegative(json.getInt("pointsA"));
        state.pointsB = nonNegative(json.getInt("pointsB"));
        state.gamesA = nonNegative(json.optInt("gamesA"));
        state.gamesB = nonNegative(json.optInt("gamesB"));
        state.setsA = nonNegative(json.optInt("setsA"));
        state.setsB = nonNegative(json.optInt("setsB"));
        state.tieBreakPointsA = nonNegative(json.optInt("tieBreakPointsA"));
        state.tieBreakPointsB = nonNegative(json.optInt("tieBreakPointsB"));
        state.inTieBreak = json.optBoolean("inTieBreak");
        String tieBreakServer = json.optString("tieBreakStartingServer", "");
        state.tieBreakStartingServer = "A".equals(tieBreakServer) ? Team.A
                : "B".equals(tieBreakServer) ? Team.B : null;
        state.completed = json.optBoolean("completed");
        state.winner = json.isNull("winner") ? null : json.optString("winner", null);
        state.currentServer = Team.valueOf(json.optString("currentServer", "A"));
        state.pointsSinceServerChange = nonNegative(json.optInt("pointsSinceServerChange"));
        state.roundNumber = Math.max(1, json.optInt("roundNumber", 1));
        state.sessionPointsA = nonNegative(json.optInt("sessionPointsA"));
        state.sessionPointsB = nonNegative(json.optInt("sessionPointsB"));
        state.revision = nonNegative(json.optInt("revision"));
        JSONArray rounds = json.optJSONArray("roundHistory");
        if (rounds != null) {
            for (int i = 0; i < rounds.length(); i++) {
                JSONObject item = rounds.getJSONObject(i);
                MatchModel.RoundResult result = new MatchModel.RoundResult();
                result.roundNumber = Math.max(1, item.optInt("roundNumber", i + 1));
                result.teamA = nonNegative(item.optInt("teamA"));
                result.teamB = nonNegative(item.optInt("teamB"));
                result.totalPoints = Math.max(1, item.optInt("totalPoints", settings.target));
                result.winner = item.optString("winner", "DRAW");
                state.roundHistory.add(result);
            }
        }
        return state;
    }

    private static int nonNegative(int value) throws JSONException {
        if (value < 0) {
            throw new JSONException("Negative score");
        }
        return value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
