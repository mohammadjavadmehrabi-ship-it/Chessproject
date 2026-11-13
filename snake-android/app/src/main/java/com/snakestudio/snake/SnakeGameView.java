package com.snakestudio.snake;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;
import java.util.Random;

/**
 * High-FPS hardware-accelerated native Canvas view for the Snake game.
 * Features smooth sub-cell interpolation, 3 themes, Persian/English UI,
 * swipe + optional on-screen D-Pad controls, particles, floating score popups,
 * screen shake, and persistent per-mode high scores.
 */
public final class SnakeGameView extends View implements GameEngine.Listener {

    private static final String PREFS_NAME = "snake_studio_prefs";
    private static final int MAX_PARTICLES = 56;
    private static final int MAX_FLOATS = 12;

    private static final class Particle {
        boolean active;
        float x, y, vx, vy, radius, life, maxLife;
        int color;
    }

    private static final class FloatingText {
        boolean active;
        float x, y, vy, life, maxLife;
        String text;
        int color;
        boolean big;
    }

    private final GameEngine engine;
    private final SoundEngine sound;
    private final SharedPreferences prefs;
    private final Random fxRng = new Random();
    private final float density;

    private ThemePalette palette = ThemePalette.NEON;
    private boolean persian = true;
    private boolean showDpad = true;

    // Insets from MainActivity
    private int insetTop = 0;
    private int insetBottom = 0;
    private int insetLeft = 0;
    private int insetRight = 0;

    // Layout geometry
    private int viewW = 0;
    private int viewH = 0;
    private float boardLeft, boardTop, boardW, boardH, cellSize;
    private final RectF boardRect = new RectF();
    private final RectF boardFrameRect = new RectF();
    private final RectF scoreBadgeRect = new RectF();
    private final RectF bestBadgeRect = new RectF();
    private final RectF btnPauseRect = new RectF();
    private final RectF btnSoundRect = new RectF();
    private final RectF[] dpadRects = new RectF[]{new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF modalRect = new RectF();

    // Menu / Pause / GameOver hit rects
    private final RectF[] btnModeRects = new RectF[]{new RectF(), new RectF(), new RectF()};
    private final RectF[] btnSpeedRects = new RectF[]{new RectF(), new RectF(), new RectF()};
    private final RectF[] btnThemeRects = new RectF[]{new RectF(), new RectF(), new RectF()};
    private final RectF btnDpadToggleRect = new RectF();
    private final RectF btnLangToggleRect = new RectF();
    private final RectF btnPrimaryRect = new RectF();
    private final RectF btnSecondaryRect = new RectF();
    private final RectF btnTertiaryRect = new RectF();
    private final RectF tempRect = new RectF();
    private final RectF statBoxRect = new RectF();

    // Pre-allocated Paints & Paths (zero GC in render loop)
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snakePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint portalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private final DashPathEffect portalDash;

    // Pre-allocated interpolation buffers
    private final float[] interpX = new float[GameEngine.COLS * GameEngine.ROWS + 4];
    private final float[] interpY = new float[GameEngine.COLS * GameEngine.ROWS + 4];

    // FX pools
    private final Particle[] particles = new Particle[MAX_PARTICLES];
    private final FloatingText[] floats = new FloatingText[MAX_FLOATS];

    // Timing & visual animations
    private long lastFrameNanos = 0L;
    private float animTime = 0f;
    private float shakeTime = 0f;
    private float scorePop = 0f;
    private float overlayAlpha = 1f;
    private int pressedDpadDir = -1;
    private int activeDpadPointer = -1;

    // Swipe tracking
    private int swipePointerId = -1;
    private float swipeStartX = 0f;
    private float swipeStartY = 0f;
    private float minSwipePx;

    public interface ThemeChangeCallback {
        void onThemeChanged(int bgOuterColor);
    }

    private ThemeChangeCallback themeCallback;

    public SnakeGameView(Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.minSwipePx = dp(22);

        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.engine = new GameEngine();
        this.sound = new SoundEngine();

        for (int i = 0; i < MAX_PARTICLES; i++) particles[i] = new Particle();
        for (int i = 0; i < MAX_FLOATS; i++) floats[i] = new FloatingText();

        strokePaint.setStyle(Paint.Style.STROKE);
        snakePaint.setStyle(Paint.Style.STROKE);
        snakePaint.setStrokeCap(Paint.Cap.ROUND);
        snakePaint.setStrokeJoin(Paint.Join.ROUND);

        portalPaint.setStyle(Paint.Style.STROKE);
        portalPaint.setStrokeWidth(dp(2.5f));
        portalDash = new DashPathEffect(new float[]{dp(10), dp(7)}, 0f);
        portalPaint.setPathEffect(portalDash);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        setFocusable(true);
        setFocusableInTouchMode(true);
        setHapticFeedbackEnabled(true);

        loadPrefs();
        engine.setListener(this);
    }

    public void setThemeChangeCallback(ThemeChangeCallback cb) {
        this.themeCallback = cb;
        if (cb != null) {
            cb.onThemeChanged(palette.bgOuter);
        }
    }

    public int getCurrentBgColor() {
        return palette.bgOuter;
    }

    public void setSafeInsets(int left, int top, int right, int bottom) {
        if (this.insetLeft != left || this.insetTop != top
                || this.insetRight != right || this.insetBottom != bottom) {
            this.insetLeft = left;
            this.insetTop = top;
            this.insetRight = right;
            this.insetBottom = bottom;
            if (viewW > 0 && viewH > 0) {
                computeLayout(viewW, viewH);
                invalidate();
            }
        }
    }

    public void onHostPause() {
        lastFrameNanos = 0L;
        if (engine.getState() == GameEngine.STATE_PLAYING) {
            engine.pause();
            overlayAlpha = 0f;
        }
        savePrefs();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lastFrameNanos = 0L;
    }

    public void onHostDestroy() {
        savePrefs();
        sound.release();
    }

    /**
     * Handles system Back button cleanly. Returns true if consumed.
     */
    public boolean handleBackPressed() {
        int st = engine.getState();
        if (st == GameEngine.STATE_PLAYING) {
            engine.pause();
            overlayAlpha = 0f;
            sound.play(SoundEngine.SND_BUTTON);
            return true;
        } else if (st == GameEngine.STATE_PAUSED || st == GameEngine.STATE_GAME_OVER) {
            engine.goToMenu();
            overlayAlpha = 0f;
            sound.play(SoundEngine.SND_BUTTON);
            return true;
        }
        return false;
    }

    private void loadPrefs() {
        engine.setBestScore(GameEngine.MODE_CLASSIC, prefs.getInt("best_0", 0));
        engine.setBestScore(GameEngine.MODE_PORTAL, prefs.getInt("best_1", 0));
        engine.setBestScore(GameEngine.MODE_OBSTACLES, prefs.getInt("best_2", 0));
        engine.setMode(prefs.getInt("mode", GameEngine.MODE_CLASSIC));
        engine.setSpeedLevel(prefs.getInt("speed", GameEngine.SPEED_NORMAL));
        palette = ThemePalette.byId(prefs.getInt("theme", ThemePalette.THEME_NEON));
        sound.setMuted(prefs.getBoolean("muted", false));
        showDpad = prefs.getBoolean("dpad", true);
        persian = prefs.getBoolean("persian", true);
    }

    private void savePrefs() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt("best_0", engine.getBestScore(GameEngine.MODE_CLASSIC));
        ed.putInt("best_1", engine.getBestScore(GameEngine.MODE_PORTAL));
        ed.putInt("best_2", engine.getBestScore(GameEngine.MODE_OBSTACLES));
        ed.putInt("mode", engine.getMode());
        ed.putInt("speed", engine.getSpeedLevel());
        ed.putInt("theme", palette.id);
        ed.putBoolean("muted", sound.isMuted());
        ed.putBoolean("dpad", showDpad);
        ed.putBoolean("persian", persian);
        ed.apply();
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w;
        viewH = h;
        computeLayout(w, h);
    }

