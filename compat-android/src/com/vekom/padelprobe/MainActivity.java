package com.vekom.padelprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.List;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.Settings;
import static com.vekom.padelprobe.MatchModel.State;
import static com.vekom.padelprobe.MatchModel.Team;

public final class MainActivity extends Activity {
    private MatchStore store;
    private MatchEngine engine;
    private ScoreView scoreView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        store = new MatchStore(this);
        engine = store.load();
        scoreView = new ScoreView();
        setContentView(scoreView);
        setImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setImmersive();
    }

    @Override
    protected void onPause() {
        store.save(engine);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (scoreView.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void keepAwake(boolean enabled) {
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void buzz(long milliseconds) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(milliseconds);
        }
    }

    private enum Screen {
        HOME,
        SETTINGS,
        MATCH,
        MENU,
        HISTORY,
        CONFIRM_RESET,
        CONFIRM_EXIT,
        CONFIRM_CLEAR
    }

    private final class ScoreView extends View {
        private static final float BASE = 466f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectangle = new RectF();
        private final int background = Color.rgb(4, 13, 10);
        private final int panel = Color.rgb(16, 34, 28);
        private final int panelLight = Color.rgb(25, 51, 42);
        private final int classicPanel = Color.rgb(10, 72, 83);
        private final int tieBreakPanel = Color.rgb(65, 49, 96);
        private final int pointsPanel = Color.rgb(91, 62, 24);
        private final int dangerPanel = Color.rgb(64, 25, 29);
        private final int green = Color.rgb(42, 232, 143);
        private final int teamA = Color.rgb(49, 196, 255);
        private final int teamB = Color.rgb(255, 179, 71);
        private final int muted = Color.rgb(151, 176, 166);
        private final int danger = Color.rgb(255, 102, 112);
        private Screen screen = Screen.HOME;
        private Screen returnFromHistory = Screen.MENU;
        private Settings editing;
        private float scale = 1f;
        private float offsetX;
        private float offsetY;
        private boolean touching;
        private float touchX;
        private float touchY;

        ScoreView() {
            super(MainActivity.this);
            setBackgroundColor(background);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            scale = Math.min(getWidth(), getHeight()) / BASE;
            offsetX = (getWidth() - BASE * scale) / 2f;
            offsetY = (getHeight() - BASE * scale) / 2f;
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(scale, scale);
            canvas.drawColor(background);
            if (screen == Screen.HOME) {
                drawHome(canvas);
            } else if (screen == Screen.SETTINGS) {
                drawSettings(canvas);
            } else if (screen == Screen.MATCH) {
                drawMatch(canvas);
            } else if (screen == Screen.MENU) {
                drawMenu(canvas);
            } else if (screen == Screen.HISTORY) {
                drawHistory(canvas);
            } else {
                drawConfirmation(canvas);
            }
            if (touching) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(45, 255, 255, 255));
                canvas.drawCircle(touchX, touchY, 22, paint);
            }
            canvas.restore();
        }

        private void drawHome(Canvas canvas) {
            text(canvas, "PADEL", BASE / 2, 39, 18, muted, Paint.Align.CENTER, true);
            text(canvas, "SCORE", BASE / 2, 70, 33, green, Paint.Align.CENTER, true);
            text(canvas, "Choose match format", BASE / 2, 94, 15, muted, Paint.Align.CENTER, false);

            Mode[] modes = Mode.values();
            for (int i = 0; i < modes.length; i++) {
                int column = i % 2;
                int row = i / 2;
                float left = column == 0 ? 38 : 238;
                float top = 108 + row * 68;
                int fill = row == 0 ? classicPanel : row == 1 ? tieBreakPanel : pointsPanel;
                button(canvas, left, top, left + 190, top + 58,
                        shortMode(modes[i]), fill, Color.WHITE, 17);
            }
            if (hasSavedMatch()) {
                button(canvas, 70, 320, 396, 380,
                        engine.state().completed ? "VIEW LAST MATCH" : "RESUME MATCH",
                        green, background, 18);
            }
            text(canvas, "Tap a format to configure", BASE / 2, 414, 14,
                    muted, Paint.Align.CENTER, false);
        }

        private void drawSettings(Canvas canvas) {
            text(canvas, "SETTINGS", BASE / 2, 40, 28, green, Paint.Align.CENTER, true);
            text(canvas, editing.mode.label.toUpperCase(), BASE / 2, 66, 15,
                    muted, Paint.Align.CENTER, true);
            int count = settingsRowCount();
            for (int i = 0; i < count; i++) {
                drawSettingRow(canvas, i, 78 + i * 52);
            }
            button(canvas, 58, 348, 222, 414, "BACK", panelLight, Color.WHITE, 19);
            button(canvas, 244, 348, 408, 414, "START", green, background, 19);
            text(canvas, "Targets: 7 / 10 / 15 / 21 / 24 / 32", BASE / 2, 442,
                    12, muted, Paint.Align.CENTER, false);
        }

        private void drawSettingRow(Canvas canvas, int index, float top) {
            String label = settingLabel(index);
            String value = settingValue(index);
            rounded(canvas, 50, top, 416, top + 46, 18, panel);
            text(canvas, label, 72, top + 30, 15, Color.WHITE, Paint.Align.LEFT, false);
            if (isNumericSetting(index)) {
                circleButton(canvas, 300, top + 23, 18, "-", panelLight, Color.WHITE, 22);
                text(canvas, value, 350, top + 30, 18, green, Paint.Align.CENTER, true);
                circleButton(canvas, 400, top + 23, 18, "+", panelLight, Color.WHITE, 20);
            } else {
                rounded(canvas, 316, top + 7, 407, top + 39, 16,
                        isOnValue(value) ? green : panelLight);
                text(canvas, value, 361, top + 29, 13,
                        isOnValue(value) ? background : Color.WHITE,
                        Paint.Align.CENTER, true);
            }
        }

        private void drawMatch(Canvas canvas) {
            State state = engine.state();
            rounded(canvas, 39, 18, 427, 176, 46, Color.rgb(8, 34, 45));
            rounded(canvas, 39, 298, 427, 448, 46, Color.rgb(43, 29, 10));
            text(canvas, state.currentServer == Team.A ? "*  TEAM A" : "TEAM A",
                    BASE / 2, 50, 16, teamA, Paint.Align.CENTER, true);
            text(canvas, engine.score(Team.A), BASE / 2, 122, 70,
                    Color.WHITE, Paint.Align.CENTER, true);
            text(canvas, state.completed ? "FINISHED" : "+ POINT", BASE / 2, 158,
                    14, muted, Paint.Align.CENTER, true);

            text(canvas, engine.modeLabel(), BASE / 2, 195, 16,
                    green, Paint.Align.CENTER, true);
            text(canvas, engine.detailLabel(), BASE / 2, 217, 13,
                    muted, Paint.Align.CENTER, false);
            text(canvas, engine.statusLabel(), BASE / 2, 237, 13,
                    state.completed ? green : Color.WHITE, Paint.Align.CENTER, true);

            smallButton(canvas, 28, 246, 128, 291, "UNDO",
                    engine.canUndo() ? panelLight : panel,
                    engine.canUndo() ? Color.WHITE : muted, 13);
            smallButton(canvas, 136, 246, 236, 291, "MENU", green, background, 13);
            if (state.mode == Mode.AMERICANO && state.completed) {
                smallButton(canvas, 244, 246, 438, 291, "NEXT ROUND", green, background, 13);
            } else {
                smallButton(canvas, 244, 246, 438, 291,
                        state.settings.trackServe ? "CHANGE SERVE" : "HISTORY",
                        panelLight, Color.WHITE, 12);
            }

            text(canvas, state.currentServer == Team.B ? "*  TEAM B" : "TEAM B",
                    BASE / 2, 326, 16, teamB, Paint.Align.CENTER, true);
            text(canvas, engine.score(Team.B), BASE / 2, 393, 68,
                    Color.WHITE, Paint.Align.CENTER, true);
            text(canvas, state.completed ? "FINISHED" : "+ POINT", BASE / 2, 431,
                    14, muted, Paint.Align.CENTER, true);
        }

        private void drawMenu(Canvas canvas) {
            text(canvas, "MATCH MENU", BASE / 2, 49, 29, green, Paint.Align.CENTER, true);
            String[] items = {"CONTINUE", "CHANGE SERVE", "HISTORY", "RESET MATCH", "NEW MATCH"};
            for (int i = 0; i < items.length; i++) {
                int fill = i == 0 ? green : i >= 3 ? dangerPanel : panelLight;
                int color = i == 0 ? background : i >= 3 ? danger : Color.WHITE;
                button(canvas, 54, 66 + i * 68, 412, 128 + i * 68,
                        items[i], fill, color, 19);
            }
        }

        private void drawHistory(Canvas canvas) {
            State state = engine.state();
            text(canvas, "HISTORY", BASE / 2, 48, 26, green, Paint.Align.CENTER, true);
            text(canvas, "SESSION  " + state.sessionPointsA + " : " + state.sessionPointsB,
                    BASE / 2, 78, 17, Color.WHITE, Paint.Align.CENTER, true);
            if (state.roundHistory.isEmpty()) {
                text(canvas, "No completed rounds yet", BASE / 2, 194, 15,
                        muted, Paint.Align.CENTER, false);
            } else {
                List<MatchModel.RoundResult> rounds = state.roundHistory;
                int first = Math.max(0, rounds.size() - 5);
                int line = 0;
                for (int i = rounds.size() - 1; i >= first; i--) {
                    MatchModel.RoundResult result = rounds.get(i);
                    float top = 100 + line * 48;
                    rounded(canvas, 68, top, 398, top + 39, 14, panel);
                    text(canvas, "ROUND " + result.roundNumber, 87, top + 25, 13,
                            muted, Paint.Align.LEFT, true);
                    text(canvas, result.teamA + " : " + result.teamB, 286, top + 26, 18,
                            Color.WHITE, Paint.Align.CENTER, true);
                    text(canvas, result.winner, 376, top + 25, 13,
                            green, Paint.Align.RIGHT, true);
                    line++;
                }
            }
            button(canvas, 65, 362, 224, 410, "BACK", panel, Color.WHITE, 16);
            button(canvas, 242, 362, 401, 410, "CLEAR", panel,
                    state.roundHistory.isEmpty() ? muted : danger, 16);
        }

        private void drawConfirmation(Canvas canvas) {
            String title;
            String message;
            if (screen == Screen.CONFIRM_RESET) {
                title = "RESET MATCH?";
                message = "Current score will be cleared";
            } else if (screen == Screen.CONFIRM_CLEAR) {
                title = "CLEAR HISTORY?";
                message = "Session totals will be cleared";
            } else {
                title = "NEW MATCH?";
                message = "Current match stays saved until replaced";
            }
            text(canvas, title, BASE / 2, 139, 27, danger, Paint.Align.CENTER, true);
            text(canvas, message, BASE / 2, 181, 14, muted, Paint.Align.CENTER, false);
            button(canvas, 62, 235, 220, 294, "CANCEL", panel, Color.WHITE, 17);
            button(canvas, 246, 235, 404, 294, "CONFIRM", danger, Color.WHITE, 17);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = (event.getX() - offsetX) / scale;
            float y = (event.getY() - offsetY) / scale;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touching = true;
                touchX = x;
                touchY = y;
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                touching = false;
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                touching = false;
                handleTap(x, y);
                invalidate();
                return true;
            }
            return true;
        }

        private void handleTap(float x, float y) {
            if (screen == Screen.HOME) {
                handleHomeTap(x, y);
            } else if (screen == Screen.SETTINGS) {
                handleSettingsTap(x, y);
            } else if (screen == Screen.MATCH) {
                handleMatchTap(x, y);
            } else if (screen == Screen.MENU) {
                handleMenuTap(x, y);
            } else if (screen == Screen.HISTORY) {
                handleHistoryTap(x, y);
            } else {
                handleConfirmTap(x, y);
            }
        }

        private void handleHomeTap(float x, float y) {
            Mode[] modes = Mode.values();
            for (int i = 0; i < modes.length; i++) {
                int column = i % 2;
                int row = i / 2;
                float left = column == 0 ? 38 : 238;
                float top = 108 + row * 68;
                if (inside(x, y, left, top, left + 190, top + 58)) {
                    editing = store.loadLastSettings(modes[i]);
                    screen = Screen.SETTINGS;
                    return;
                }
            }
            if (hasSavedMatch() && inside(x, y, 70, 320, 396, 380)) {
                screen = Screen.MATCH;
                keepAwake(!engine.state().completed);
            }
        }

        private void handleSettingsTap(float x, float y) {
            if (inside(x, y, 58, 342, 222, 420)) {
                screen = Screen.HOME;
                return;
            }
            if (inside(x, y, 244, 342, 408, 420)) {
                store.saveLastSettings(editing);
                engine = new MatchEngine(editing.mode, editing);
                store.save(engine);
                screen = Screen.MATCH;
                keepAwake(true);
                return;
            }
            int index = (int) ((y - 78) / 52);
            float rowTop = 78 + index * 52;
            if (y >= 78 && index >= 0 && index < settingsRowCount()
                    && y <= rowTop + 46) {
                if (!isNumericSetting(index)) {
                    adjustSetting(index, 0);
                } else if (x >= 264 && x <= 330) {
                    adjustSetting(index, -1);
                } else if (x >= 370 && x <= 436) {
                    adjustSetting(index, 1);
                }
            }
        }

        private void handleMatchTap(float x, float y) {
            if (inside(x, y, 39, 18, 427, 176)) {
                addPoint(Team.A);
                return;
            }
            if (inside(x, y, 39, 298, 427, 448)) {
                addPoint(Team.B);
                return;
            }
            if (inside(x, y, 28, 241, 128, 296)) {
                if (engine.undo()) {
                    store.save(engine);
                    keepAwake(!engine.state().completed);
                    buzz(18);
                }
                return;
            }
            if (inside(x, y, 136, 241, 236, 296)) {
                screen = Screen.MENU;
                keepAwake(false);
                return;
            }
            if (inside(x, y, 244, 241, 438, 296)) {
                State state = engine.state();
                if (state.mode == Mode.AMERICANO && state.completed) {
                    if (engine.startNextRound()) {
                        store.save(engine);
                        keepAwake(true);
                    }
                } else if (state.settings.trackServe) {
                    engine.changeServer();
                    store.save(engine);
                    buzz(18);
                } else {
                    returnFromHistory = Screen.MATCH;
                    screen = Screen.HISTORY;
                    keepAwake(false);
                }
            }
        }

        private void addPoint(Team team) {
            MatchEngine.Result result = engine.point(team);
            if (result.accepted) {
                store.save(engine);
                buzz(result.completedNow ? 120 : 35);
                if (result.completedNow) {
                    keepAwake(false);
                }
            }
        }

        private void handleMenuTap(float x, float y) {
            if (x < 46 || x > 420 || y < 66 || y > 400) {
                return;
            }
            int index = (int) ((y - 66) / 68);
            float top = 66 + index * 68;
            if (index < 0 || index > 4 || y > top + 62) {
                return;
            }
            if (index == 0) {
                screen = Screen.MATCH;
                keepAwake(!engine.state().completed);
            } else if (index == 1) {
                engine.changeServer();
                store.save(engine);
                buzz(18);
                screen = Screen.MATCH;
                keepAwake(!engine.state().completed);
            } else if (index == 2) {
                returnFromHistory = Screen.MENU;
                screen = Screen.HISTORY;
            } else if (index == 3) {
                screen = Screen.CONFIRM_RESET;
            } else if (index == 4) {
                screen = Screen.CONFIRM_EXIT;
            }
        }

        private void handleHistoryTap(float x, float y) {
            if (inside(x, y, 65, 352, 224, 421)) {
                screen = returnFromHistory;
                keepAwake(screen == Screen.MATCH && !engine.state().completed);
            } else if (inside(x, y, 242, 352, 401, 421)
                    && !engine.state().roundHistory.isEmpty()) {
                screen = Screen.CONFIRM_CLEAR;
            }
        }

        private void handleConfirmTap(float x, float y) {
            if (inside(x, y, 62, 225, 220, 305)) {
                screen = screen == Screen.CONFIRM_CLEAR ? Screen.HISTORY : Screen.MENU;
                return;
            }
            if (!inside(x, y, 246, 225, 404, 305)) {
                return;
            }
            if (screen == Screen.CONFIRM_RESET) {
                engine.resetCurrent();
                store.save(engine);
                screen = Screen.MATCH;
                keepAwake(true);
            } else if (screen == Screen.CONFIRM_CLEAR) {
                engine.clearSession();
                store.save(engine);
                screen = Screen.HISTORY;
            } else {
                screen = Screen.HOME;
                keepAwake(false);
            }
        }

        boolean handleBack() {
            if (screen == Screen.HOME) {
                return false;
            }
            if (screen == Screen.SETTINGS) {
                screen = Screen.HOME;
            } else if (screen == Screen.MATCH) {
                screen = hasSavedMatch() && !engine.state().completed
                        ? Screen.CONFIRM_EXIT : Screen.HOME;
                keepAwake(false);
            } else if (screen == Screen.MENU) {
                screen = Screen.MATCH;
                keepAwake(!engine.state().completed);
            } else if (screen == Screen.HISTORY) {
                screen = returnFromHistory;
                keepAwake(screen == Screen.MATCH && !engine.state().completed);
            } else if (screen == Screen.CONFIRM_CLEAR) {
                screen = Screen.HISTORY;
            } else {
                screen = Screen.MENU;
            }
            invalidate();
            return true;
        }

        private int settingsRowCount() {
            if (editing.mode == Mode.CLASSIC) {
                return 5;
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return 4;
            }
            if (editing.mode == Mode.AMERICANO) {
                return 4;
            }
            return 3;
        }

        private String settingLabel(int index) {
            if (editing.mode == Mode.CLASSIC) {
                return new String[]{"Sets to win", "Games per set", "Golden point",
                        "Win set by 2", "Tie-break"}[index];
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return new String[]{"Games per set", "Golden point", "Win set by 2",
                        "Tie-break"}[index];
            }
            if (editing.mode == Mode.AMERICANO) {
                return new String[]{"Total points", "Track serve", "Serve every",
                        "Starting server"}[index];
            }
            return new String[]{"Target", "Win by 2", "Starting server"}[index];
        }

        private String settingValue(int index) {
            if (editing.mode == Mode.CLASSIC) {
                if (index == 0) return Integer.toString(editing.setsToWin);
                if (index == 1) return Integer.toString(editing.gamesPerSet);
                if (index == 2) return yesNo(editing.goldenPoint);
                if (index == 3) return yesNo(editing.winSetByTwo);
                return yesNo(editing.tieBreakEnabled);
            }
            if (editing.mode == Mode.SINGLE_SET) {
                if (index == 0) return Integer.toString(editing.gamesPerSet);
                if (index == 1) return yesNo(editing.goldenPoint);
                if (index == 2) return yesNo(editing.winSetByTwo);
                return yesNo(editing.tieBreakEnabled);
            }
            if (editing.mode == Mode.AMERICANO) {
                if (index == 0) return Integer.toString(editing.target);
                if (index == 1) return yesNo(editing.trackServe);
                if (index == 2) return Integer.toString(editing.serveEvery);
                return editing.startingServer.name();
            }
            if (index == 0) return Integer.toString(editing.target);
            if (index == 1) return yesNo(editing.winByTwo);
            return editing.startingServer.name();
        }

        private boolean isNumericSetting(int index) {
            if (editing.mode == Mode.CLASSIC) return index < 2;
            if (editing.mode == Mode.SINGLE_SET) return index == 0;
            if (editing.mode == Mode.AMERICANO) return index == 0 || index == 2;
            return index == 0;
        }

        private void adjustSetting(int index, int direction) {
            if (!isNumericSetting(index)) {
                if (editing.mode == Mode.CLASSIC) {
                    if (index == 2) editing.goldenPoint = !editing.goldenPoint;
                    else if (index == 3) editing.winSetByTwo = !editing.winSetByTwo;
                    else editing.tieBreakEnabled = !editing.tieBreakEnabled;
                } else if (editing.mode == Mode.SINGLE_SET) {
                    if (index == 1) editing.goldenPoint = !editing.goldenPoint;
                    else if (index == 2) editing.winSetByTwo = !editing.winSetByTwo;
                    else editing.tieBreakEnabled = !editing.tieBreakEnabled;
                } else if (editing.mode == Mode.AMERICANO) {
                    if (index == 1) editing.trackServe = !editing.trackServe;
                    else editing.startingServer = editing.startingServer.other();
                } else if (index == 1) {
                    editing.winByTwo = !editing.winByTwo;
                } else {
                    editing.startingServer = editing.startingServer.other();
                }
                return;
            }
            if (direction == 0) {
                direction = 1;
            }
            if (editing.mode == Mode.CLASSIC) {
                if (index == 0) editing.setsToWin = clamp(editing.setsToWin + direction, 1, 3);
                else editing.gamesPerSet = clamp(editing.gamesPerSet + direction, 1, 12);
            } else if (editing.mode == Mode.SINGLE_SET) {
                editing.gamesPerSet = clamp(editing.gamesPerSet + direction, 1, 12);
            } else if (editing.mode == Mode.AMERICANO && index == 2) {
                editing.serveEvery = clamp(editing.serveEvery + direction, 1, 16);
            } else {
                editing.target = nextPreset(editing.target, direction);
            }
        }

        private int nextPreset(int current, int direction) {
            int[] presets = {7, 10, 15, 21, 24, 32};
            if (direction > 0) {
                for (int preset : presets) {
                    if (preset > current) return preset;
                }
                return presets[0];
            }
            for (int i = presets.length - 1; i >= 0; i--) {
                if (presets[i] < current) return presets[i];
            }
            return presets[presets.length - 1];
        }

        private boolean hasSavedMatch() {
            State state = engine.state();
            return state.revision > 0 || state.completed || !state.roundHistory.isEmpty();
        }

        private String shortMode(Mode mode) {
            if (mode == Mode.CLASSIC) return "CLASSIC";
            if (mode == Mode.SINGLE_SET) return "SINGLE SET";
            if (mode == Mode.TIE_BREAK) return "TIE-BREAK";
            if (mode == Mode.SUPER_TIE_BREAK) return "SUPER TB";
            if (mode == Mode.RACE_TO_N) return "RACE TO N";
            return "AMERICANO";
        }

        private String yesNo(boolean value) {
            return value ? "ON" : "OFF";
        }

        private boolean isOnValue(String value) {
            return "ON".equals(value) || "A".equals(value) || "B".equals(value);
        }

        private int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private boolean inside(float x, float y, float left, float top, float right, float bottom) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        private void rounded(Canvas canvas, float left, float top, float right,
                             float bottom, float radius, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            rectangle.set(left, top, right, bottom);
            canvas.drawRoundRect(rectangle, radius, radius, paint);
        }

        private void button(Canvas canvas, float left, float top, float right, float bottom,
                            String label, int fill, int color, float size) {
            rounded(canvas, left, top, right, bottom, 22, fill);
            text(canvas, label, (left + right) / 2, (top + bottom) / 2 + size * 0.36f,
                    size, color, Paint.Align.CENTER, true);
        }

        private void smallButton(Canvas canvas, float left, float top, float right, float bottom,
                                 String label, int fill, int color, float size) {
            rounded(canvas, left, top, right, bottom, 18, fill);
            text(canvas, label, (left + right) / 2,
                    (top + bottom) / 2 + size * 0.36f, size, color,
                    Paint.Align.CENTER, true);
        }

        private void circleButton(Canvas canvas, float x, float y, float radius,
                                  String label, int fill, int color, float size) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            canvas.drawCircle(x, y, radius, paint);
            text(canvas, label, x, y + size * 0.34f, size, color, Paint.Align.CENTER, true);
        }

        private void text(Canvas canvas, String value, float x, float baseline, float size,
                          int color, Paint.Align align, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            canvas.drawText(value, x, baseline, paint);
        }
    }
}
