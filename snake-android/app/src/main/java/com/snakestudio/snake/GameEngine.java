package com.snakestudio.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-Java Snake v2 game engine (zero Android dependencies for full JVM testability).
 * Supports 6 game modes (Classic, No-Walls, Obstacles, Maze, Wormhole, Time Blitz),
 * 3 speed levels, 2-step turn queue, golden bonus stars, combos, and 3 power-up orbs
 * (Shield, Slow-Mo, 2x Score).
 */
public final class GameEngine {
    public static final int COLS = 17;
    public static final int ROWS = 19;

    public static final int MODE_CLASSIC = 0;
    public static final int MODE_PORTAL = 1;
    public static final int MODE_OBSTACLES = 2;
    public static final int MODE_MAZE = 3;
    public static final int MODE_WORMHOLE = 4;
    public static final int MODE_BLITZ = 5;
    public static final int MODE_COUNT = 6;

    public static final int SPEED_SLOW = 0;
    public static final int SPEED_NORMAL = 1;
    public static final int SPEED_FAST = 2;

    public static final int STATE_MENU = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_GAME_OVER = 3;

    public static final int DIR_UP = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_DOWN = 2;
    public static final int DIR_LEFT = 3;

    public static final int POWER_NONE = 0;
    public static final int POWER_SHIELD = 1;
    public static final int POWER_SLOW = 2;
    public static final int POWER_DOUBLE = 3;

    public static final float BONUS_DURATION_SEC = 7.5f;
    public static final float POWERUP_SPAWN_LIFETIME_SEC = 9.0f;
    public static final float SLOW_EFFECT_SEC = 6.5f;
    public static final float DOUBLE_EFFECT_SEC = 8.5f;
    public static final float COMBO_WINDOW_SEC = 4.2f;
    public static final float BLITZ_INITIAL_SEC = 60.0f;
    public static final float BLITZ_MAX_SEC = 99.0f;

    public static final class Cell {
        public final int x;
        public final int y;

        public Cell(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public boolean same(int ox, int oy) {
            return this.x == ox && this.y == oy;
        }

        public boolean same(Cell o) {
            return o != null && this.x == o.x && this.y == o.y;
        }
    }

    public interface Listener {
        void onEatFood(int cellX, int cellY, int pointsAwarded, int comboCount, boolean doubled);
        void onEatBonus(int cellX, int cellY, int pointsAwarded, boolean doubled);
        void onCollectPowerUp(int cellX, int cellY, int powerType);
        void onShieldSave(int cellX, int cellY);
        void onPortalWarp(int fromX, int fromY, int toX, int toY);
        void onTurn();
        void onGameOver(boolean won, boolean timeUp, boolean newBest);
    }

    private final Random random;
    private final List<Cell> snake = new ArrayList<Cell>(COLS * ROWS);
    private final List<Cell> prevSnake = new ArrayList<Cell>(COLS * ROWS);
    private final List<Cell> obstacles = new ArrayList<Cell>(64);
    private final boolean[][] obstacleGrid = new boolean[COLS][ROWS];
    private final int[] turnQueue = new int[2];
    private int turnQueueCount = 0;

    private int state = STATE_MENU;
    private int mode = MODE_CLASSIC;
    private int speedLevel = SPEED_NORMAL;
    private int currentDir = DIR_RIGHT;

    private Cell food;
    private Cell bonusFood;
    private float bonusTimeLeft = 0f;

    // Power-up pickup on board
    private Cell powerUpCell;
    private int powerUpType = POWER_NONE;
    private float powerUpBoardTimeLeft = 0f;

    // Active player power-up effects
    private boolean shieldActive = false;
    private float slowTimeLeft = 0f;
    private float doubleTimeLeft = 0f;

    // Wormhole twin portals (MODE_WORMHOLE)
    private Cell portalA;
    private Cell portalB;
    private int portalWarpCount = 0;
    private boolean pendingPortalRelocate = false;

    // Time Blitz mode countdown (MODE_BLITZ)
    private float blitzTimeLeft = 0f;