    private void computeLayout(int w, int h) {
        if (w <= 0 || h <= 0) return;

        float safeL = insetLeft + dp(12);
        float safeR = w - insetRight - dp(12);
        float safeT = insetTop + dp(8);
        float safeB = h - insetBottom - dp(10);
        float usableW = Math.max(dp(120), safeR - safeL);
        float usableH = Math.max(dp(200), safeB - safeT);

        // 1. Top HUD Bar
        float hudH = Math.min(dp(54), usableH * 0.082f);
        float hudTop = safeT;
        float hudBot = hudTop + hudH;

        float iconBtnSize = hudH * 0.88f;
        float gap = dp(8);

        // Rightmost buttons: Pause & Sound
        btnPauseRect.set(safeR - iconBtnSize, hudTop + (hudH - iconBtnSize) * 0.5f,
                safeR, hudTop + (hudH + iconBtnSize) * 0.5f);
        btnSoundRect.set(btnPauseRect.left - gap - iconBtnSize, btnPauseRect.top,
                btnPauseRect.left - gap, btnPauseRect.bottom);

        // Score & Best badges share remaining left space
        float badgesRight = btnSoundRect.left - gap;
        float badgesTotalW = Math.max(dp(100), badgesRight - safeL);
        float badgeW = (badgesTotalW - gap) * 0.5f;
        scoreBadgeRect.set(safeL, hudTop, safeL + badgeW, hudBot);
        bestBadgeRect.set(scoreBadgeRect.right + gap, hudTop, badgesRight, hudBot);

        // 2. Bottom D-Pad zone (if enabled)
        float playTop = hudBot + dp(10);
        float playBot;
        if (showDpad) {
            float dpadZoneH = Math.min(dp(164), usableH * 0.245f);
            float dpadTop = safeB - dpadZoneH;
            playBot = dpadTop - dp(10);
            computeDpadLayout(w * 0.5f, dpadTop + dpadZoneH * 0.5f, dpadZoneH);
        } else {
            playBot = safeB - dp(24);
            for (int i = 0; i < 4; i++) dpadRects[i].setEmpty();
        }

        // 3. Game Board centered in available region
        float availW = usableW - dp(8);
        float availH = Math.max(dp(120), playBot - playTop - dp(8));
        cellSize = (float) Math.floor(Math.min(availW / GameEngine.COLS, availH / GameEngine.ROWS));
        if (cellSize < 4f) cellSize = 4f;
        boardW = cellSize * GameEngine.COLS;
        boardH = cellSize * GameEngine.ROWS;
        boardLeft = (w - boardW) * 0.5f;
        boardTop = playTop + (playBot - playTop - boardH) * 0.5f;
        boardRect.set(boardLeft, boardTop, boardLeft + boardW, boardTop + boardH);

        float framePad = dp(5);
        boardFrameRect.set(boardLeft - framePad, boardTop - framePad,
                boardRect.right + framePad, boardRect.bottom + framePad);

        // 4. Overlay modal bounds
        float modalW = Math.min(usableW - dp(8), dp(356));
        float modalMaxH = Math.min(usableH - dp(16), dp(530));
        float modalL = (w - modalW) * 0.5f;
        float modalT = safeT + (usableH - modalMaxH) * 0.5f;
        modalRect.set(modalL, modalT, modalL + modalW, modalT + modalMaxH);
    }

    private void computeDpadLayout(float cx, float cy, float zoneH) {
        float btnW = Math.min(dp(64), zoneH * 0.40f);
        float btnH = Math.min(dp(50), zoneH * 0.31f);
        float sepX = btnW * 0.96f;
        float sepY = btnH * 1.02f;

        // UP
        dpadRects[GameEngine.DIR_UP].set(cx - btnW * 0.5f, cy - sepY - btnH * 0.5f,
                cx + btnW * 0.5f, cy - sepY + btnH * 0.5f);
        // DOWN
        dpadRects[GameEngine.DIR_DOWN].set(cx - btnW * 0.5f, cy + sepY - btnH * 0.5f,
                cx + btnW * 0.5f, cy + sepY + btnH * 0.5f);
        // LEFT
        dpadRects[GameEngine.DIR_LEFT].set(cx - sepX - btnW * 0.5f, cy - btnH * 0.5f,
                cx - sepX + btnW * 0.5f, cy + btnH * 0.5f);
        // RIGHT
        dpadRects[GameEngine.DIR_RIGHT].set(cx + sepX - btnW * 0.5f, cy - btnH * 0.5f,
                cx + sepX + btnW * 0.5f, cy + btnH * 0.5f);
    }

    // --- GameEngine.Listener callbacks ---

    @Override
    public void onEatFood(int cellX, int cellY, int pointsAwarded, int comboCount) {
        sound.play(SoundEngine.SND_EAT);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        scorePop = 1f;
        float px = boardLeft + (cellX + 0.5f) * cellSize;
        float py = boardTop + (cellY + 0.5f) * cellSize;
        spawnParticles(px, py, 14, palette.foodBody, palette.snakeHead);
        String label = "+" + Strings.num(pointsAwarded, persian);
        spawnFloatingText(px, py - cellSize * 0.4f, label, palette.textPrimary, false);
        if (comboCount >= 2) {
            spawnFloatingText(px, py - cellSize * 1.35f,
                    Strings.comboTag(comboCount, persian), palette.bonusBody, true);
        }
        savePrefs();
    }

    @Override
    public void onEatBonus(int cellX, int cellY, int pointsAwarded) {
        sound.play(SoundEngine.SND_BONUS);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        scorePop = 1.25f;
        float px = boardLeft + (cellX + 0.5f) * cellSize;
        float py = boardTop + (cellY + 0.5f) * cellSize;
        spawnParticles(px, py, 24, palette.bonusBody, 0xFFFFFFFF);
        spawnFloatingText(px, py - cellSize * 0.5f,
                "+" + Strings.num(pointsAwarded, persian) + " ★", palette.bonusBody, true);
        savePrefs();
    }

    @Override
    public void onTurn() {
        sound.play(SoundEngine.SND_TURN);
    }

    @Override
    public void onGameOver(boolean won, boolean newBest) {
        if (won || newBest) {
            sound.play(SoundEngine.SND_NEW_RECORD);
        } else {
            sound.play(SoundEngine.SND_GAME_OVER);
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        shakeTime = won ? 0f : 0.30f;
        overlayAlpha = 0f;
        List<GameEngine.Cell> sn = engine.getSnake();
        if (!sn.isEmpty()) {
            GameEngine.Cell head = sn.get(0);
            float px = boardLeft + (head.x + 0.5f) * cellSize;
            float py = boardTop + (head.y + 0.5f) * cellSize;
            spawnParticles(px, py, 26, won ? palette.bonusBody : palette.foodBody, palette.snakeHead);
        }
        savePrefs();
    }

    private void spawnParticles(float x, float y, int count, int colA, int colB) {
        int spawned = 0;
        for (int i = 0; i < MAX_PARTICLES && spawned < count; i++) {
            Particle p = particles[i];
            if (!p.active) {
                p.active = true;
                p.x = x;
                p.y = y;
                double ang = fxRng.nextDouble() * Math.PI * 2.0;
                float spd = dp(45) + fxRng.nextFloat() * dp(120);
                p.vx = (float) Math.cos(ang) * spd;
                p.vy = (float) Math.sin(ang) * spd;
                p.radius = dp(2.2f) + fxRng.nextFloat() * dp(3.0f);
                p.maxLife = 0.35f + fxRng.nextFloat() * 0.30f;
                p.life = p.maxLife;
                p.color = (spawned % 2 == 0) ? colA : colB;
                spawned++;
            }
        }
    }

    private void spawnFloatingText(float x, float y, String text, int color, boolean big) {
        for (int i = 0; i < MAX_FLOATS; i++) {
            FloatingText ft = floats[i];
            if (!ft.active) {
                ft.active = true;
                ft.x = Math.max(boardLeft + dp(34), Math.min(boardRect.right - dp(34), x));
                ft.y = Math.max(boardTop + dp(20), y);
                ft.vy = -dp(42);
                ft.maxLife = 0.85f;
                ft.life = ft.maxLife;
                ft.text = text;
                ft.color = color;
                ft.big = big;
                return;
            }
        }
    }

    // --- Main Loop & Rendering ---

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (viewW <= 0 || viewH <= 0) return;

        long now = SystemClock.elapsedRealtimeNanos();
        float dt;
        if (lastFrameNanos == 0L) {
            dt = 1f / 60f;
        } else {
            dt = (now - lastFrameNanos) / 1_000_000_000f;
            if (dt < 0f) dt = 0f;
            if (dt > 0.05f) dt = 0.05f;
        }
        lastFrameNanos = now;
        animTime += dt;

        // Advance simulation & effects
        engine.update(dt);
        updateEffects(dt);

        // Background
        canvas.drawColor(palette.bgOuter);

        // Top HUD
        drawTopHud(canvas);

        // Board with optional impact shake
        canvas.save();
        if (shakeTime > 0f) {
            float mag = dp(7) * (shakeTime / 0.30f);
            float sx = (fxRng.nextFloat() * 2f - 1f) * mag;
            float sy = (fxRng.nextFloat() * 2f - 1f) * mag;
            canvas.translate(sx, sy);
        }
        drawBoard(canvas);
        drawObstacles(canvas);
        drawFood(canvas);
        drawBonusFood(canvas);
        drawSnake(canvas);
        drawParticles(canvas);
        drawFloatingTexts(canvas);
        canvas.restore();

        // Bottom Controls / Hint
        if (showDpad) {
            drawDpad(canvas);
        } else if (engine.getState() == GameEngine.STATE_PLAYING) {
            textPaint.setColor(withAlpha(palette.textSecondary, 165));
            textPaint.setTextSize(dp(12.5f));
            canvas.drawText(Strings.swipeHint(persian), viewW * 0.5f,
                    boardFrameRect.bottom + dp(20), textPaint);
        }

        // Overlays
        int st = engine.getState();
        if (st == GameEngine.STATE_MENU) {
            drawMenuOverlay(canvas);
        } else if (st == GameEngine.STATE_PAUSED) {
            drawPauseOverlay(canvas);
        } else if (st == GameEngine.STATE_GAME_OVER) {
            drawGameOverOverlay(canvas);
        }

        postInvalidateOnAnimation();
    }

