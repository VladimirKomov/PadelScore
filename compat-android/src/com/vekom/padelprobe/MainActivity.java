package com.vekom.padelprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.OverScroller;

import java.util.List;
import java.util.Collections;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.GameScoring;
import static com.vekom.padelprobe.MatchModel.SetEnding;
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

    private void buzzStarPoint() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        long[] pattern = {0, 38, 55, 70};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    private enum Screen {
        HOME,
        SETTINGS,
        PICKER,
        MATCH,
        MENU,
        HISTORY,
        CONFIRM_RESET,
        CONFIRM_EXIT,
        CONFIRM_CLEAR
    }

    private final class ScoreView extends View {
        private static final float BASE = 466f;
        private static final float ROW_HEIGHT = 90f;
        private static final float ROW_STEP = 100f;
        private static final float PICKER_HEIGHT = 84f;
        private static final float PICKER_STEP = 94f;
        private static final float NESTED_HEADER_BOTTOM = 100f;
        private static final float NESTED_CONTENT_TOP = 108f;
        private static final float BOTTOM_SCROLL_SPACE = 160f;
        private static final float ROTARY_SCROLL_STEP = 68f;
        private static final float BACK_SWIPE_DISTANCE = 110f;
        private static final long SCORE_FLASH_MILLISECONDS = 420L;
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
        private final Mode[] homeModes = {
                Mode.CLASSIC,
                Mode.AMERICANO,
                Mode.SINGLE_SET,
                Mode.TIE_BREAK,
                Mode.SUPER_TIE_BREAK,
                Mode.RACE_TO_N
        };
        private final OverScroller scroller;
        private final int touchSlop;
        private final int minimumFlingVelocity;
        private final int maximumFlingVelocity;
        private Screen screen = Screen.HOME;
        private Screen returnFromHistory = Screen.MENU;
        private Settings editing;
        private VelocityTracker velocityTracker;
        private float scale = 1f;
        private float offsetX;
        private float offsetY;
        private float scrollOffset;
        private float scrollMax;
        private float downX;
        private float downY;
        private float lastY;
        private float settingsScrollRestore;
        private int pickerSettingIndex = -1;
        private Team scoreFlashTeam;
        private long scoreFlashUntil;
        private boolean dragging;
        private boolean gestureDirectionLocked;
        private boolean horizontalBackGesture;
        private boolean nestedGestureExclusion;
        private int gestureExclusionWidth = -1;
        private int gestureExclusionHeight = -1;
        private boolean touching;
        private float touchX;
        private float touchY;

        ScoreView() {
            super(MainActivity.this);
            setBackgroundColor(background);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
            scroller = new OverScroller(MainActivity.this);
            ViewConfiguration configuration = ViewConfiguration.get(MainActivity.this);
            touchSlop = configuration.getScaledTouchSlop();
            minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
            maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updateSystemGestureExclusion();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updateSystemGestureExclusion();
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
            } else if (screen == Screen.PICKER) {
                drawPicker(canvas);
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

        private void updateSystemGestureExclusion() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            boolean nested = screen != Screen.HOME;
            if (nested == nestedGestureExclusion
                    && getWidth() == gestureExclusionWidth
                    && getHeight() == gestureExclusionHeight) {
                return;
            }
            MainActivity.this.getWindow().getDecorView().setSystemGestureExclusionRects(nested
                    ? Collections.singletonList(new Rect(0, 0, getWidth(), getHeight()))
                    : Collections.emptyList());
            nestedGestureExclusion = nested;
            gestureExclusionWidth = getWidth();
            gestureExclusionHeight = getHeight();
        }

        private void drawHome(Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, 78, BASE, 456);
            canvas.translate(0, -scrollOffset);
            float top = 88;
            if (hasSavedMatch()) {
                drawResumeRow(canvas, top);
                top += ROW_STEP;
            }
            for (Mode mode : homeModes) {
                drawModeRow(canvas, mode, top);
                top += ROW_STEP;
            }
            canvas.restore();
            setScrollBounds(top);
            drawScrollIndicator(canvas);
            drawHomeHeader(canvas);
        }

        private void drawSettings(Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, NESTED_HEADER_BOTTOM, BASE, 456);
            canvas.translate(0, -scrollOffset);
            float top = NESTED_CONTENT_TOP;
            int count = settingsRowCount();
            for (int i = 0; i < count; i++) {
                drawSettingRow(canvas, i, top);
                top += ROW_STEP;
            }
            drawStartRow(canvas, top);
            top += ROW_STEP;
            canvas.restore();
            setScrollBounds(top);
            drawScrollIndicator(canvas);
            drawBackHeader(canvas, "MATCH SETTINGS", editing.mode.label.toUpperCase());
        }

        private void drawSettingRow(Canvas canvas, int index, float top) {
            String label = settingLabel(index);
            String value = settingValue(index);
            rounded(canvas, 32, top, 434, top + ROW_HEIGHT, 40, panel);
            circleButton(canvas, 76, top + 45, 28, settingIcon(index),
                    settingColor(index), Color.WHITE, settingIcon(index).length() > 1 ? 14 : 19);
            text(canvas, label.toUpperCase(), 120, top + 55,
                    label.length() > 9 ? 19 : 24,
                    Color.WHITE, Paint.Align.LEFT, true);
            if (isPickerSetting(index)) {
                text(canvas, value, 378, top + 56, value.length() > 8 ? 18 : 24,
                        green, Paint.Align.RIGHT, true);
                text(canvas, "›", 412, top + 59, 36,
                        muted, Paint.Align.CENTER, false);
            } else {
                rounded(canvas, 326, top + 25, 412, top + 65, 20,
                        isOnValue(value) ? green : panelLight);
                text(canvas, value, 369, top + 52, 18,
                        isOnValue(value) ? background : Color.WHITE,
                        Paint.Align.CENTER, true);
            }
        }

        private void drawPicker(Canvas canvas) {
            String[] values = pickerLabels();
            String selected = currentPickerValue();
            canvas.save();
            canvas.clipRect(0, NESTED_HEADER_BOTTOM, BASE, 456);
            canvas.translate(0, -scrollOffset);
            float top = NESTED_CONTENT_TOP;
            for (String value : values) {
                boolean active = value.equals(selected);
                rounded(canvas, 40, top, 426, top + PICKER_HEIGHT, 38,
                        active ? green : panel);
                text(canvas, value, 86, top + 54, value.length() > 10 ? 24 : 30,
                        active ? background : Color.WHITE, Paint.Align.LEFT, true);
                if (active) {
                    text(canvas, "✓", 386, top + 57, 28,
                            background, Paint.Align.CENTER, true);
                } else {
                    text(canvas, "›", 386, top + 58, 32,
                            muted, Paint.Align.CENTER, false);
                }
                top += PICKER_STEP;
            }
            canvas.restore();
            setScrollBounds(top);
            drawScrollIndicator(canvas);
            drawBackHeader(canvas, settingLabel(pickerSettingIndex).toUpperCase(),
                    "");
        }

        private void drawHomeHeader(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRect(0, 0, BASE, 78, paint);
            text(canvas, "PADEL SCORE", BASE / 2, 42, 28,
                    green, Paint.Align.CENTER, true);
            text(canvas, "CHOOSE MATCH FORMAT", BASE / 2, 67, 13,
                    muted, Paint.Align.CENTER, true);
        }

        private void drawBackHeader(Canvas canvas, String title, String subtitle) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRect(0, 0, BASE, NESTED_HEADER_BOTTOM, paint);
            circleButton(canvas, 92, 70, 22, "‹", panelLight, Color.WHITE, 28);
            text(canvas, title, 126, subtitle.isEmpty() ? 78 : 66,
                    subtitle.isEmpty() ? 23 : 20, Color.WHITE, Paint.Align.LEFT, true);
            if (!subtitle.isEmpty()) {
                text(canvas, subtitle, 126, 87, 12, green, Paint.Align.LEFT, true);
            }
        }

        private void drawSectionLabel(Canvas canvas, String label, float top) {
            text(canvas, label, 58, top + 17, 11, muted, Paint.Align.LEFT, true);
        }

        private void drawResumeRow(Canvas canvas, float top) {
            rounded(canvas, 32, top, 434, top + ROW_HEIGHT, 40, panelLight);
            circleButton(canvas, 76, top + 45, 28, "▶", green, background, 17);
            text(canvas, engine.state().completed ? "VIEW LAST MATCH" : "RESUME MATCH",
                    120, top + 55, 24, Color.WHITE, Paint.Align.LEFT, true);
            text(canvas, "›", 412, top + 59, 36, muted, Paint.Align.CENTER, false);
        }

        private void drawModeRow(Canvas canvas, Mode mode, float top) {
            rounded(canvas, 32, top, 434, top + ROW_HEIGHT, 40, panel);
            drawModeIcon(canvas, mode, 76, top + 45);
            text(canvas, shortMode(mode), 120, top + 55, 26,
                    Color.WHITE, Paint.Align.LEFT, true);
            text(canvas, "›", 412, top + 59, 36, muted, Paint.Align.CENTER, false);
        }

        private void drawModeIcon(Canvas canvas, Mode mode, float x, float y) {
            int color = modeColor(mode);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(x, y, 28, paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(3f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (mode == Mode.CLASSIC) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(x - 4, y - 4, 7, paint);
                canvas.drawLine(x + 1, y + 1, x + 9, y + 9, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x + 9, y - 8, 2.5f, paint);
            } else if (mode == Mode.AMERICANO) {
                canvas.drawCircle(x - 6, y - 6, 4, paint);
                canvas.drawCircle(x + 6, y - 6, 4, paint);
                canvas.drawCircle(x - 6, y + 6, 4, paint);
                canvas.drawCircle(x + 6, y + 6, 4, paint);
            } else if (mode == Mode.RACE_TO_N) {
                canvas.drawRect(x - 8, y - 10, x - 5, y + 11, paint);
                canvas.drawRect(x - 5, y - 10, x + 9, y - 1, paint);
            } else {
                String icon = mode == Mode.SINGLE_SET ? "1"
                        : mode == Mode.TIE_BREAK ? "7" : "10";
                text(canvas, icon, x, y + (icon.length() > 1 ? 5 : 7),
                        icon.length() > 1 ? 13 : 18, Color.WHITE, Paint.Align.CENTER, true);
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawStartRow(Canvas canvas, float top) {
            rounded(canvas, 32, top, 434, top + ROW_HEIGHT, 40, green);
            circleButton(canvas, 76, top + 45, 28, "▶", background,
                    green, 17);
            text(canvas, "START", 120, top + 55, 26,
                    background, Paint.Align.LEFT, true);
            text(canvas, "›", 412, top + 59, 36,
                    background, Paint.Align.CENTER, false);
        }

        private void drawScrollIndicator(Canvas canvas) {
            if (scrollMax <= 0) {
                return;
            }
            float start = screen == Screen.HOME ? 93 : 112;
            float position = start + (scrollOffset / scrollMax) * (425 - start);
            rounded(canvas, 450, position, 454, position + 28, 2,
                    Color.rgb(78, 104, 94));
        }

        private int modeColor(Mode mode) {
            if (mode == Mode.CLASSIC) return Color.rgb(0, 167, 204);
            if (mode == Mode.AMERICANO) return Color.rgb(238, 139, 43);
            if (mode == Mode.SINGLE_SET) return Color.rgb(44, 143, 213);
            if (mode == Mode.TIE_BREAK) return Color.rgb(121, 90, 203);
            if (mode == Mode.SUPER_TIE_BREAK) return Color.rgb(161, 76, 181);
            return Color.rgb(195, 145, 44);
        }

        private String settingIcon(int index) {
            if (editing.mode == Mode.CLASSIC) {
                return new String[]{"S", "G", "★", "S", "S", "A/B"}[index];
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return new String[]{"G", "★", "S", "S", "A/B"}[index];
            }
            if (editing.mode == Mode.AMERICANO) {
                return new String[]{"◎", "S", "↻", "A/B"}[index];
            }
            return new String[]{"◎", "+2", "A/B"}[index];
        }

        private int settingColor(int index) {
            int[] colors = {
                    Color.rgb(0, 167, 204),
                    Color.rgb(100, 82, 190),
                    Color.rgb(215, 137, 39),
                    Color.rgb(20, 148, 111),
                    Color.rgb(178, 70, 118)
            };
            return colors[index % colors.length];
        }

        private void drawMatch(Canvas canvas) {
            State state = engine.state();
            boolean flashing = scoreFlashTeam != null
                    && SystemClock.uptimeMillis() < scoreFlashUntil;
            int teamAFill = flashing && scoreFlashTeam == Team.A
                    ? Color.rgb(10, 92, 121) : Color.rgb(8, 34, 45);
            int teamBFill = flashing && scoreFlashTeam == Team.B
                    ? Color.rgb(126, 83, 20) : Color.rgb(43, 29, 10);
            rounded(canvas, 39, 18, 427, 176, 46, teamAFill);
            rounded(canvas, 39, 298, 427, 448, 46, teamBFill);

            text(canvas, scoreHint(state, Team.A, flashing), BASE / 2, 42,
                    15, flashing && scoreFlashTeam == Team.A ? Color.WHITE : muted,
                    Paint.Align.CENTER, true);
            text(canvas, engine.score(Team.A), BASE / 2, 126, 84,
                    Color.WHITE, Paint.Align.CENTER, true);
            drawTeamHeader(canvas, Team.A, 160, 138, 168);

            drawMatchInfoLine(canvas, state);

            smallButton(canvas, 28, 232, 151, 291, "UNDO",
                    engine.canUndo() ? panelLight : panel,
                    engine.canUndo() ? Color.WHITE : muted, 17);
            smallButton(canvas, 158, 232, 281, 291, "MENU", green, background, 17);
            if (state.mode == Mode.AMERICANO && state.completed) {
                smallButton(canvas, 288, 232, 438, 291, "NEXT", green, background, 17);
            } else {
                smallButton(canvas, 288, 232, 438, 291,
                        state.settings.trackServe ? "SERVE" : "HISTORY",
                        panelLight, Color.WHITE, 16);
            }

            drawTeamHeader(canvas, Team.B, 327, 306, 336);
            text(canvas, engine.score(Team.B), BASE / 2, 413, 84,
                    Color.WHITE, Paint.Align.CENTER, true);
            text(canvas, scoreHint(state, Team.B, flashing), BASE / 2, 440,
                    15, flashing && scoreFlashTeam == Team.B ? Color.WHITE : muted,
                    Paint.Align.CENTER, true);
            if (flashing) {
                postInvalidateOnAnimation();
            } else if (scoreFlashTeam != null) {
                scoreFlashTeam = null;
            }
        }

        private void drawTeamHeader(Canvas canvas, Team team, float baseline,
                                    float pillTop, float pillBottom) {
            int color = team == Team.A ? teamA : teamB;
            text(canvas, "TEAM " + team.name(), 78, baseline, 19,
                    color, Paint.Align.LEFT, true);
            if (engine.state().settings.trackServe && engine.state().currentServer == team) {
                rounded(canvas, 314, pillTop, 400, pillBottom, 15, color);
                text(canvas, "SERVE", 357, pillBottom - 8, 13,
                        background, Paint.Align.CENTER, true);
            }
        }

        private String scoreHint(State state, Team team, boolean flashing) {
            if (state.completed) {
                return "FINISHED";
            }
            if (flashing && scoreFlashTeam == team) {
                return "+1 ADDED";
            }
            return "TAP +1";
        }

        private void drawMatchInfoLine(Canvas canvas, State state) {
            String mode = compactMatchMode(state);
            String separator = "  ·  ";
            String status = compactMatchStatus(state);
            float size = 21f;
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(size);
            while (size > 19f && paint.measureText(mode + separator + status) > 366f) {
                size -= 1f;
                paint.setTextSize(size);
            }

            float modeWidth = paint.measureText(mode);
            float separatorWidth = paint.measureText(separator);
            float statusWidth = paint.measureText(status);
            float x = (BASE - modeWidth - separatorWidth - statusWidth) / 2f;
            int modeColor = state.completed ? muted : green;
            int statusColor = state.completed ? green : engine.isStarPoint()
                    ? teamB : Color.WHITE;
            text(canvas, mode, x, 211, size, modeColor, Paint.Align.LEFT, true);
            x += modeWidth;
            text(canvas, separator, x, 211, size, muted, Paint.Align.LEFT, true);
            x += separatorWidth;
            text(canvas, status, x, 211, size, statusColor, Paint.Align.LEFT, true);
        }

        private String compactMatchMode(State state) {
            if (state.mode == Mode.CLASSIC) return "CLASS";
            if (state.mode == Mode.SINGLE_SET) return "1SET";
            if (state.mode == Mode.TIE_BREAK) return "TB" + state.settings.target;
            if (state.mode == Mode.SUPER_TIE_BREAK) return "STB" + state.settings.target;
            if (state.mode == Mode.RACE_TO_N) return "RACE" + state.settings.target;
            return "AM" + state.settings.target;
        }

        private String compactMatchStatus(State state) {
            if (state.completed) {
                return "DRAW".equals(state.winner) ? "DRAW" : state.winner + " WINS";
            }
            if ((state.mode == Mode.CLASSIC || state.mode == Mode.SINGLE_SET)
                    && state.inTieBreak) {
                return "TB  ·  S" + state.setsA + ":" + state.setsB
                        + "  ·  G" + state.gamesA + ":" + state.gamesB;
            }
            if (state.mode == Mode.CLASSIC || state.mode == Mode.SINGLE_SET) {
                String gameStatus = engine.statusLabel();
                if (!"IN PLAY".equals(gameStatus)) {
                    return "STAR POINT".equals(gameStatus) ? "★ STAR" : gameStatus;
                }
            }
            if (state.mode == Mode.CLASSIC) {
                return "S" + state.setsA + ":" + state.setsB
                        + "  ·  G" + state.gamesA + ":" + state.gamesB;
            }
            if (state.mode == Mode.SINGLE_SET) {
                return "G" + state.gamesA + ":" + state.gamesB;
            }
            if (state.mode == Mode.AMERICANO) {
                int played = state.pointsA + state.pointsB;
                int remaining = Math.max(0, state.settings.target - played);
                return "R" + state.roundNumber + "  ·  " + remaining + " LEFT";
            }
            int remaining = Math.max(0,
                    state.settings.target - Math.max(state.pointsA, state.pointsB));
            return remaining + " LEFT";
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
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                recycleVelocityTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                downX = x;
                downY = y;
                lastY = y;
                dragging = false;
                gestureDirectionLocked = false;
                horizontalBackGesture = false;
                touching = true;
                touchX = x;
                touchY = y;
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                float distanceX = x - downX;
                float distanceY = y - downY;
                float absoluteX = Math.abs(distanceX);
                float absoluteY = Math.abs(distanceY);
                if (!gestureDirectionLocked
                        && Math.max(absoluteX, absoluteY) * scale > touchSlop) {
                    if (screen != Screen.HOME && downX <= 280
                            && distanceX > 0 && absoluteX > absoluteY * 1.25f) {
                        horizontalBackGesture = true;
                        gestureDirectionLocked = true;
                        touching = false;
                    } else if (absoluteY >= absoluteX) {
                        gestureDirectionLocked = true;
                        dragging = isScrollableScreen();
                        touching = false;
                    } else if (distanceX < 0 || downX > 280) {
                        gestureDirectionLocked = true;
                        touching = false;
                    }
                }
                if (dragging) {
                    scrollOffset = clampScroll(scrollOffset - (y - lastY));
                    invalidate();
                }
                lastY = y;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                touching = false;
                dragging = false;
                gestureDirectionLocked = false;
                horizontalBackGesture = false;
                recycleVelocityTracker();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                touching = false;
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                if (horizontalBackGesture) {
                    float distanceX = x - downX;
                    float distanceY = Math.abs(y - downY);
                    if (distanceX >= BACK_SWIPE_DISTANCE
                            && distanceX > distanceY * 1.25f) {
                        buzz(22);
                        handleBack();
                    }
                } else if (dragging && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
                    float velocityY = velocityTracker.getYVelocity();
                    if (Math.abs(velocityY) >= minimumFlingVelocity) {
                        scroller.fling(0, Math.round(scrollOffset), 0,
                                Math.round(-velocityY / scale), 0, 0,
                                0, Math.round(scrollMax));
                        postInvalidateOnAnimation();
                    }
                } else if (!gestureDirectionLocked) {
                    handleTap(x, y);
                }
                dragging = false;
                gestureDirectionLocked = false;
                horizontalBackGesture = false;
                recycleVelocityTracker();
                invalidate();
                return true;
            }
            return true;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_SCROLL && isScrollableScreen()) {
                float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
                if (delta == 0) {
                    delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                }
                if (delta != 0) {
                    scrollByControl(-delta * ROTARY_SCROLL_STEP);
                    return true;
                }
            }
            return super.onGenericMotionEvent(event);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (isScrollableScreen()) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        || keyCode == KeyEvent.KEYCODE_NAVIGATE_NEXT) {
                    scrollByControl(ROTARY_SCROLL_STEP);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                        || keyCode == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) {
                    scrollByControl(-ROTARY_SCROLL_STEP);
                    return true;
                }
            }
            return super.onKeyDown(keyCode, event);
        }

        private void scrollByControl(float distance) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            scrollOffset = clampScroll(scrollOffset + distance);
            invalidate();
        }

        @Override
        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                scrollOffset = clampScroll(scroller.getCurrY());
                postInvalidateOnAnimation();
            }
        }

        private void handleTap(float x, float y) {
            if (screen == Screen.HOME) {
                handleHomeTap(x, y);
            } else if (screen == Screen.SETTINGS) {
                handleSettingsTap(x, y);
            } else if (screen == Screen.PICKER) {
                handlePickerTap(x, y);
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
            if (y < 78) {
                return;
            }
            float contentY = y + scrollOffset;
            float top = 88;
            if (hasSavedMatch()) {
                if (inside(x, contentY, 32, top, 434, top + ROW_HEIGHT)) {
                    showScreen(Screen.MATCH);
                    keepAwake(true);
                    return;
                }
                top += ROW_STEP;
            }
            for (Mode mode : homeModes) {
                if (openModeIfTapped(x, contentY, mode, top)) return;
                top += ROW_STEP;
            }
        }

        private boolean openModeIfTapped(float x, float y, Mode mode, float top) {
            if (!inside(x, y, 32, top, 434, top + ROW_HEIGHT)) {
                return false;
            }
            editing = store.loadLastSettings(mode);
            showScreen(Screen.SETTINGS);
            return true;
        }

        private void handleSettingsTap(float x, float y) {
            if (inside(x, y, 58, 38, 126, 100)) {
                showScreen(Screen.HOME);
                return;
            }
            if (y < NESTED_HEADER_BOTTOM) {
                return;
            }
            float contentY = y + scrollOffset;
            float firstRow = NESTED_CONTENT_TOP;
            int count = settingsRowCount();
            int index = (int) ((contentY - firstRow) / ROW_STEP);
            float rowTop = firstRow + index * ROW_STEP;
            if (index >= 0 && index < count
                    && inside(x, contentY, 32, rowTop, 434, rowTop + ROW_HEIGHT)) {
                if (isPickerSetting(index)) {
                    settingsScrollRestore = scrollOffset;
                    pickerSettingIndex = index;
                    showScreen(Screen.PICKER);
                } else {
                    adjustSetting(index, 0);
                    buzz(14);
                }
                return;
            }
            float startTop = firstRow + count * ROW_STEP;
            if (inside(x, contentY, 32, startTop, 434, startTop + ROW_HEIGHT)) {
                store.saveLastSettings(editing);
                engine = new MatchEngine(editing.mode, editing);
                store.save(engine);
                showScreen(Screen.MATCH);
                keepAwake(true);
            }
        }

        private void handlePickerTap(float x, float y) {
            if (inside(x, y, 58, 38, 126, 100)) {
                returnToSettings();
                return;
            }
            if (y < NESTED_HEADER_BOTTOM) {
                return;
            }
            float contentY = y + scrollOffset;
            int index = (int) ((contentY - NESTED_CONTENT_TOP) / PICKER_STEP);
            float rowTop = NESTED_CONTENT_TOP + index * PICKER_STEP;
            String[] values = pickerLabels();
            if (index >= 0 && index < values.length
                    && inside(x, contentY, 40, rowTop, 426, rowTop + PICKER_HEIGHT)) {
                setPickerValue(values[index]);
                buzz(18);
                returnToSettings();
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
            if (inside(x, y, 28, 228, 151, 296)) {
                if (engine.undo()) {
                    store.save(engine);
                    keepAwake(true);
                    scoreFlashTeam = null;
                    buzz(18);
                }
                return;
            }
            if (inside(x, y, 158, 228, 281, 296)) {
                screen = Screen.MENU;
                keepAwake(false);
                return;
            }
            if (inside(x, y, 288, 228, 438, 296)) {
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
            boolean wasStarPoint = engine.isStarPoint();
            MatchEngine.Result result = engine.point(team);
            if (result.accepted) {
                store.save(engine);
                scoreFlashTeam = team;
                scoreFlashUntil = SystemClock.uptimeMillis() + SCORE_FLASH_MILLISECONDS;
                if (!wasStarPoint && engine.isStarPoint()) {
                    buzzStarPoint();
                } else {
                    buzz(result.completedNow ? 120 : 35);
                }
                keepAwake(true);
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
                keepAwake(true);
            } else if (index == 1) {
                engine.changeServer();
                store.save(engine);
                buzz(18);
                screen = Screen.MATCH;
                keepAwake(true);
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
                keepAwake(screen == Screen.MATCH);
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
                showScreen(Screen.HOME);
            } else if (screen == Screen.PICKER) {
                returnToSettings();
            } else if (screen == Screen.MATCH) {
                showScreen(hasSavedMatch() && !engine.state().completed
                        ? Screen.CONFIRM_EXIT : Screen.HOME);
                keepAwake(false);
            } else if (screen == Screen.MENU) {
                showScreen(Screen.MATCH);
                keepAwake(true);
            } else if (screen == Screen.HISTORY) {
                showScreen(returnFromHistory);
                keepAwake(screen == Screen.MATCH);
            } else if (screen == Screen.CONFIRM_CLEAR) {
                showScreen(Screen.HISTORY);
            } else {
                showScreen(Screen.MENU);
            }
            invalidate();
            return true;
        }

        private int settingsRowCount() {
            if (editing.mode == Mode.CLASSIC) {
                return 6;
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return 5;
            }
            if (editing.mode == Mode.AMERICANO) {
                return 4;
            }
            return 3;
        }

        private String settingLabel(int index) {
            if (editing.mode == Mode.CLASSIC) {
                return new String[]{"Sets", "Games", "Game scoring", "Set ending",
                        "Track serve", "First server"}[index];
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return new String[]{"Games", "Game scoring", "Set ending",
                        "Track serve", "First server"}[index];
            }
            if (editing.mode == Mode.AMERICANO) {
                return new String[]{"Points", "Track serve", "Serve every",
                        "First server"}[index];
            }
            return new String[]{"Target", "Win by 2", "First server"}[index];
        }

        private String settingValue(int index) {
            if (editing.mode == Mode.CLASSIC) {
                if (index == 0) return Integer.toString(editing.setsToWin);
                if (index == 1) return Integer.toString(editing.gamesPerSet);
                if (index == 2) return gameScoringLabel(editing.gameScoring);
                if (index == 3) return setEndingLabel(editing.setEnding);
                if (index == 4) return yesNo(editing.trackServe);
                return editing.startingServer.name();
            }
            if (editing.mode == Mode.SINGLE_SET) {
                if (index == 0) return Integer.toString(editing.gamesPerSet);
                if (index == 1) return gameScoringLabel(editing.gameScoring);
                if (index == 2) return setEndingLabel(editing.setEnding);
                if (index == 3) return yesNo(editing.trackServe);
                return editing.startingServer.name();
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

        private boolean isPickerSetting(int index) {
            return isNumericSetting(index)
                    || (editing.mode == Mode.CLASSIC && (index == 2 || index == 3))
                    || (editing.mode == Mode.SINGLE_SET && (index == 1 || index == 2));
        }

        private boolean isChoiceSetting(int index) {
            return (editing.mode == Mode.CLASSIC && (index == 2 || index == 3))
                    || (editing.mode == Mode.SINGLE_SET && (index == 1 || index == 2));
        }

        private int[] numericPickerValues() {
            if (editing.mode == Mode.CLASSIC && pickerSettingIndex == 0) {
                return new int[]{1, 2, 3};
            }
            if ((editing.mode == Mode.CLASSIC && pickerSettingIndex == 1)
                    || (editing.mode == Mode.SINGLE_SET && pickerSettingIndex == 0)) {
                return range(1, 12);
            }
            if (editing.mode == Mode.AMERICANO && pickerSettingIndex == 2) {
                return range(1, 16);
            }
            return new int[]{7, 10, 15, 21, 24, 32};
        }

        private String[] pickerLabels() {
            if (isChoiceSetting(pickerSettingIndex)) {
                boolean gameScoring = (editing.mode == Mode.CLASSIC && pickerSettingIndex == 2)
                        || (editing.mode == Mode.SINGLE_SET && pickerSettingIndex == 1);
                return gameScoring
                        ? new String[]{"STAR", "ADVANTAGE", "GOLDEN"}
                        : new String[]{"TB " + editing.gamesPerSet + ":" + editing.gamesPerSet,
                        "2-GAME LEAD", "FIRST TO " + editing.gamesPerSet};
            }
            int[] numeric = numericPickerValues();
            String[] labels = new String[numeric.length];
            for (int i = 0; i < numeric.length; i++) {
                labels[i] = Integer.toString(numeric[i]);
            }
            return labels;
        }

        private int currentNumericValue() {
            if (editing.mode == Mode.CLASSIC) {
                return pickerSettingIndex == 0 ? editing.setsToWin : editing.gamesPerSet;
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return editing.gamesPerSet;
            }
            if (editing.mode == Mode.AMERICANO && pickerSettingIndex == 2) {
                return editing.serveEvery;
            }
            return editing.target;
        }

        private String currentPickerValue() {
            if (isChoiceSetting(pickerSettingIndex)) {
                boolean gameScoring = (editing.mode == Mode.CLASSIC && pickerSettingIndex == 2)
                        || (editing.mode == Mode.SINGLE_SET && pickerSettingIndex == 1);
                return gameScoring
                        ? gameScoringLabel(editing.gameScoring)
                        : setEndingLabel(editing.setEnding);
            }
            return Integer.toString(currentNumericValue());
        }

        private void setPickerValue(String value) {
            if (!isChoiceSetting(pickerSettingIndex)) {
                setNumericSettingValue(Integer.parseInt(value));
                return;
            }
            boolean gameScoring = (editing.mode == Mode.CLASSIC && pickerSettingIndex == 2)
                    || (editing.mode == Mode.SINGLE_SET && pickerSettingIndex == 1);
            if (gameScoring) {
                editing.gameScoring = "STAR".equals(value) ? GameScoring.STAR
                        : "GOLDEN".equals(value) ? GameScoring.GOLDEN
                        : GameScoring.ADVANTAGE;
            } else {
                editing.setEnding = value.startsWith("TB ") ? SetEnding.TIE_BREAK
                        : value.startsWith("FIRST") ? SetEnding.FIRST_TO
                        : SetEnding.TWO_GAME_LEAD;
            }
        }

        private String gameScoringLabel(GameScoring value) {
            return value == GameScoring.STAR ? "STAR"
                    : value == GameScoring.GOLDEN ? "GOLDEN" : "ADVANTAGE";
        }

        private String setEndingLabel(SetEnding value) {
            if (value == SetEnding.TIE_BREAK) {
                return "TB " + editing.gamesPerSet + ":" + editing.gamesPerSet;
            }
            return value == SetEnding.FIRST_TO
                    ? "FIRST TO " + editing.gamesPerSet : "2-GAME LEAD";
        }

        private void setNumericSettingValue(int value) {
            if (editing.mode == Mode.CLASSIC) {
                if (pickerSettingIndex == 0) editing.setsToWin = value;
                else editing.gamesPerSet = value;
            } else if (editing.mode == Mode.SINGLE_SET) {
                editing.gamesPerSet = value;
            } else if (editing.mode == Mode.AMERICANO && pickerSettingIndex == 2) {
                editing.serveEvery = value;
            } else {
                editing.target = value;
            }
        }

        private int[] range(int start, int end) {
            int[] values = new int[end - start + 1];
            for (int i = 0; i < values.length; i++) {
                values[i] = start + i;
            }
            return values;
        }

        private void adjustSetting(int index, int direction) {
            if (!isNumericSetting(index)) {
                if (editing.mode == Mode.CLASSIC) {
                    if (index == 4) editing.trackServe = !editing.trackServe;
                    else if (index == 5) editing.startingServer = editing.startingServer.other();
                } else if (editing.mode == Mode.SINGLE_SET) {
                    if (index == 3) editing.trackServe = !editing.trackServe;
                    else if (index == 4) editing.startingServer = editing.startingServer.other();
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

        private void showScreen(Screen target) {
            screen = target;
            resetScroll();
        }

        private void returnToSettings() {
            screen = Screen.SETTINGS;
            scroller.abortAnimation();
            scrollOffset = settingsScrollRestore;
            scrollMax = Math.max(scrollMax, scrollOffset);
            pickerSettingIndex = -1;
            invalidate();
        }

        private void resetScroll() {
            scroller.abortAnimation();
            scrollOffset = 0;
            scrollMax = 0;
            dragging = false;
            gestureDirectionLocked = false;
            horizontalBackGesture = false;
        }

        private void setScrollBounds(float contentBottom) {
            scrollMax = Math.max(0, contentBottom + BOTTOM_SCROLL_SPACE - 448f);
            scrollOffset = clampScroll(scrollOffset);
        }

        private float clampScroll(float value) {
            return Math.max(0, Math.min(scrollMax, value));
        }

        private boolean isScrollableScreen() {
            return screen == Screen.HOME || screen == Screen.SETTINGS
                    || screen == Screen.PICKER;
        }

        private void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
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