    private int score = 0;
    private int applesEaten = 0;
    private int comboCount = 0;
    private float comboTimer = 0f;
    private boolean wonBoard = false;
    private boolean timeExpired = false;
    private boolean newBestThisRun = false;

    private final int[] bestScores = new int[MODE_COUNT];
    private float tickAccumulator = 0f;
    private Listener listener;

    public GameEngine() {
        this(new Random());
    }

    public GameEngine(Random rng) {
        this.random = rng;
        setupPreviewBoard();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setBestScore(int gameMode, int value) {
        if (gameMode >= 0 && gameMode < bestScores.length && value >= 0) {
            bestScores[gameMode] = value;
        }
    }

    public int getBestScore(int gameMode) {
        if (gameMode < 0 || gameMode >= bestScores.length) return 0;
        return bestScores[gameMode];
    }

    public int getCurrentBestScore() {
        return bestScores[mode];
    }

    public int getState() { return state; }
    public int getMode() { return mode; }
    public int getSpeedLevel() { return speedLevel; }
    public int getCurrentDir() { return currentDir; }
    public int getScore() { return score; }
    public int getApplesEaten() { return applesEaten; }
    public int getComboCount() { return comboCount; }
    public boolean isWonBoard() { return wonBoard; }
    public boolean isTimeExpired() { return timeExpired; }
    public boolean isNewBestThisRun() { return newBestThisRun; }

    public boolean isWrapWalls() {
        return mode == MODE_PORTAL
                || mode == MODE_MAZE
                || mode == MODE_WORMHOLE
                || mode == MODE_BLITZ;
    }

    public List<Cell> getSnake() { return snake; }
    public List<Cell> getPrevSnake() { return prevSnake; }
    public List<Cell> getObstacles() { return obstacles; }
    public Cell getFood() { return food; }
    public Cell getBonusFood() { return bonusFood; }
    public Cell getPowerUpCell() { return powerUpCell; }
    public int getPowerUpType() { return powerUpType; }
    public Cell getPortalA() { return portalA; }
    public Cell getPortalB() { return portalB; }

    public boolean isShieldActive() { return shieldActive; }
    public float getSlowTimeLeft() { return slowTimeLeft; }
    public float getDoubleTimeLeft() { return doubleTimeLeft; }
    public float getBlitzTimeLeft() { return blitzTimeLeft; }

    public float getBonusProgress() {
        if (bonusFood == null || bonusTimeLeft <= 0f) return 0f;
        return Math.min(1f, bonusTimeLeft / BONUS_DURATION_SEC);
    }

    public float getPowerUpBoardProgress() {
        if (powerUpCell == null || powerUpBoardTimeLeft <= 0f) return 0f;
        return Math.min(1f, powerUpBoardTimeLeft / POWERUP_SPAWN_LIFETIME_SEC);
    }

    public float getTickIntervalSec() {
        float baseMs;
        float minMs;
        float stepMs;
        if (speedLevel == SPEED_SLOW) {
            baseMs = 185f;
            minMs = 105f;
            stepMs = 1.8f;
        } else if (speedLevel == SPEED_FAST) {
            baseMs = 104f;
            minMs = 58f;
            stepMs = 1.3f;
        } else {
            baseMs = 140f;
            minMs = 74f;
            stepMs = 1.6f;
        }
        float ms = Math.max(minMs, baseMs - applesEaten * stepMs);
        if (slowTimeLeft > 0f) {
            ms *= 1.48f;
        }
        return ms / 1000f;
    }

    public float getMoveProgress() {
        if (state != STATE_PLAYING) return 1f;
        float interval = getTickIntervalSec();
        if (interval <= 0f) return 1f;
        float p = tickAccumulator / interval;
        if (p < 0f) return 0f;
        if (p > 1f) return 1f;
        return p;
    }

    public void setMode(int newMode) {
        if (newMode < 0 || newMode >= MODE_COUNT) return;
        this.mode = newMode;
        if (state == STATE_MENU) {
            setupPreviewBoard();
        }
    }

    public void setSpeedLevel(int newSpeed) {
        if (newSpeed < SPEED_SLOW || newSpeed > SPEED_FAST) return;
        this.speedLevel = newSpeed;
    }

    public void goToMenu() {
        state = STATE_MENU;
        setupPreviewBoard();
    }

    public void pause() {
        if (state == STATE_PLAYING) {
            state = STATE_PAUSED;
        }
    }

    public void resume() {
        if (state == STATE_PAUSED) {
            state = STATE_PLAYING;
        }
    }

    public void startGame() {
        resetBoard();
        state = STATE_PLAYING;
    }

    private void setupPreviewBoard() {
        resetBoard();
        state = STATE_MENU;
    }

    private void resetBoard() {
        snake.clear();
        prevSnake.clear();
        obstacles.clear();
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                obstacleGrid[x][y] = false;
            }
        }