    private void updateEffects(float dt) {
        if (shakeTime > 0f) {
            shakeTime = Math.max(0f, shakeTime - dt);
        }
        if (scorePop > 0f) {
            scorePop = Math.max(0f, scorePop - dt * 4.5f);
        }
        if (overlayAlpha < 1f) {
            overlayAlpha = Math.min(1f, overlayAlpha + dt * 5.0f);
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            Particle p = particles[i];
            if (!p.active) continue;
            p.life -= dt;
            if (p.life <= 0f) {
                p.active = false;
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 0.94f;
            p.vy *= 0.94f;
        }
        for (int i = 0; i < MAX_FLOATS; i++) {
            FloatingText ft = floats[i];
            if (!ft.active) continue;
            ft.life -= dt;
            if (ft.life <= 0f) {
                ft.active = false;
                continue;
            }
            ft.y += ft.vy * dt;
        }
    }

    private void drawTopHud(Canvas canvas) {
        float corner = dp(14);

        // Score Badge
        canvas.save();
        if (scorePop > 0f) {
            float sc = 1f + 0.08f * scorePop;
            canvas.scale(sc, sc, scoreBadgeRect.centerX(), scoreBadgeRect.centerY());
        }
        fillPaint.setColor(palette.bgTopBar);
        canvas.drawRoundRect(scoreBadgeRect, corner, corner, fillPaint);
        strokePaint.setColor(palette.boardBorder);
        strokePaint.setStrokeWidth(dp(1.2f));
        canvas.drawRoundRect(scoreBadgeRect, corner, corner, strokePaint);

        // Mini apple icon on left of score badge
        float iconX = scoreBadgeRect.left + dp(18);
        float iconY = scoreBadgeRect.centerY();
        drawMiniApple(canvas, iconX, iconY, dp(7.5f));

        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(10.5f));
        float textCenterX = (scoreBadgeRect.left + dp(32) + scoreBadgeRect.right - dp(6)) * 0.5f;
        canvas.drawText(Strings.scoreLabel(persian), textCenterX,
                scoreBadgeRect.top + scoreBadgeRect.height() * 0.36f, textPaint);

        textPaint.setColor(palette.textPrimary);
        textPaint.setTextSize(dp(17.5f));
        canvas.drawText(Strings.num(engine.getScore(), persian), textCenterX,
                scoreBadgeRect.top + scoreBadgeRect.height() * 0.80f, textPaint);
        canvas.restore();

        // Best Score Badge
        fillPaint.setColor(palette.bgTopBar);
        canvas.drawRoundRect(bestBadgeRect, corner, corner, fillPaint);
        strokePaint.setColor(palette.boardBorder);
        strokePaint.setStrokeWidth(dp(1.2f));
        canvas.drawRoundRect(bestBadgeRect, corner, corner, strokePaint);

        drawMiniTrophy(canvas, bestBadgeRect.left + dp(18), bestBadgeRect.centerY(), dp(8.5f), palette.bonusBody);

        float bestCenterX = (bestBadgeRect.left + dp(32) + bestBadgeRect.right - dp(6)) * 0.5f;
        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(10.5f));
        canvas.drawText(Strings.bestLabel(persian), bestCenterX,
                bestBadgeRect.top + bestBadgeRect.height() * 0.36f, textPaint);

        textPaint.setColor(palette.bonusBody);
        textPaint.setTextSize(dp(17.5f));
        canvas.drawText(Strings.num(engine.getCurrentBestScore(), persian), bestCenterX,
                bestBadgeRect.top + bestBadgeRect.height() * 0.80f, textPaint);

        // Sound Button
        drawCircleButton(canvas, btnSoundRect, sound.isMuted() ? palette.pillBg : palette.bgTopBar);
        drawSpeakerIcon(canvas, btnSoundRect.centerX(), btnSoundRect.centerY(),
                btnSoundRect.width() * 0.25f, sound.isMuted());

