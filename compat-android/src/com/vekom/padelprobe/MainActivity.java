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
            text(canvas, "PADEL", BASE / 2, 48, 19, muted, Paint.Align.CENTER, true);
            text(canvas, "SCORE", BASE / 2, 80, 34, green, Paint.Align.CENTER, true);
            text(canvas, "Choose match format", BASE / 2, 104, 14, muted, Paint.Align.CENTER, false);

            Mode[] modes = Mode.values();
            for (int i = 0; i < modes.length; i++) {
                int column = i % 2;
                int row = i / 2;
                float left = column == 0 ? 48 : 239;
                float top = 126 + row * 62;
                button(canvas, left, top, left + 179, top + 50,
                        shortMode(modes[i]), panel, Color.WHITE, 15);
            }
            if (hasSavedMatch()) {
                button(canvas, 82, 326, 384, 378,
                        engine.state().completed ? "VIEW LAST MATCH" : "RESUME MATCH",
                        green, background, 16);
            }
            text(canvas, "Tap a format to configure", BASE / 2, 411, 13,
                    muted, Paint.Align.CENTER, false);
        }

        private void drawSettings(Canvas canvas) {
            text(canvas, "SETTINGS", BASE / 2, 43, 26, green, Paint.Align.CENTER, true);
            text(canvas, editing.mode.label.toUpperCase(), BASE / 2, 69, 14,
                    muted, Paint.Align.CENTER, true);
            int count = settingsRowCount();
            for (int i = 0; i < count; i++) {
                drawSettingRow(canvas, i, 84 + i * 49);
            }
            button(canvas, 64, 356, 220, 408, "BACK", panel, Color.WHITE, 17);
            button(canvas, 246, 356, 402, 408, "START", green, background, 17);
            text(canvas, "Targets: 7 / 10 / 15 / 21 / 24 / 32", BASE / 2, 435,
                    11, muted, Paint.Align.CENTER, false);
        }

        private void drawSettingRow(Canvas canvas, int index, float top) {
            String label = settingLabel(index);
            String value = settingValue(index);
            rounded(canvas, 53, top, 413, top + 42, 16, panel);
            text(canvas, label, 73, top + 27, 14, Color.WHITE, Paint.Align.LEFT, false);
            if (isNumericSetting(index)) {
                circleButton(canvas, 302, top + 21, 16, "-", panelLight, Color.WHITE, 20);
                text(canvas, value, 350, top + 27, 16, green, Paint.Align.CENTER, true);
                circleButton(canvas, 398, top + 21, 16, "+", panelLight, Color.WHITE, 18);
            } else {
                rounded(canvas, 320, top + 7, 402, top + 35, 14,
                        isOnValue(value) ? green : panelLight);
                text(canvas, value, 361, top + 27, 12,
                        isOnValue(value) ? background : Color.WHITE,
                        Paint.Align.CENTER, true);
            }
        }

        private void drawMatch(Canvas canvas) {
            State state = engine.state();
            rounded(canvas, 39, 22, 427, 184, 46, Color.rgb(8, 34, 45));
            rounded(canvas, 39, 282, 427, 444, 46, Color.rgb(43, 29, 10));
            text(canvas, state.currentServer == Team.A ? "*  TEAM A" : "TEAM A",
                    BASE / 2, 55, 15, teamA, Paint.Align.CENTER, true);
            text(canvas, engine.score(Team.A), BASE / 2, 128, 70,
                    Color.WHITE, Paint.Align.CENTER, true);
            text(canvas, state.completed ? "FINISHED" : "+ POINT", BASE / 2, 165,
                    13, muted, Paint.Align.CENTER, true);

            text(canvas, engine.modeLabel(), BASE / 2, 207, 15,
                    green, Paint.Align.CENTER, true);
            text(canvas, engine.detailLabel(), BASE / 2, 229, 12,
                    muted, Paint.Align.CENTER, false);
            text(canvas, engine.statusLabel(), BASE / 2, 250, 12,
                    state.completed ? green : Color.WHITE, Paint.Align.CENTER, true);

            smallButton(canvas, 28, 258, 126, 287, "UNDO",
                    engine.canUndo() ? panelLight : panel, engine.canUndo() ? Color.WHITE : muted);
            smallButton(canvas, 134, 258, 232, 287, "MENU", panelLight, Color.WHITE);
            if (state.mode == Mode.AMERICANO && state.completed) {
                smallButton(canvas, 240, 258, 438, 287, "NEXT ROUND", green, background);
            } else {
                smallButton(canvas, 240, 258, 438, 287,
                        state.settings.trackServe ? "CHANGE SERVE" : "HISTORY",
                        panelLight, Color.WHITE);
            }

            text(canvas, state.currentServer == Team.B ? "*  TEAM B" : "TEAM B",
                    BASE / 2, 316, 15, teamB, Paint.Align.CENTER, true);
            text(canvas, engine.score(Team.B), BASE / 2, 389, 70,
                    Color.WHITE, Paint.Align.CENTER, true);
            text(canvas, state.completed ? "FINISHED" : "+ POINT", BASE / 2, 426,
                    13, muted, Paint.Align.CENTER, true);
        }

        private void drawMenu(Canvas canvas) {
            text(canvas, "MATCH MENU", BASE / 2, 54, 26, green, Paint.Align.CENTER, true);
            String[] items = {"CONTINUE", "CHANGE SERVE", "HISTORY", "RESET MATCH", "NEW MATCH"};
            for (int i = 0; i < items.length; i++) {
                int color = i == 3 || i == 4 ? danger : Color.WHITE;
                button(canvas, 66, 78 + i * 60, 400, 128 + i * 60,
                        items[i], panel, color, 16);
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
            if (y >= 126 && y <= 300) {
                int row = (int) ((y - 126) / 62);
                int column = x < 233 ? 0 : 1;
                int index = row * 2 + column;
                if (index >= 0 && index < Mode.values().length) {
                    Mode mode = Mode.values()[index];
                    editing = store.loadLastSettings(mode);
                    screen = Screen.SETTINGS;
                }
            } else if (hasSavedMatch() && inside(x, y, 82, 326, 384, 378)) {
                screen = Screen.MATCH;
                keepAwake(!engine.state().completed);
            }
        }

        private void handleSettingsTap(float x, float y) {
            if (inside(x, y, 64, 356, 220, 408)) {
                screen = Screen.HOME;
                return;
            }
            if (inside(x, y, 246, 356, 402, 408)) {
                store.saveLastSettings(editing);
                engine = new MatchEngine(editing.mode, editing);
                store.save(engine);
                screen = Screen.MATCH;
                keepAwake(true);
                return;
            }
            int index = (int) ((y - 84) / 49);
            if (y >= 84 && index >= 0 && index < settingsRowCount()) {
                adjustSetting(index, x >= 354 ? 1 : x >= 280 ? -1 : 0);
            }
        }

        private void handleMatchTap(float x, float y) {
            if (inside(x, y, 39, 22, 427, 184)) {
                addPoint(Team.A);
                return;
            }
            if (inside(x, y, 39, 292, 427, 444)) {
                addPoint(Team.B);
                return;
            }
            if (inside(x, y, 28, 258, 126, 290)) {
                if (engine.undo()) {
                    store.save(engine);
                    keepAwake(!engine.state().completed);
                    buzz(18);
                }
                return;
            }
            if (inside(x, y, 134, 258, 232, 290)) {
                screen = Screen.MENU;
                keepAwake(false);
                return;
            }
            if (inside(x, y, 240, 258, 438, 290)) {
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
            if (x < 55 || x > 411 || y < 78 || y > 368) {
                return;
            }
            int index = (int) ((y - 78) / 60);
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
                                 String label, int fill, int color) {
            rounded(canvas, left, top, right, bottom, 13, fill);
            text(canvas, label, (left + right) / 2, top + 20, 11, color,
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