        int cx = COLS / 2;
        int cy = ROWS / 2;
        snake.add(new Cell(cx, cy));
        snake.add(new Cell(cx - 1, cy));
        snake.add(new Cell(cx - 2, cy));
        snake.add(new Cell(cx - 3, cy));
        copySnakeToPrev();

        currentDir = DIR_RIGHT;
        turnQueueCount = 0;
        score = 0;
        applesEaten = 0;
        comboCount = 0;
        comboTimer = 0f;
        bonusFood = null;
        bonusTimeLeft = 0f;
        powerUpCell = null;
        powerUpType = POWER_NONE;
        powerUpBoardTimeLeft = 0f;
        shieldActive = false;
        slowTimeLeft = 0f;
        doubleTimeLeft = 0f;
        portalA = null;
        portalB = null;
        portalWarpCount = 0;
        pendingPortalRelocate = false;
        blitzTimeLeft = (mode == MODE_BLITZ) ? BLITZ_INITIAL_SEC : 0f;
        wonBoard = false;
        timeExpired = false;
        newBestThisRun = false;
        tickAccumulator = 0f;

        if (mode == MODE_OBSTACLES) {
            generateSafeObstacles(cx, cy);
        } else if (mode == MODE_MAZE) {
            generateMazeObstacles();
        } else if (mode == MODE_WORMHOLE) {
            spawnPortalPair();
        }

        food = spawnFreeCell();
    }

    private void addObstacle(int x, int y) {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return;
        if (!obstacleGrid[x][y]) {
            obstacleGrid[x][y] = true;
            obstacles.add(new Cell(x, y));
        }
    }

    private void generateMazeObstacles() {
        // Classic symmetric arcade labyrinth with 4 generous 5-cell wrap gates
        for (int x = 0; x <= 5; x++) {
            addObstacle(x, 0);
            addObstacle(x, ROWS - 1);
        }
        for (int x = 11; x < COLS; x++) {
            addObstacle(x, 0);
            addObstacle(x, ROWS - 1);
        }
        for (int y = 1; y <= 6; y++) {
            addObstacle(0, y);
            addObstacle(COLS - 1, y);
        }
        for (int y = 12; y <= ROWS - 2; y++) {
            addObstacle(0, y);
            addObstacle(COLS - 1, y);
        }
        // 4 symmetric interior crystal bars (leaves center snake corridor & cross avenues wide open)
        for (int x = 4; x <= 6; x++) {
            addObstacle(x, 4);
            addObstacle(x, ROWS - 5);
        }
        for (int x = 10; x <= 12; x++) {
            addObstacle(x, 4);
            addObstacle(x, ROWS - 5);
        }
    }

    private void generateSafeObstacles(int headX, int headY) {
        int targetCount = 12;
        int attempts = 0;
        while (obstacles.size() < targetCount && attempts < 260) {
            attempts++;
            int ox = 1 + random.nextInt(COLS - 2);
            int oy = 1 + random.nextInt(ROWS - 2);
            if (oy >= headY - 2 && oy <= headY + 2 && ox >= headX - 5 && ox <= headX + 6) {
                continue;
            }
            if (obstacleGrid[ox][oy] || isOccupiedBySnake(ox, oy, false)) {
                continue;
            }
            obstacleGrid[ox][oy] = true;
            if (isBoardFullyReachable(headX, headY) && !createsDeadEnd(ox, oy)) {
                obstacles.add(new Cell(ox, oy));
            } else {
                obstacleGrid[ox][oy] = false;
            }
        }
    }