        // Pause / Play Button
        drawCircleButton(canvas, btnPauseRect, palette.bgTopBar);
        if (engine.getState() == GameEngine.STATE_PAUSED) {
            drawPlayIcon(canvas, btnPauseRect.centerX(), btnPauseRect.centerY(),
                    btnPauseRect.width() * 0.22f, palette.accent);
        } else {
            drawPauseIcon(canvas, btnPauseRect.centerX(), btnPauseRect.centerY(),
                    btnPauseRect.width() * 0.22f, palette.textPrimary);
        }
    }

    private void drawCircleButton(Canvas canvas, RectF rect, int bgColor) {
        float r = rect.width() * 0.5f;
        fillPaint.setColor(bgColor);
        canvas.drawCircle(rect.centerX(), rect.centerY(), r, fillPaint);
        strokePaint.setColor(palette.boardBorder);
        strokePaint.setStrokeWidth(dp(1.3f));
        canvas.drawCircle(rect.centerX(), rect.centerY(), r, strokePaint);
    }

    private void drawBoard(Canvas canvas) {
        // Outer rounded frame
        float frameR = dp(14);
        fillPaint.setColor(palette.boardFrame);
        canvas.drawRoundRect(boardFrameRect, frameR, frameR, fillPaint);

        // Clip cells cleanly inside inner rounded rect
        canvas.save();
        iconPath.reset();
        iconPath.addRoundRect(boardRect, dp(10), dp(10), Path.Direction.CW);
        canvas.clipPath(iconPath);

        // Base fill dark cell color
        fillPaint.setColor(palette.cellDark);
        canvas.drawRect(boardRect, fillPaint);

        // Subtle checkerboard light cells
        fillPaint.setColor(palette.cellLight);
        for (int y = 0; y < GameEngine.ROWS; y++) {
            float top = boardTop + y * cellSize;
            for (int x = (y & 1); x < GameEngine.COLS; x += 2) {
                float left = boardLeft + x * cellSize;
                canvas.drawRect(left, top, left + cellSize, top + cellSize, fillPaint);
            }
        }
        canvas.restore();

        // Border (solid in Classic/Obstacles, animated glowing dashed in Portal mode!)
        if (engine.getMode() == GameEngine.MODE_PORTAL) {
            portalPaint.setColor(palette.portalBorder);
            canvas.drawRoundRect(boardFrameRect, frameR, frameR, portalPaint);
        } else {
            strokePaint.setColor(palette.boardBorder);
            strokePaint.setStrokeWidth(dp(2.2f));
            canvas.drawRoundRect(boardFrameRect, frameR, frameR, strokePaint);
        }
    }

    private void drawObstacles(Canvas canvas) {
        List<GameEngine.Cell> obs = engine.getObstacles();
        if (obs.isEmpty()) return;
        float pad = cellSize * 0.08f;
        float rad = cellSize * 0.20f;
        for (int i = 0; i < obs.size(); i++) {
            GameEngine.Cell c = obs.get(i);
            float l = boardLeft + c.x * cellSize + pad;
            float t = boardTop + c.y * cellSize + pad;
            float r = l + cellSize - pad * 2f;
            float b = t + cellSize - pad * 2f;

            // Shadow / dark base
            tempRect.set(l, t, r, b);
            fillPaint.setColor(palette.obstacleDark);
            canvas.drawRoundRect(tempRect, rad, rad, fillPaint);

            // Inner raised stone face
            float bevel = cellSize * 0.10f;
            tempRect.set(l + bevel * 0.5f, t + bevel * 0.4f, r - bevel * 0.5f, b - bevel);
            fillPaint.setColor(palette.obstacleFill);
            canvas.drawRoundRect(tempRect, rad * 0.8f, rad * 0.8f, fillPaint);

            // Top specular rim
            strokePaint.setColor(palette.obstacleLight);
            strokePaint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(l + rad, t + bevel * 0.8f, r - rad, t + bevel * 0.8f, strokePaint);
        }
    }

    private void drawFood(Canvas canvas) {
        GameEngine.Cell f = engine.getFood();
        if (f == null) return;
        float cx = boardLeft + (f.x + 0.5f) * cellSize;
        float cy = boardTop + (f.y + 0.5f) * cellSize;
        float pulse = 1f + 0.08f * (float) Math.sin(animTime * 5.2f);
        float r = cellSize * 0.39f * pulse;

        // Soft pulsing glow
        fillPaint.setColor(palette.foodGlow);
        canvas.drawCircle(cx, cy, r * 1.48f, fillPaint);

        drawMiniApple(canvas, cx, cy, r);
    }

    private void drawMiniApple(Canvas canvas, float cx, float cy, float r) {
        // Main body
        fillPaint.setColor(palette.foodBody);
        canvas.drawCircle(cx, cy + r * 0.06f, r, fillPaint);

        // Specular highlight
        fillPaint.setColor(0x88FFFFFF);
        canvas.drawCircle(cx - r * 0.30f, cy - r * 0.24f, r * 0.26f, fillPaint);

        // Stem
        strokePaint.setColor(0xFF78350F);
        strokePaint.setStrokeWidth(Math.max(dp(1.4f), r * 0.22f));
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx, cy - r * 0.72f, cx + r * 0.14f, cy - r * 1.18f, strokePaint);

        // Leaf
        fillPaint.setColor(palette.foodLeaf);
        iconPath.reset();
        iconPath.moveTo(cx + r * 0.06f, cy - r * 0.90f);
        iconPath.quadTo(cx + r * 0.75f, cy - r * 1.25f, cx + r * 0.82f, cy - r * 0.70f);
        iconPath.quadTo(cx + r * 0.38f, cy - r * 0.65f, cx + r * 0.06f, cy - r * 0.90f);
        canvas.drawPath(iconPath, fillPaint);
    }

    private void drawBonusFood(Canvas canvas) {
        GameEngine.Cell b = engine.getBonusFood();
        if (b == null) return;
        float progress = engine.getBonusProgress();
        float cx = boardLeft + (b.x + 0.5f) * cellSize;
        float cy = boardTop + (b.y + 0.5f) * cellSize;
        float pulse = 1f + 0.10f * (float) Math.sin(animTime * 8.0f);
        float r = cellSize * 0.42f * pulse;

        // Golden glow
        fillPaint.setColor(palette.bonusGlow);
        canvas.drawCircle(cx, cy, r * 1.55f, fillPaint);

        // Countdown timer ring
        float ringR = cellSize * 0.56f;
        tempRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        strokePaint.setColor(0x44FFFFFF);
        strokePaint.setStrokeWidth(dp(2.2f));
        canvas.drawOval(tempRect, strokePaint);
        strokePaint.setColor(palette.bonusBody);
        canvas.drawArc(tempRect, -90f, 360f * progress, false, strokePaint);

        // Rotating 5-point golden star
        canvas.save();
        canvas.rotate(animTime * 55f, cx, cy);
        iconPath.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float rad = (i % 2 == 0) ? r : r * 0.48f;
            float px = cx + (float) Math.cos(ang) * rad;
            float py = cy + (float) Math.sin(ang) * rad;
            if (i == 0) iconPath.moveTo(px, py);
            else iconPath.lineTo(px, py);
        }
        iconPath.close();
        fillPaint.setColor(palette.bonusBody);
        canvas.drawPath(iconPath, fillPaint);
        fillPaint.setColor(0xAAFFFFFF);
        canvas.drawCircle(cx, cy, r * 0.25f, fillPaint);
        canvas.restore();
    }

    private void drawSnake(Canvas canvas) {
        List<GameEngine.Cell> curr = engine.getSnake();
        List<GameEngine.Cell> prev = engine.getPrevSnake();
        int n = curr.size();
        if (n == 0) return;

        float p = engine.getMoveProgress();
        for (int i = 0; i < n; i++) {
            GameEngine.Cell c = curr.get(i);
            float gx = c.x;
            float gy = c.y;
            if (i < prev.size()) {
                GameEngine.Cell pr = prev.get(i);
                int dx = c.x - pr.x;
                int dy = c.y - pr.y;
                // Only interpolate adjacent cells (skip cross-board portal wrap streaks!)
                if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                    gx = pr.x + dx * p;
                    gy = pr.y + dy * p;
                }
            }
            interpX[i] = boardLeft + (gx + 0.5f) * cellSize;
            interpY[i] = boardTop + (gy + 0.5f) * cellSize;
        }

        float baseDiam = cellSize * 0.80f;

        // PASS 1: Dark outline silhouette (tail to head)
        snakePaint.setColor(palette.snakeOutline);
        fillPaint.setColor(palette.snakeOutline);
        for (int i = n - 1; i >= 0; i--) {
            float t = (n <= 1) ? 0f : (i / (float) (n - 1));
            float taper = (i == 0) ? 1.08f : (1.0f - 0.34f * t * t);
            float w = baseDiam * taper + dp(2.8f);
            if (i > 0 && isAdjacentPx(interpX[i], interpY[i], interpX[i - 1], interpY[i - 1])) {
                snakePaint.setStrokeWidth(w);
                canvas.drawLine(interpX[i], interpY[i], interpX[i - 1], interpY[i - 1], snakePaint);
            } else {
                canvas.drawCircle(interpX[i], interpY[i], w * 0.5f, fillPaint);
            }
        }

        // PASS 2: Vibrant Head -> Mid -> Tail gradient body
        for (int i = n - 1; i >= 0; i--) {
            float t = (n <= 1) ? 0f : (i / (float) (n - 1));
            float taper = (i == 0) ? 1.06f : (1.0f - 0.34f * t * t);
            float w = baseDiam * taper;
            int segColor = (t < 0.5f)
                    ? ThemePalette.lerpColor(palette.snakeHead, palette.snakeMid, t * 2f)
                    : ThemePalette.lerpColor(palette.snakeMid, palette.snakeTail, (t - 0.5f) * 2f);
            snakePaint.setColor(segColor);
            fillPaint.setColor(segColor);
            if (i > 0 && isAdjacentPx(interpX[i], interpY[i], interpX[i - 1], interpY[i - 1])) {
                snakePaint.setStrokeWidth(w);
                canvas.drawLine(interpX[i], interpY[i], interpX[i - 1], interpY[i - 1], snakePaint);
            }
            canvas.drawCircle(interpX[i], interpY[i], w * 0.5f, fillPaint);
        }

        // PASS 3: Subtle glossy dorsal highlight along body
        fillPaint.setColor(palette.snakeHighlight);
        for (int i = n - 1; i >= 1; i--) {
            float t = i / (float) Math.max(1, n - 1);
            float r = (baseDiam * (1.0f - 0.34f * t * t)) * 0.19f;
            canvas.drawCircle(interpX[i] - r * 0.5f, interpY[i] - r * 0.7f, r, fillPaint);
        }

        // PASS 4: Expressive head (flickering tongue + food-tracking eyes)
        drawSnakeHeadDetails(canvas, interpX[0], interpY[0], baseDiam * 0.53f);
    }

    private boolean isAdjacentPx(float x1, float y1, float x2, float y2) {
        float maxD = cellSize * 1.65f;
        return Math.abs(x1 - x2) <= maxD && Math.abs(y1 - y2) <= maxD;
    }

    private void drawSnakeHeadDetails(Canvas canvas, float hx, float hy, float headR) {
        int dir = engine.getCurrentDir();
        float fx = 0f, fy = 0f;
        if (dir == GameEngine.DIR_UP) fy = -1f;
        else if (dir == GameEngine.DIR_DOWN) fy = 1f;
        else if (dir == GameEngine.DIR_LEFT) fx = -1f;
        else fx = 1f;

        float px = -fy;
        float py = fx;

        // Flickering cute forked tongue when near food or periodically
        GameEngine.Cell target = engine.getBonusFood() != null ? engine.getBonusFood() : engine.getFood();
        boolean nearFood = false;
        float targetX = hx + fx * cellSize * 2f;
        float targetY = hy + fy * cellSize * 2f;
        if (target != null) {
            targetX = boardLeft + (target.x + 0.5f) * cellSize;
            targetY = boardTop + (target.y + 0.5f) * cellSize;
            float dist = (float) Math.hypot(targetX - hx, targetY - hy);
            nearFood = dist < cellSize * 4.5f;
        }

        boolean showTongue = engine.getState() != GameEngine.STATE_GAME_OVER
                && (nearFood || (animTime % 2.8f) < 0.35f);
        if (showTongue) {
            float extend = 0.65f + 0.35f * (float) Math.abs(Math.sin(animTime * 16f));
            float tLen = headR * 0.95f * extend;
            float tx0 = hx + fx * headR * 0.85f;
            float ty0 = hy + fy * headR * 0.85f;
            float tx1 = tx0 + fx * tLen;
            float ty1 = ty0 + fy * tLen;
            float fork = headR * 0.32f;
            strokePaint.setColor(palette.tongueColor);
            strokePaint.setStrokeWidth(Math.max(dp(1.4f), headR * 0.16f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(tx0, ty0, tx1, ty1, strokePaint);
            canvas.drawLine(tx1, ty1, tx1 + (fx * 0.6f + px * 0.65f) * fork,
                    ty1 + (fy * 0.6f + py * 0.65f) * fork, strokePaint);
            canvas.drawLine(tx1, ty1, tx1 + (fx * 0.6f - px * 0.65f) * fork,
                    ty1 + (fy * 0.6f - py * 0.65f) * fork, strokePaint);
        }

        // Eyes
        float eyeOffFwd = headR * 0.25f;
        float eyeOffSide = headR * 0.46f;
        float eyeR = headR * 0.33f;
        boolean dead = (engine.getState() == GameEngine.STATE_GAME_OVER && !engine.isWonBoard());

        for (int s = -1; s <= 1; s += 2) {
            float ex = hx + fx * eyeOffFwd + px * eyeOffSide * s;
            float ey = hy + fy * eyeOffFwd + py * eyeOffSide * s;

            fillPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(ex, ey, eyeR, fillPaint);

            if (dead) {
                // Cute dizzy X eyes on crash
                strokePaint.setColor(0xFF0F172A);
                strokePaint.setStrokeWidth(Math.max(dp(1.3f), eyeR * 0.32f));
                float xr = eyeR * 0.48f;
                canvas.drawLine(ex - xr, ey - xr, ex + xr, ey + xr, strokePaint);
                canvas.drawLine(ex - xr, ey + xr, ex + xr, ey - xr, strokePaint);
            } else {
                // Pupil tracks food smoothly
                float dx = targetX - ex;
                float dy = targetY - ey;
                float len = (float) Math.hypot(dx, dy);
                float lx = (len > 1f) ? (dx / len) : fx;
                float ly = (len > 1f) ? (dy / len) : fy;
                float pX = ex + lx * eyeR * 0.32f;
                float pY = ey + ly * eyeR * 0.32f;
                float pR = eyeR * 0.56f;
                fillPaint.setColor(0xFF0F172A);
                canvas.drawCircle(pX, pY, pR, fillPaint);
                fillPaint.setColor(0xEEFFFFFF);
                canvas.drawCircle(pX - pR * 0.28f, pY - pR * 0.28f, pR * 0.32f, fillPaint);
            }
        }
    }

    private void drawParticles(Canvas canvas) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            Particle p = particles[i];
            if (!p.active) continue;
            float a = Math.max(0f, p.life / p.maxLife);
            fillPaint.setColor(withAlpha(p.color, (int) (255 * a)));
            canvas.drawCircle(p.x, p.y, p.radius * (0.4f + 0.6f * a), fillPaint);
        }
    }

    private void drawFloatingTexts(Canvas canvas) {
        for (int i = 0; i < MAX_FLOATS; i++) {
            FloatingText ft = floats[i];
            if (!ft.active) continue;
            float a = Math.max(0f, ft.life / ft.maxLife);
            textPaint.setTextSize(ft.big ? dp(15.5f) : dp(14f));
            textPaint.setColor(withAlpha(0xFF000000, (int) (140 * a)));
            canvas.drawText(ft.text, ft.x + dp(1), ft.y + dp(1.5f), textPaint);
            textPaint.setColor(withAlpha(ft.color, (int) (255 * a)));
            canvas.drawText(ft.text, ft.x, ft.y, textPaint);
        }
    }

    private void drawDpad(Canvas canvas) {
        float corner = dp(15);
        for (int dir = 0; dir < 4; dir++) {
            RectF r = dpadRects[dir];
            boolean pressed = (pressedDpadDir == dir);
            fillPaint.setColor(pressed ? palette.dpadPressed : palette.dpadBg);
            canvas.drawRoundRect(r, corner, corner, fillPaint);

            strokePaint.setColor(pressed ? palette.accent : palette.dpadBorder);
            strokePaint.setStrokeWidth(pressed ? dp(2.0f) : dp(1.4f));
            canvas.drawRoundRect(r, corner, corner, strokePaint);

            int iconCol = pressed
                    ? (palette.darkIconsOnAccent ? 0xFF0A140A : 0xFFFFFFFF)
                    : palette.dpadIcon;
            drawChevron(canvas, r.centerX(), r.centerY(), Math.min(r.width(), r.height()) * 0.22f, dir, iconCol);
        }
    }

    private void drawChevron(Canvas canvas, float cx, float cy, float sz, int dir, int color) {
        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(dp(3.0f));
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        iconPath.reset();
        if (dir == GameEngine.DIR_UP) {
            iconPath.moveTo(cx - sz, cy + sz * 0.45f);
            iconPath.lineTo(cx, cy - sz * 0.55f);
            iconPath.lineTo(cx + sz, cy + sz * 0.45f);
        } else if (dir == GameEngine.DIR_DOWN) {
            iconPath.moveTo(cx - sz, cy - sz * 0.45f);
            iconPath.lineTo(cx, cy + sz * 0.55f);
            iconPath.lineTo(cx + sz, cy - sz * 0.45f);
        } else if (dir == GameEngine.DIR_LEFT) {
            iconPath.moveTo(cx + sz * 0.45f, cy - sz);
            iconPath.lineTo(cx - sz * 0.55f, cy);
            iconPath.lineTo(cx + sz * 0.45f, cy + sz);
        } else {
            iconPath.moveTo(cx - sz * 0.45f, cy - sz);
            iconPath.lineTo(cx + sz * 0.55f, cy);
            iconPath.lineTo(cx - sz * 0.45f, cy + sz);
        }
        canvas.drawPath(iconPath, strokePaint);
    }

    // --- Overlays ---

    private void drawScrim(Canvas canvas) {
        fillPaint.setColor(withAlpha(0xFF040812, (int) (175 * overlayAlpha)));
        canvas.drawRect(0, 0, viewW, viewH, fillPaint);
    }

    private void drawModalCard(Canvas canvas, RectF card) {
        float rad = dp(24);
        fillPaint.setColor(withAlpha(palette.cardBg, (int) (244 * overlayAlpha)));
        canvas.drawRoundRect(card, rad, rad, fillPaint);
        strokePaint.setColor(withAlpha(palette.cardBorder, (int) (255 * overlayAlpha)));
        strokePaint.setStrokeWidth(dp(1.8f));
        canvas.drawRoundRect(card, rad, rad, strokePaint);
    }

    private void drawMenuOverlay(Canvas canvas) {
        drawScrim(canvas);

        float pad = dp(18);
        float w = modalRect.width();
        float h = Math.min(modalRect.height(), dp(516));
        float l = modalRect.left;
        float t = (viewH - h) * 0.5f;
        tempRect.set(l, t, l + w, t + h);
        drawModalCard(canvas, tempRect);

        float cx = tempRect.centerX();
        float innerL = tempRect.left + pad;
        float innerW = w - pad * 2f;
        float y = tempRect.top + h * 0.065f;

        // Animated snake emblem at top
        drawMenuEmblem(canvas, cx, y + dp(14), dp(22));
        y += dp(44);

        // Title & Subtitle
        textPaint.setColor(palette.textPrimary);
        textPaint.setTextSize(dp(26f));
        canvas.drawText(Strings.title(persian), cx, y + dp(12), textPaint);
        y += dp(28);

        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(12.5f));
        canvas.drawText(Strings.subtitle(persian), cx, y + dp(6), textPaint);
        y += dp(22);

        float rowH = Math.min(dp(38), h * 0.074f);
        float secGap = Math.min(dp(14), h * 0.025f);
        float labelH = dp(17);

        // Mode Section
        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(11.5f));
        canvas.drawText(Strings.modeSection(persian), cx, y + dp(10), textPaint);
        y += labelH;
        drawSegmentedRow(canvas, innerL, y, innerW, rowH, btnModeRects,
                new String[]{
                        Strings.modeName(0, persian),
                        Strings.modeName(1, persian),
                        Strings.modeName(2, persian)
                },
                engine.getMode(), null);
        y += rowH + secGap;

        // Speed Section
        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(11.5f));
        canvas.drawText(Strings.speedSection(persian), cx, y + dp(10), textPaint);
        y += labelH;
        drawSegmentedRow(canvas, innerL, y, innerW, rowH, btnSpeedRects,
                new String[]{
                        Strings.speedName(0, persian),
                        Strings.speedName(1, persian),
                        Strings.speedName(2, persian)
                },
                engine.getSpeedLevel(), null);
        y += rowH + secGap;

        // Theme Section
        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(11.5f));
        canvas.drawText(Strings.themeSection(persian), cx, y + dp(10), textPaint);
        y += labelH;
        int[] swatches = new int[]{ThemePalette.NEON.accent, ThemePalette.RETRO.cellLight, ThemePalette.SUNSET.accent};
        drawSegmentedRow(canvas, innerL, y, innerW, rowH, btnThemeRects,
                new String[]{
                        Strings.themeName(0, persian),
                        Strings.themeName(1, persian),
                        Strings.themeName(2, persian)
                },
                palette.id, swatches);
        y += rowH + secGap;

        // Quick Toggles Row (D-Pad + Language)
        float halfGap = dp(8);
        float dpadW = innerW * 0.62f - halfGap * 0.5f;
        btnDpadToggleRect.set(innerL, y, innerL + dpadW, y + rowH);
        btnLangToggleRect.set(btnDpadToggleRect.right + halfGap, y, innerL + innerW, y + rowH);
        drawPillButton(canvas, btnDpadToggleRect, Strings.dpadToggle(showDpad, persian),
                palette.pillBg, palette.textPrimary, dp(12f), palette.boardBorder);
        drawPillButton(canvas, btnLangToggleRect, Strings.langToggle(persian),
                palette.pillBg, palette.textPrimary, dp(12f), palette.boardBorder);
        y += rowH + secGap;

        // Current Mode Best Score strip
        int best = engine.getCurrentBestScore();
        textPaint.setColor(palette.bonusBody);
        textPaint.setTextSize(dp(13.5f));
        String bestStr = Strings.bestLabel(persian) + " (" + Strings.modeName(engine.getMode(), persian)
                + "): " + Strings.num(best, persian);
        canvas.drawText(bestStr, cx, y + dp(12), textPaint);

        // Big Play Button pinned cleanly near bottom of card
        float playH = Math.min(dp(50), h * 0.102f);
        float playTop = tempRect.bottom - pad - playH;
        btnPrimaryRect.set(innerL, playTop, innerL + innerW, playTop + playH);
        drawAccentButton(canvas, btnPrimaryRect, Strings.play(persian), dp(17.5f));

        // Clear unused rects
        btnSecondaryRect.setEmpty();
        btnTertiaryRect.setEmpty();
    }

    private void drawPauseOverlay(Canvas canvas) {
        drawScrim(canvas);

        float pad = dp(20);
        float w = Math.min(modalRect.width(), dp(310));
        float h = dp(286);
        float l = (viewW - w) * 0.5f;
        float t = (viewH - h) * 0.5f;
        tempRect.set(l, t, l + w, t + h);
        drawModalCard(canvas, tempRect);

        float cx = tempRect.centerX();
        float innerL = tempRect.left + pad;
        float innerW = w - pad * 2f;

        textPaint.setColor(palette.textPrimary);
        textPaint.setTextSize(dp(23f));
        canvas.drawText(Strings.paused(persian), cx, tempRect.top + dp(52), textPaint);

        float btnH = dp(48);
        float gap = dp(12);
        float y = tempRect.top + dp(84);

        btnPrimaryRect.set(innerL, y, innerL + innerW, y + btnH);
        drawAccentButton(canvas, btnPrimaryRect, Strings.resume(persian), dp(16f));
        y += btnH + gap;

        btnSecondaryRect.set(innerL, y, innerL + innerW, y + btnH);
        drawPillButton(canvas, btnSecondaryRect, Strings.restart(persian),
                palette.pillBg, palette.textPrimary, dp(15f), palette.boardBorder);
        y += btnH + gap;

        btnTertiaryRect.set(innerL, y, innerL + innerW, y + btnH);
        drawPillButton(canvas, btnTertiaryRect, Strings.mainMenu(persian),
                palette.pillBg, palette.textSecondary, dp(14.5f), palette.boardBorder);
    }

    private void drawGameOverOverlay(Canvas canvas) {
        drawScrim(canvas);

        float pad = dp(20);
        float w = Math.min(modalRect.width(), dp(330));
        float h = dp(374);
        float l = (viewW - w) * 0.5f;
        float t = (viewH - h) * 0.5f;
        tempRect.set(l, t, l + w, t + h);
        drawModalCard(canvas, tempRect);

        float cx = tempRect.centerX();
        float innerL = tempRect.left + pad;
        float innerW = w - pad * 2f;
        float y = tempRect.top + dp(46);

        // Title
        textPaint.setColor(engine.isWonBoard() ? palette.bonusBody : palette.foodBody);
        textPaint.setTextSize(dp(25f));
        canvas.drawText(engine.isWonBoard() ? Strings.youWin(persian) : Strings.gameOver(persian),
                cx, y, textPaint);
        y += dp(14);

        // New Record Banner (if beaten!)
        if (engine.isNewBestThisRun()) {
            float badgeW = dp(164);
            float badgeH = dp(28);
            statBoxRect.set(cx - badgeW * 0.5f, y, cx + badgeW * 0.5f, y + badgeH);
            fillPaint.setColor(withAlpha(palette.bonusBody, 42));
            canvas.drawRoundRect(statBoxRect, dp(14), dp(14), fillPaint);
            strokePaint.setColor(palette.bonusBody);
            strokePaint.setStrokeWidth(dp(1.2f));
            canvas.drawRoundRect(statBoxRect, dp(14), dp(14), strokePaint);
            textPaint.setColor(palette.bonusBody);
            textPaint.setTextSize(dp(12.5f));
            Paint.FontMetrics nrFm = textPaint.getFontMetrics();
            float nrBase = statBoxRect.centerY() - (nrFm.ascent + nrFm.descent) * 0.5f;
            canvas.drawText("★  " + Strings.newRecord(persian) + "  ★", cx, nrBase, textPaint);
            y += badgeH + dp(14);
        } else {
            y += dp(16);
        }

        // 2x2 Stats Grid
        float statGap = dp(10);
        float statW = (innerW - statGap) * 0.5f;
        float statH = dp(58);

        drawStatBox(canvas, innerL, y, statW, statH,
                Strings.scoreLabel(persian), Strings.num(engine.getScore(), persian), palette.textPrimary);
        drawStatBox(canvas, innerL + statW + statGap, y, statW, statH,
                Strings.bestLabel(persian), Strings.num(engine.getCurrentBestScore(), persian), palette.bonusBody);
        y += statH + statGap;

        drawStatBox(canvas, innerL, y, statW, statH,
                Strings.applesLabel(persian), Strings.num(engine.getApplesEaten(), persian), palette.foodBody);
        drawStatBox(canvas, innerL + statW + statGap, y, statW, statH,
                Strings.lengthLabel(persian), Strings.num(engine.getSnake().size(), persian), palette.snakeHead);
        y += statH + dp(20);

        // Action Buttons
        float btnH = dp(48);
        btnPrimaryRect.set(innerL, y, innerL + innerW, y + btnH);
        drawAccentButton(canvas, btnPrimaryRect, Strings.playAgain(persian), dp(16.5f));
        y += btnH + dp(10);

        btnSecondaryRect.set(innerL, y, innerL + innerW, y + dp(42));
        drawPillButton(canvas, btnSecondaryRect, Strings.mainMenu(persian),
                palette.pillBg, palette.textSecondary, dp(14f), palette.boardBorder);
        btnTertiaryRect.setEmpty();
    }

    private void drawStatBox(Canvas canvas, float l, float t, float w, float h,
                             String label, String value, int valColor) {
        statBoxRect.set(l, t, l + w, t + h);
        fillPaint.setColor(palette.pillBg);
        canvas.drawRoundRect(statBoxRect, dp(14), dp(14), fillPaint);
        strokePaint.setColor(palette.boardBorder);
        strokePaint.setStrokeWidth(dp(1.0f));
        canvas.drawRoundRect(statBoxRect, dp(14), dp(14), strokePaint);

        textPaint.setColor(palette.textSecondary);
        textPaint.setTextSize(dp(11.5f));
        canvas.drawText(label, statBoxRect.centerX(), statBoxRect.top + h * 0.36f, textPaint);

        textPaint.setColor(valColor);
        textPaint.setTextSize(dp(19f));
        canvas.drawText(value, statBoxRect.centerX(), statBoxRect.top + h * 0.80f, textPaint);
    }

    private void drawSegmentedRow(Canvas canvas, float l, float t, float w, float h,
                                  RectF[] outRects, String[] labels, int selectedIndex, int[] swatches) {
        int count = labels.length;
        float gap = dp(6);
        float itemW = (w - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            float il = l + i * (itemW + gap);
            outRects[i].set(il, t, il + itemW, t + h);
            boolean sel = (i == selectedIndex);
            int bg = sel ? palette.pillSelectedBg : palette.pillBg;
            int fg = sel
                    ? (palette.darkIconsOnAccent ? 0xFF0A140A : 0xFFFFFFFF)
                    : palette.textPrimary;
            int border = sel ? palette.accent : palette.boardBorder;
            float textShift = (swatches != null) ? dp(5.5f) : 0f;
            drawPillButtonShifted(canvas, outRects[i], labels[i], bg, fg, dp(12.2f), border, textShift);
            if (swatches != null && i < swatches.length) {
                float sx = outRects[i].left + dp(11);
                float sy = outRects[i].centerY();
                float sr = dp(4.2f);
                fillPaint.setColor(swatches[i]);
                canvas.drawCircle(sx, sy, sr, fillPaint);
                strokePaint.setColor(0xDDFFFFFF);
                strokePaint.setStrokeWidth(dp(1.1f));
                canvas.drawCircle(sx, sy, sr, strokePaint);
            }
        }
    }

    private void drawPillButton(Canvas canvas, RectF rect, String label,
                                int bgColor, int textColor, float textSize, int borderColor) {
        drawPillButtonShifted(canvas, rect, label, bgColor, textColor, textSize, borderColor, 0f);
    }

    private void drawPillButtonShifted(Canvas canvas, RectF rect, String label,
                                       int bgColor, int textColor, float textSize,
                                       int borderColor, float textOffsetX) {
        float r = dp(12);
        fillPaint.setColor(bgColor);
        canvas.drawRoundRect(rect, r, r, fillPaint);
        strokePaint.setColor(borderColor);
        strokePaint.setStrokeWidth(dp(1.2f));
        canvas.drawRoundRect(rect, r, r, strokePaint);

        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseY = rect.centerY() - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(label, rect.centerX() + textOffsetX, baseY, textPaint);
    }

    private void drawAccentButton(Canvas canvas, RectF rect, String label, float textSize) {
        float r = dp(16);
        fillPaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                palette.accent, palette.accentDark, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, r, r, fillPaint);
        fillPaint.setShader(null);

        // Subtle top gloss highlight
        fillPaint.setColor(0x28FFFFFF);
        canvas.drawRoundRect(rect.left + dp(3), rect.top + dp(2),
                rect.right - dp(3), rect.centerY(), r, r, fillPaint);

        textPaint.setColor(palette.darkIconsOnAccent ? 0xFF0A140A : 0xFFFFFFFF);
        textPaint.setTextSize(textSize);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseY = rect.centerY() - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(label, rect.centerX(), baseY, textPaint);
    }

    private void drawMenuEmblem(Canvas canvas, float cx, float cy, float r) {
        // Soft radial aura
        fillPaint.setColor(withAlpha(palette.accent, 38));
        canvas.drawCircle(cx, cy, r * 1.45f, fillPaint);

        // Mini sinuous animated snake wave
        int segs = 9;
        for (int i = segs - 1; i >= 0; i--) {
            float t = i / (float) (segs - 1);
            float sx = cx + (0.5f - t) * r * 2.1f;
            float sy = cy + (float) Math.sin(animTime * 4.0f - i * 0.7f) * r * 0.28f;
            float sr = r * (0.28f + 0.16f * (1f - t));
            int col = ThemePalette.lerpColor(palette.snakeHead, palette.snakeTail, t);
            fillPaint.setColor(palette.snakeOutline);
            canvas.drawCircle(sx, sy, sr + dp(1.5f), fillPaint);
            fillPaint.setColor(col);
            canvas.drawCircle(sx, sy, sr, fillPaint);
            if (i == 0) {
                // Eyes on head
                fillPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(sx + sr * 0.25f, sy - sr * 0.35f, sr * 0.28f, fillPaint);
                canvas.drawCircle(sx + sr * 0.25f, sy + sr * 0.35f, sr * 0.28f, fillPaint);
                fillPaint.setColor(0xFF0F172A);
                canvas.drawCircle(sx + sr * 0.33f, sy - sr * 0.35f, sr * 0.15f, fillPaint);
                canvas.drawCircle(sx + sr * 0.33f, sy + sr * 0.35f, sr * 0.15f, fillPaint);
            }
        }
    }

    // --- Vector HUD Icons ---

    private void drawPauseIcon(Canvas canvas, float cx, float cy, float r, int color) {
        fillPaint.setColor(color);
        float bw = r * 0.56f;
        float bh = r * 1.65f;
        float gap = r * 0.36f;
        canvas.drawRoundRect(cx - gap - bw, cy - bh * 0.5f, cx - gap, cy + bh * 0.5f, dp(2), dp(2), fillPaint);
        canvas.drawRoundRect(cx + gap, cy - bh * 0.5f, cx + gap + bw, cy + bh * 0.5f, dp(2), dp(2), fillPaint);
    }

    private void drawPlayIcon(Canvas canvas, float cx, float cy, float r, int color) {
        fillPaint.setColor(color);
        iconPath.reset();
        iconPath.moveTo(cx - r * 0.65f, cy - r * 0.85f);
        iconPath.lineTo(cx + r * 0.95f, cy);
        iconPath.lineTo(cx - r * 0.65f, cy + r * 0.85f);
        iconPath.close();
        canvas.drawPath(iconPath, fillPaint);
    }

    private void drawSpeakerIcon(Canvas canvas, float cx, float cy, float r, boolean muted) {
        fillPaint.setColor(muted ? palette.textSecondary : palette.textPrimary);
        iconPath.reset();
        iconPath.moveTo(cx - r * 0.95f, cy - r * 0.35f);
        iconPath.lineTo(cx - r * 0.45f, cy - r * 0.35f);
        iconPath.lineTo(cx + r * 0.15f, cy - r * 0.85f);
        iconPath.lineTo(cx + r * 0.15f, cy + r * 0.85f);
        iconPath.lineTo(cx - r * 0.45f, cy + r * 0.35f);
        iconPath.lineTo(cx - r * 0.95f, cy + r * 0.35f);
        iconPath.close();
        canvas.drawPath(iconPath, fillPaint);

        if (muted) {
            strokePaint.setColor(palette.foodBody);
            strokePaint.setStrokeWidth(dp(2.0f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx + r * 0.38f, cy - r * 0.42f, cx + r * 0.98f, cy + r * 0.42f, strokePaint);
            canvas.drawLine(cx + r * 0.98f, cy - r * 0.42f, cx + r * 0.38f, cy + r * 0.42f, strokePaint);
        } else {
            strokePaint.setColor(palette.accent);
            strokePaint.setStrokeWidth(dp(1.8f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            tempRect.set(cx - r * 0.25f, cy - r * 0.52f, cx + r * 0.65f, cy + r * 0.52f);
            canvas.drawArc(tempRect, -45f, 90f, false, strokePaint);
            tempRect.set(cx - r * 0.45f, cy - r * 0.85f, cx + r * 1.05f, cy + r * 0.85f);
            canvas.drawArc(tempRect, -45f, 90f, false, strokePaint);
        }
    }

    private void drawMiniTrophy(Canvas canvas, float cx, float cy, float r, int color) {
        fillPaint.setColor(color);
        // Cup bowl
        iconPath.reset();
        iconPath.moveTo(cx - r * 0.72f, cy - r * 0.72f);
        iconPath.lineTo(cx + r * 0.72f, cy - r * 0.72f);
        iconPath.quadTo(cx + r * 0.65f, cy + r * 0.18f, cx, cy + r * 0.26f);
        iconPath.quadTo(cx - r * 0.65f, cy + r * 0.18f, cx - r * 0.72f, cy - r * 0.72f);
        iconPath.close();
        canvas.drawPath(iconPath, fillPaint);
        // Stem + Base
        canvas.drawRect(cx - r * 0.16f, cy + r * 0.20f, cx + r * 0.16f, cy + r * 0.62f, fillPaint);
        canvas.drawRoundRect(cx - r * 0.55f, cy + r * 0.55f, cx + r * 0.55f, cy + r * 0.82f, dp(1.5f), dp(1.5f), fillPaint);
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // --- Touch & Keyboard Input ---

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int idx = event.getActionIndex();
        float x = event.getX(idx);
        float y = event.getY(idx);
        int pid = event.getPointerId(idx);

        int st = engine.getState();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            // 1. Always check HUD Sound button
            if (hit(btnSoundRect, x, y, dp(6))) {
                sound.setMuted(!sound.isMuted());
                if (!sound.isMuted()) sound.play(SoundEngine.SND_BUTTON);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                savePrefs();
                return true;
            }

            // 2. HUD Pause button (during PLAYING or PAUSED)
            if ((st == GameEngine.STATE_PLAYING || st == GameEngine.STATE_PAUSED)
                    && hit(btnPauseRect, x, y, dp(6))) {
                sound.play(SoundEngine.SND_BUTTON);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (st == GameEngine.STATE_PLAYING) {
                    engine.pause();
                    overlayAlpha = 0f;
                } else {
                    engine.resume();
                }
                return true;
            }

            // 3. Overlay clicks
            if (st == GameEngine.STATE_MENU) {
                return handleMenuTouch(x, y);
            } else if (st == GameEngine.STATE_PAUSED) {
                return handlePauseTouch(x, y);
            } else if (st == GameEngine.STATE_GAME_OVER) {
                return handleGameOverTouch(x, y);
            }

            // 4. Gameplay: D-Pad press first
            if (showDpad) {
                for (int dir = 0; dir < 4; dir++) {
                    if (hit(dpadRects[dir], x, y, dp(8))) {
                        pressedDpadDir = dir;
                        activeDpadPointer = pid;
                        engine.queueDirection(dir);
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        return true;
                    }
                }
            }

            // 5. Gameplay: Start swipe gesture anywhere else
            if (swipePointerId == -1) {
                swipePointerId = pid;
                swipeStartX = x;
                swipeStartY = y;
            }
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (st == GameEngine.STATE_PLAYING && swipePointerId != -1) {
                int pIdx = event.findPointerIndex(swipePointerId);
                if (pIdx >= 0) {
                    float cx = event.getX(pIdx);
                    float cy = event.getY(pIdx);
                    float dx = cx - swipeStartX;
                    float dy = cy - swipeStartY;
                    if (Math.abs(dx) >= minSwipePx || Math.abs(dy) >= minSwipePx) {
                        int dir;
                        if (Math.abs(dx) > Math.abs(dy)) {
                            dir = (dx > 0f) ? GameEngine.DIR_RIGHT : GameEngine.DIR_LEFT;
                        } else {
                            dir = (dy > 0f) ? GameEngine.DIR_DOWN : GameEngine.DIR_UP;
                        }
                        if (engine.queueDirection(dir)) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                        // Reset anchor for continuous drag steering
                        swipeStartX = cx;
                        swipeStartY = cy;
                    }
                }
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            if (pid == activeDpadPointer || action == MotionEvent.ACTION_CANCEL) {
                pressedDpadDir = -1;
                activeDpadPointer = -1;
            }
            if (pid == swipePointerId || action == MotionEvent.ACTION_CANCEL) {
                swipePointerId = -1;
            }
            return true;
        }

        return super.onTouchEvent(event);
    }

    private boolean handleMenuTouch(float x, float y) {
        if (hit(btnPrimaryRect, x, y, dp(4))) {
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            engine.startGame();
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (hit(btnModeRects[i], x, y, dp(3))) {
                engine.setMode(i);
                sound.play(SoundEngine.SND_BUTTON);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                savePrefs();
                return true;
            }
            if (hit(btnSpeedRects[i], x, y, dp(3))) {
                engine.setSpeedLevel(i);
                sound.play(SoundEngine.SND_BUTTON);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                savePrefs();
                return true;
            }
            if (hit(btnThemeRects[i], x, y, dp(3))) {
                palette = ThemePalette.byId(i);
                if (themeCallback != null) themeCallback.onThemeChanged(palette.bgOuter);
                sound.play(SoundEngine.SND_BUTTON);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                savePrefs();
                return true;
            }
        }
        if (hit(btnDpadToggleRect, x, y, dp(3))) {
            showDpad = !showDpad;
            computeLayout(viewW, viewH);
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            savePrefs();
            return true;
        }
        if (hit(btnLangToggleRect, x, y, dp(3))) {
            persian = !persian;
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            savePrefs();
            return true;
        }
        return true;
    }

    private boolean handlePauseTouch(float x, float y) {
        if (hit(btnPrimaryRect, x, y, dp(4))) {
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            engine.resume();
            return true;
        }
        if (hit(btnSecondaryRect, x, y, dp(4))) {
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            engine.startGame();
            return true;
        }
        if (hit(btnTertiaryRect, x, y, dp(4))) {
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            engine.goToMenu();
            overlayAlpha = 0f;
            return true;
        }
        return true;
    }

    private boolean handleGameOverTouch(float x, float y) {
        if (hit(btnPrimaryRect, x, y, dp(4))) {
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            engine.startGame();
            return true;
        }
        if (hit(btnSecondaryRect, x, y, dp(4))) {
            sound.play(SoundEngine.SND_BUTTON);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            engine.goToMenu();
            overlayAlpha = 0f;
            return true;
        }
        return true;
    }

    private static boolean hit(RectF rect, float x, float y, float slop) {
        if (rect.isEmpty()) return false;
        return x >= rect.left - slop && x <= rect.right + slop
                && y >= rect.top - slop && y <= rect.bottom + slop;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int st = engine.getState();
        if (st == GameEngine.STATE_PLAYING) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W) {
                engine.queueDirection(GameEngine.DIR_UP);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_S) {
                engine.queueDirection(GameEngine.DIR_DOWN);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_A) {
                engine.queueDirection(GameEngine.DIR_LEFT);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_D) {
                engine.queueDirection(GameEngine.DIR_RIGHT);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_P) {
                engine.pause();
                overlayAlpha = 0f;
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (st == GameEngine.STATE_PAUSED) {
                engine.resume();
                return true;
            } else if (st == GameEngine.STATE_MENU || st == GameEngine.STATE_GAME_OVER) {
                engine.startGame();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
