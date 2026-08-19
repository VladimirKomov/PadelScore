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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.OverScroller;

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
        private float downY;
        private float lastY;
        private float settingsScrollRestore;
        private int pickerSettingIndex = -1;
        private boolean dragging;
        private boolean touching;
        private float touchX;
        private float touchY;

        ScoreView() {
            super(MainActivity.this);
            setBackgroundColor(background);
            setFocusable(true);
            scroller = new OverScroller(MainActivity.this);
            ViewConfiguration configuration = ViewConfiguration.get(MainActivity.this);
            touchSlop = configuration.getScaledTouchSlop();
            minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
            maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
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

        private void drawHome(Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, 78, BASE, 456);
            canvas.translate(0, -scrollOffset);
            float top = 86;
            if (hasSavedMatch()) {
                drawSectionLabel(canvas, "CURRENT MATCH", top);
                top += 27;
                drawResumeRow(canvas, top);
                top += 82;
            }
            drawSectionLabel(canvas, "POPULAR", top);
            top += 27;
            drawModeRow(canvas, Mode.CLASSIC, top);
            top += 80;
            drawModeRow(canvas, Mode.AMERICANO, top);
            top += 88;
            drawSectionLabel(canvas, "OTHER MODES", top);
            top += 27;
            for (int i = 2; i < homeModes.length; i++) {
                drawModeRow(canvas, homeModes[i], top);
                top += 80;
            }
            canvas.restore();
            setScrollBounds(top + 8);
            drawScrollIndicator(canvas);
            drawHomeHeader(canvas);
        }

        private void drawSettings(Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, 78, BASE, 456);
            canvas.translate(0, -scrollOffset);
            float top = 87;
            drawSectionLabel(canvas, "MATCH RULES", top);
            top += 27;
            int count = settingsRowCount();
            for (int i = 0; i < count; i++) {
                drawSettingRow(canvas, i, top);
                top += 80;
            }
            drawStartRow(canvas, top);
            top += 80;
            canvas.restore();
            setScrollBounds(top + 8);
            drawScrollIndicator(canvas);
            drawBackHeader(canvas, "MATCH SETTINGS", editing.mode.label.toUpperCase());
        }

        private void drawSettingRow(Canvas canvas, int index, float top) {
            String label = settingLabel(index);
            String value = settingValue(index);
            rounded(canvas, 40, top, 426, top + 70, 31, panel);
            circleButton(canvas, 78, top + 35, 23, settingIcon(index),
                    settingColor(index), Color.WHITE, settingIcon(index).length() > 1 ? 12 : 16);
            text(canvas, label.toUpperCase(), 112, top + 30, 15,
                    Color.WHITE, Paint.Align.LEFT, true);
            if (isNumericSetting(index)) {
                text(canvas, "SELECT VALUE", 112, top + 51, 10,
                        muted, Paint.Align.LEFT, true);
                text(canvas, value, 371, top + 43, 22,
                        green, Paint.Align.RIGHT, true);
                text(canvas, "›", 407, top + 47, 31,
                        muted, Paint.Align.CENTER, false);
            } else {
                text(canvas, "TAP TO CHANGE", 112, top + 51, 10,
                        muted, Paint.Align.LEFT, true);
                rounded(canvas, 326, top + 18, 408, top + 52, 17,
                        isOnValue(value) ? green : panelLight);
                text(canvas, value, 367, top + 41, 13,
                        isOnValue(value) ? background : Color.WHITE,
                        Paint.Align.CENTER, true);
            }
        }

        private void drawPicker(Canvas canvas) {
            int[] values = pickerValues();
            int selected = currentNumericValue();
            canvas.save();
            canvas.clipRect(0, 78, BASE, 456);
            canvas.translate(0, -scrollOffset);
            float top = 88;
            for (int value : values) {
                boolean active = value == selected;
                rounded(canvas, 52, top, 414, top + 64, 28,
                        active ? green : panel);
                text(canvas, Integer.toString(value), 89, top + 42, 24,
                        active ? background : Color.WHITE, Paint.Align.LEFT, true);
                if (active) {
                    text(canvas, "SELECTED", 379, top + 39, 12,
                            background, Paint.Align.RIGHT, true);
                } else {
                    text(canvas, "›", 386, top + 43, 27,
                            muted, Paint.Align.CENTER, false);
                }
                top += 72;
            }
            canvas.restore();
            setScrollBounds(top + 8);
            drawScrollIndicator(canvas);
            drawBackHeader(canvas, settingLabel(pickerSettingIndex).toUpperCase(),
                    "SELECT VALUE");
        }

        private void drawHomeHeader(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRect(0, 0, BASE, 78, paint);
            text(canvas, "PADEL SCORE", BASE / 2, 42, 26,
                    green, Paint.Align.CENTER, true);
            text(canvas, "CHOOSE MATCH FORMAT", BASE / 2, 67, 12,
                    muted, Paint.Align.CENTER, true);
        }

        private void drawBackHeader(Canvas canvas, String title, String subtitle) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRect(0, 0, BASE, 78, paint);
            circleButton(canvas, 54, 40, 25, "‹", panelLight, Color.WHITE, 30);
            text(canvas, title, 94, 36, 18, Color.WHITE, Paint.Align.LEFT, true);
            text(canvas, subtitle, 94, 57, 11, green, Paint.Align.LEFT, true);
        }

        private void drawSectionLabel(Canvas canvas, String label, float top) {
            text(canvas, label, 58, top + 17, 11, muted, Paint.Align.LEFT, true);
        }

        private void drawResumeRow(Canvas canvas, float top) {
            rounded(canvas, 40, top, 426, top + 70, 31, panelLight);
            circleButton(canvas, 78, top + 35, 23, "▶", green, background, 14);
            text(canvas, engine.state().completed ? "VIEW LAST MATCH" : "RESUME MATCH",
                    112, top + 30, 16, Color.WHITE, Paint.Align.LEFT, true);
            text(canvas, engine.modeLabel().toUpperCase(), 112, top + 51, 10,
                    muted, Paint.Align.LEFT, true);
            text(canvas, "›", 407, top + 47, 31, muted, Paint.Align.CENTER, false);
        }

        private void drawModeRow(Canvas canvas, Mode mode, float top) {
            rounded(canvas, 40, top, 426, top + 70, 31, panel);
            drawModeIcon(canvas, mode, 78, top + 35);
            text(canvas, shortMode(mode), 112, top + 30, 16,
                    Color.WHITE, Paint.Align.LEFT, true);
            text(canvas, modeHint(mode), 112, top + 51, 10,
                    muted, Paint.Align.LEFT, true);
            text(canvas, "›", 407, top + 47, 31, muted, Paint.Align.CENTER, false);
        }

        private void drawModeIcon(Canvas canvas, Mode mode, float x, float y) {
            int color = modeColor(mode);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(x, y, 23, paint);
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
            rounded(canvas, 40, top, 426, top + 70, 31, green);
            circleButton(canvas, 78, top + 35, 23, "▶", background,
                    green, 14);
            text(canvas, "START MATCH", 112, top + 41, 18,
                    background, Paint.Align.LEFT, true);
            text(canvas, "›", 407, top + 47, 31,
                    background, Paint.Align.CENTER, false);
        }

        private void drawScrollIndicator(Canvas canvas) {
            if (scrollMax <= 0) {
                return;
            }
            float position = 93 + (scrollOffset / scrollMax) * 332;
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

        private String modeHint(Mode mode) {
            if (mode == Mode.CLASSIC) return "SETS AND GAMES";
            if (mode == Mode.AMERICANO) return "FIXED TOTAL POINTS";
            if (mode == Mode.SINGLE_SET) return "ONE SET";
            if (mode == Mode.TIE_BREAK) return "FIRST TO 7";
            if (mode == Mode.SUPER_TIE_BREAK) return "FIRST TO 10";
            return "CUSTOM TARGET";
        }

        private String settingIcon(int index) {
            if (editing.mode == Mode.CLASSIC) {
                return new String[]{"S", "G", "★", "+2", "7"}[index];
            }
            if (editing.mode == Mode.SINGLE_SET) {
                return new String[]{"G", "★", "+2", "7"}[index];
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
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                recycleVelocityTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                downY = y;
                lastY = y;
                dragging = false;
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
                if (isScrollableScreen()) {
                    if (!dragging && Math.abs(y - downY) * scale > touchSlop) {
                        dragging = true;
                        touching = false;
                    }
                    if (dragging) {
                        scrollOffset = clampScroll(scrollOffset - (y - lastY));
                        invalidate();
                    }
                }
                lastY = y;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                touching = false;
                dragging = false;
                recycleVelocityTracker();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                touching = false;
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                if (dragging && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
                    float velocityY = velocityTracker.getYVelocity();
                    if (Math.abs(velocityY) >= minimumFlingVelocity) {
                        scroller.fling(0, Math.round(scrollOffset), 0,
                                Math.round(-velocityY / scale), 0, 0,
                                0, Math.round(scrollMax));
                        postInvalidateOnAnimation();
                    }
                } else {
                    handleTap(x, y);
                }
                dragging = false;
                recycleVelocityTracker();
                invalidate();
                return true;
            }
            return true;
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
            float top = 86;
            if (hasSavedMatch()) {
                top += 27;
                if (inside(x, contentY, 40, top, 426, top + 70)) {
                    showScreen(Screen.MATCH);
                    keepAwake(!engine.state().completed);
                    return;
                }
                top += 82;
            }
            top += 27;
            if (openModeIfTapped(x, contentY, Mode.CLASSIC, top)) return;
            top += 80;
            if (openModeIfTapped(x, contentY, Mode.AMERICANO, top)) return;
            top += 88 + 27;
            for (int i = 2; i < homeModes.length; i++) {
                if (openModeIfTapped(x, contentY, homeModes[i], top)) return;
                top += 80;
            }
        }

        private boolean openModeIfTapped(float x, float y, Mode mode, float top) {
            if (!inside(x, y, 40, top, 426, top + 70)) {
                return false;
            }
            editing = store.loadLastSettings(mode);
            showScreen(Screen.SETTINGS);
            return true;
        }

        private void handleSettingsTap(float x, float y) {
            if (inside(x, y, 24, 8, 96, 76)) {
                showScreen(Screen.HOME);
                return;
            }
            if (y < 78) {
                return;
            }
            float contentY = y + scrollOffset;
            float firstRow = 114;
            int count = settingsRowCount();
            int index = (int) ((contentY - firstRow) / 80);
            float rowTop = firstRow + index * 80;
            if (index >= 0 && index < count
                    && inside(x, contentY, 40, rowTop, 426, rowTop + 70)) {
                if (isNumericSetting(index)) {
                    settingsScrollRestore = scrollOffset;
                    pickerSettingIndex = index;
                    showScreen(Screen.PICKER);
                } else {
                    adjustSetting(index, 0);
                    buzz(14);
                }
                return;
            }
            float startTop = firstRow + count * 80;
            if (inside(x, contentY, 40, startTop, 426, startTop + 70)) {
                store.saveLastSettings(editing);
                engine = new MatchEngine(editing.mode, editing);
                store.save(engine);
                showScreen(Screen.MATCH);
                keepAwake(!engine.state().completed);
            }
        }

        private void handlePickerTap(float x, float y) {
            if (inside(x, y, 24, 8, 96, 76)) {
                returnToSettings();
                return;
            }
            if (y < 78) {
                return;
            }
            float contentY = y + scrollOffset;
            int index = (int) ((contentY - 88) / 72);
            float rowTop = 88 + index * 72;
            int[] values = pickerValues();
            if (index >= 0 && index < values.length
                    && inside(x, contentY, 52, rowTop, 414, rowTop + 64)) {
                setNumericSettingValue(values[index]);
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
                showScreen(Screen.HOME);
            } else if (screen == Screen.PICKER) {
                returnToSettings();
            } else if (screen == Screen.MATCH) {
                showScreen(hasSavedMatch() && !engine.state().completed
                        ? Screen.CONFIRM_EXIT : Screen.HOME);
                keepAwake(false);
            } else if (screen == Screen.MENU) {
                showScreen(Screen.MATCH);
                keepAwake(!engine.state().completed);
            } else if (screen == Screen.HISTORY) {
                showScreen(returnFromHistory);
                keepAwake(screen == Screen.MATCH && !engine.state().completed);
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

        private int[] pickerValues() {
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
        }

        private void setScrollBounds(float contentBottom) {
            scrollMax = Math.max(0, contentBottom - 448f);
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