    private void spawnPortalPair() {
        portalA = null;
        portalB = null;
        List<Cell> candidates = new ArrayList<Cell>();
        Cell head = snake.isEmpty() ? new Cell(COLS / 2, ROWS / 2) : snake.get(0);
        for (int x = 2; x < COLS - 2; x++) {
            for (int y = 2; y < ROWS - 2; y++) {
                if (obstacleGrid[x][y]) continue;
                if (isOccupiedBySnake(x, y, false)) continue;
                if (food != null && food.same(x, y)) continue;
                if (bonusFood != null && bonusFood.same(x, y)) continue;
                if (powerUpCell != null && powerUpCell.same(x, y)) continue;
                // Keep away from immediate head forward cell
                if (Math.abs(x - head.x) <= 2 && Math.abs(y - head.y) <= 2) continue;
                candidates.add(new Cell(x, y));
            }
        }
        if (candidates.size() < 2) return;
        for (int tries = 0; tries < 60; tries++) {
            Cell a = candidates.get(random.nextInt(candidates.size()));
            Cell b = candidates.get(random.nextInt(candidates.size()));
            int dist = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
            if (dist >= 9) {
                portalA = a;
                portalB = b;
                return;
            }
        }
        portalA = candidates.get(0);
        portalB = candidates.get(candidates.size() - 1);
    }

    private boolean createsDeadEnd(int ox, int oy) {
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};
        for (int i = 0; i < 4; i++) {
            int nx = ox + dx[i];
            int ny = oy + dy[i];
            if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) continue;
            if (obstacleGrid[nx][ny]) continue;
            int exits = 0;
            for (int j = 0; j < 4; j++) {
                int ax = nx + dx[j];
                int ay = ny + dy[j];
                if (ax >= 0 && ax < COLS && ay >= 0 && ay < ROWS && !obstacleGrid[ax][ay]) {
                    exits++;
                }
            }
            if (exits <= 1) return true;
        }
        return false;
    }

    private boolean isBoardFullyReachable(int startX, int startY) {
        boolean[][] visited = new boolean[COLS][ROWS];
        ArrayDeque<Cell> q = new ArrayDeque<Cell>();
        visited[startX][startY] = true;
        q.add(new Cell(startX, startY));
        int visitedCount = 1;
        int totalFree = COLS * ROWS;
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                if (obstacleGrid[x][y]) totalFree--;
            }
        }
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};
        while (!q.isEmpty()) {
            Cell c = q.poll();
            for (int i = 0; i < 4; i++) {
                int nx = c.x + dx[i];
                int ny = c.y + dy[i];
                if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS
                        && !visited[nx][ny] && !obstacleGrid[nx][ny]) {
                    visited[nx][ny] = true;
                    visitedCount++;
                    q.add(new Cell(nx, ny));
                }
            }
        }
        return visitedCount == totalFree;
    }

    public static boolean isOpposite(int d1, int d2) {
        return (d1 == DIR_UP && d2 == DIR_DOWN)
                || (d1 == DIR_DOWN && d2 == DIR_UP)
                || (d1 == DIR_LEFT && d2 == DIR_RIGHT)
                || (d1 == DIR_RIGHT && d2 == DIR_LEFT);
    }

    public boolean queueDirection(int dir) {
        if (state != STATE_PLAYING) return false;
        if (dir < DIR_UP || dir > DIR_LEFT) return false;

        int referenceDir = (turnQueueCount > 0) ? turnQueue[turnQueueCount - 1] : currentDir;
        if (dir == referenceDir || isOpposite(dir, referenceDir)) {
            return false;
        }
        if (turnQueueCount < 2) {
            turnQueue[turnQueueCount++] = dir;
            if (listener != null) listener.onTurn();
            return true;
        }
        return false;
    }

    public void update(float dtSec) {
        if (state != STATE_PLAYING) return;
        if (dtSec < 0f) return;
        if (dtSec > 0.1f) dtSec = 0.1f;

        if (comboTimer > 0f) {
            comboTimer -= dtSec;
            if (comboTimer <= 0f) {
                comboTimer = 0f;
                comboCount = 0;
            }
        }

        if (bonusFood != null) {
            bonusTimeLeft -= dtSec;
            if (bonusTimeLeft <= 0f) {
                bonusFood = null;
                bonusTimeLeft = 0f;
            }
        }

        if (powerUpCell != null) {
            powerUpBoardTimeLeft -= dtSec;
            if (powerUpBoardTimeLeft <= 0f) {
                powerUpCell = null;
                powerUpType = POWER_NONE;
                powerUpBoardTimeLeft = 0f;
            }
        }

        if (slowTimeLeft > 0f) {
            slowTimeLeft = Math.max(0f, slowTimeLeft - dtSec);
        }
        if (doubleTimeLeft > 0f) {
            doubleTimeLeft = Math.max(0f, doubleTimeLeft - dtSec);
        }

        if (mode == MODE_BLITZ) {
            blitzTimeLeft -= dtSec;
            if (blitzTimeLeft <= 0f) {
                blitzTimeLeft = 0f;
                triggerGameOver(false, true);
                return;
            }
        }

        tickAccumulator += dtSec;
        float interval = getTickIntervalSec();
        while (tickAccumulator >= interval && state == STATE_PLAYING) {
            tickAccumulator -= interval;
            stepTick();
            interval = getTickIntervalSec();
        }
    }

    public void stepTick() {
        if (state != STATE_PLAYING) return;

        if (turnQueueCount > 0) {
            int nextDir = turnQueue[0];
            if (turnQueueCount == 2) {
                turnQueue[0] = turnQueue[1];
            }
            turnQueueCount--;
            if (!isOpposite(nextDir, currentDir)) {
                currentDir = nextDir;
            }
        }

        Cell head = snake.get(0);
        int[] next = computeNextCell(head.x, head.y, currentDir);
        int nx = next[0];
        int ny = next[1];
        boolean lethal = isLethalMove(nx, ny);

        // Shield Rescue: if about to crash while Shield is active, auto-steer to a safe perpendicular cell!
        if (lethal && shieldActive) {
            int escapeDir = findBestEscapeDirection(head.x, head.y, currentDir);
            if (escapeDir != -1) {
                shieldActive = false;
                currentDir = escapeDir;
                turnQueueCount = 0;
                next = computeNextCell(head.x, head.y, currentDir);
                nx = next[0];
                ny = next[1];
                lethal = false;
                if (listener != null) {
                    listener.onShieldSave(head.x, head.y);
                }
            }
        }

        if (lethal) {
            triggerGameOver(false, false);
            return;
        }

        // Wormhole teleportation check
        boolean warped = false;
        int warpFromX = nx, warpFromY = ny;
        if (mode == MODE_WORMHOLE && portalA != null && portalB != null) {
            if (portalA.same(nx, ny)) {
                nx = portalB.x;
                ny = portalB.y;
                warped = true;
            } else if (portalB.same(nx, ny)) {
                nx = portalA.x;
                ny = portalA.y;
                warped = true;
            }
            if (warped) {
                // Check destination not occupied by body (other than vacating tail)
                if (isOccupiedBySnake(nx, ny, true)) {
                    if (shieldActive) {
                        shieldActive = false;
                        if (listener != null) listener.onShieldSave(warpFromX, warpFromY);
                    } else {
                        triggerGameOver(false, false);
                        return;
                    }
                }
                portalWarpCount++;
                if (portalWarpCount % 3 == 0) {
                    pendingPortalRelocate = true;
                }
                if (listener != null) {
                    listener.onPortalWarp(warpFromX, warpFromY, nx, ny);
                }
            }
        }

        boolean willEatFood = (food != null && food.same(nx, ny));
        boolean willEatBonus = (bonusFood != null && bonusFood.same(nx, ny));
        boolean willEatPower = (powerUpCell != null && powerUpCell.same(nx, ny));
        boolean willGrow = willEatFood || willEatBonus;

        // Save previous positions for smooth interpolation
        copySnakeToPrev();

        // Advance snake
        snake.add(0, new Cell(nx, ny));
        if (!willGrow) {
            snake.remove(snake.size() - 1);
        } else if (!prevSnake.isEmpty()) {
            Cell lastPrev = prevSnake.get(prevSnake.size() - 1);
            prevSnake.add(new Cell(lastPrev.x, lastPrev.y));
        }

        // Safely relocate wormholes once snake has completely exited both portals
        if (pendingPortalRelocate && portalA != null && portalB != null
                && !isOccupiedBySnake(portalA.x, portalA.y, false)
                && !isOccupiedBySnake(portalB.x, portalB.y, false)) {
            pendingPortalRelocate = false;
            spawnPortalPair();
        }

        boolean doubled = (doubleTimeLeft > 0f);
        int mult = doubled ? 2 : 1;

        if (willEatFood) {
            applesEaten++;
            if (comboTimer > 0f) {
                comboCount = Math.min(5, comboCount + 1);
            } else {
                comboCount = 1;
            }
            comboTimer = COMBO_WINDOW_SEC;

            int basePts = 10 + (comboCount >= 3 ? 10 : (comboCount == 2 ? 5 : 0));
            if (mode == MODE_MAZE || mode == MODE_OBSTACLES) {
                basePts += 5; // Extra challenge reward!
            }
            int pts = basePts * mult;
            addScore(pts);

            if (mode == MODE_BLITZ) {
                blitzTimeLeft = Math.min(BLITZ_MAX_SEC, blitzTimeLeft + 2.0f);
            }

            if (listener != null) {
                listener.onEatFood(nx, ny, pts, comboCount, doubled);
            }

            // Golden bonus star every 5 apples
            if (applesEaten % 5 == 0 && bonusFood == null) {
                bonusFood = spawnFreeCell();
                if (bonusFood != null) {
                    bonusTimeLeft = BONUS_DURATION_SEC;
                }
            }

            // Power-up orb spawning frequency depends on mode
            int powerInterval = (mode == MODE_BLITZ) ? 3
                    : ((mode == MODE_OBSTACLES || mode == MODE_MAZE || mode == MODE_WORMHOLE) ? 4 : 6);
            if (applesEaten % powerInterval == 0 && powerUpCell == null) {
                spawnPowerUp();
            }

            food = spawnFreeCell();
            if (food == null) {
                addScore(500 * mult);
                triggerGameOver(true, false);
            }
        } else if (willEatBonus) {
            bonusFood = null;
            bonusTimeLeft = 0f;
            int pts = 30 * mult;
            addScore(pts);
            if (mode == MODE_BLITZ) {
                blitzTimeLeft = Math.min(BLITZ_MAX_SEC, blitzTimeLeft + 5.0f);
            }
            if (listener != null) {
                listener.onEatBonus(nx, ny, pts, doubled);
            }
        } else if (willEatPower) {
            int picked = powerUpType;
            powerUpCell = null;
            powerUpType = POWER_NONE;
            powerUpBoardTimeLeft = 0f;
            applyPowerUp(picked);
            if (listener != null) {
                listener.onCollectPowerUp(nx, ny, picked);
            }
        }
    }

    private int[] computeNextCell(int x, int y, int dir) {
        int nx = x;
        int ny = y;
        if (dir == DIR_UP) ny--;
        else if (dir == DIR_DOWN) ny++;
        else if (dir == DIR_LEFT) nx--;
        else if (dir == DIR_RIGHT) nx++;

        if (isWrapWalls()) {
            if (nx < 0) nx = COLS - 1;
            else if (nx >= COLS) nx = 0;
            if (ny < 0) ny = ROWS - 1;
            else if (ny >= ROWS) ny = 0;
        }
        return new int[]{nx, ny};
    }

    private boolean isLethalMove(int nx, int ny) {
        if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) {
            return true;
        }
        if (obstacleGrid[nx][ny]) {
            return true;
        }
        boolean willGrow = (food != null && food.same(nx, ny))
                || (bonusFood != null && bonusFood.same(nx, ny));
        return isOccupiedBySnake(nx, ny, !willGrow);
    }

    private int findBestEscapeDirection(int hx, int hy, int dir) {
        int leftDir = (dir + 3) % 4;
        int rightDir = (dir + 1) % 4;
        int[] cand = new int[]{leftDir, rightDir};
        int bestDir = -1;
        int bestClearance = -1;
        for (int i = 0; i < cand.length; i++) {
            int d = cand[i];
            int[] p = computeNextCell(hx, hy, d);
            if (!isLethalMove(p[0], p[1])) {
                int clearance = countStraightClearance(p[0], p[1], d);
                if (clearance > bestClearance) {
                    bestClearance = clearance;
                    bestDir = d;
                }
            }
        }
        return bestDir;
    }

    private int countStraightClearance(int sx, int sy, int dir) {
        int x = sx, y = sy, count = 1;
        for (int step = 0; step < 6; step++) {
            int[] p = computeNextCell(x, y, dir);
            if (isLethalMove(p[0], p[1])) break;
            x = p[0];
            y = p[1];
            count++;
        }
        return count;
    }

    private void spawnPowerUp() {
        Cell c = spawnFreeCell();
        if (c == null) return;
        int roll = random.nextInt(3);
        if (roll == 0 && !shieldActive) {
            powerUpType = POWER_SHIELD;
        } else if (roll == 1) {
            powerUpType = POWER_SLOW;
        } else {
            powerUpType = POWER_DOUBLE;
        }
        powerUpCell = c;
        powerUpBoardTimeLeft = POWERUP_SPAWN_LIFETIME_SEC;
    }

    private void applyPowerUp(int type) {
        if (type == POWER_SHIELD) {
            shieldActive = true;
        } else if (type == POWER_SLOW) {
            slowTimeLeft = SLOW_EFFECT_SEC;
        } else if (type == POWER_DOUBLE) {
            doubleTimeLeft = DOUBLE_EFFECT_SEC;
        }
    }

    private void addScore(int delta) {
        score += delta;
        if (score > bestScores[mode]) {
            bestScores[mode] = score;
            newBestThisRun = true;
        }
    }

    private void triggerGameOver(boolean won, boolean timeUp) {
        wonBoard = won;
        timeExpired = timeUp;
        state = STATE_GAME_OVER;
        tickAccumulator = 0f;
        if (listener != null) {
            listener.onGameOver(won, timeUp, newBestThisRun);
        }
    }

    private void copySnakeToPrev() {
        prevSnake.clear();
        for (int i = 0; i < snake.size(); i++) {
            Cell c = snake.get(i);
            prevSnake.add(new Cell(c.x, c.y));
        }
    }

    private boolean isOccupiedBySnake(int x, int y, boolean ignoreTail) {
        int limit = ignoreTail ? (snake.size() - 1) : snake.size();
        for (int i = 0; i < limit; i++) {
            if (snake.get(i).same(x, y)) return true;
        }
        return false;
    }

    private Cell spawnFreeCell() {
        List<Cell> free = new ArrayList<Cell>(COLS * ROWS);
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                if (obstacleGrid[x][y]) continue;
                if (food != null && food.same(x, y)) continue;
                if (bonusFood != null && bonusFood.same(x, y)) continue;
                if (powerUpCell != null && powerUpCell.same(x, y)) continue;
                if (portalA != null && portalA.same(x, y)) continue;
                if (portalB != null && portalB.same(x, y)) continue;
                if (isOccupiedBySnake(x, y, false)) continue;
                free.add(new Cell(x, y));
            }
        }
        if (free.isEmpty()) return null;
        return free.get(random.nextInt(free.size()));
    }

    // Package test helpers
    void setFoodForTest(int x, int y) {
        this.food = new Cell(x, y);
    }

    void setBonusFoodForTest(int x, int y, float durationSec) {
        this.bonusFood = new Cell(x, y);
        this.bonusTimeLeft = durationSec;
    }

    void setPowerUpForTest(int x, int y, int type) {
        this.powerUpCell = new Cell(x, y);
        this.powerUpType = type;
        this.powerUpBoardTimeLeft = POWERUP_SPAWN_LIFETIME_SEC;
    }

    void setPortalsForTest(int ax, int ay, int bx, int by) {
        this.portalA = new Cell(ax, ay);
        this.portalB = new Cell(bx, by);
    }
}
