package com.snakestudio.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-Java Snake game engine (zero Android dependencies for full JVM testability).
 * Supports 3 modes (Classic, Portal/Wrap, Obstacles with BFS reachability verification),
 * 3 speeds with progressive acceleration, 2-step turn queue, golden bonus fruit, and combos.
 */
public final class GameEngine {
    public static final int COLS = 17;
    public static final int ROWS = 19;

    public static final int MODE_CLASSIC = 0;
    public static final int MODE_PORTAL = 1;
    public static final int MODE_OBSTACLES = 2;

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

    public static final float BONUS_DURATION_SEC = 7.5f;
    public static final float COMBO_WINDOW_SEC = 4.2f;

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
        void onEatFood(int cellX, int cellY, int pointsAwarded, int comboCount);
        void onEatBonus(int cellX, int cellY, int pointsAwarded);
        void onTurn();
        void onGameOver(boolean won, boolean newBest);
    }

    private final Random random;
    private final List<Cell> snake = new ArrayList<Cell>(COLS * ROWS);
    private final List<Cell> prevSnake = new ArrayList<Cell>(COLS * ROWS);
    private final List<Cell> obstacles = new ArrayList<Cell>(24);
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

    private int score = 0;
    private int applesEaten = 0;
    private int comboCount = 0;
    private float comboTimer = 0f;
    private boolean wonBoard = false;
    private boolean newBestThisRun = false;

    private final int[] bestScores = new int[3];
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
    public boolean isNewBestThisRun() { return newBestThisRun; }

    public List<Cell> getSnake() { return snake; }
    public List<Cell> getPrevSnake() { return prevSnake; }
    public List<Cell> getObstacles() { return obstacles; }
    public Cell getFood() { return food; }
    public Cell getBonusFood() { return bonusFood; }
    public float getBonusProgress() {
        if (bonusFood == null || bonusTimeLeft <= 0f) return 0f;
        return Math.min(1f, bonusTimeLeft / BONUS_DURATION_SEC);
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
            baseMs = 105f;
            minMs = 58f;
            stepMs = 1.3f;
        } else {
            baseMs = 142f;
            minMs = 76f;
            stepMs = 1.6f;
        }
        float ms = Math.max(minMs, baseMs - applesEaten * stepMs);
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
        if (newMode < MODE_CLASSIC || newMode > MODE_OBSTACLES) return;
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
        // Start with 4 segments heading right in center
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
        wonBoard = false;
        newBestThisRun = false;
        tickAccumulator = 0f;

        if (mode == MODE_OBSTACLES) {
            generateSafeObstacles(cx, cy);
        }

        food = spawnFreeCell(null);
    }

    private void generateSafeObstacles(int headX, int headY) {
        // Place symmetric, attractive, guaranteed-reachable obstacle clusters
        int targetCount = 12;
        int attempts = 0;
        while (obstacles.size() < targetCount && attempts < 250) {
            attempts++;
            int ox = 1 + random.nextInt(COLS - 2);
            int oy = 1 + random.nextInt(ROWS - 2);

            // Keep wide safe spawn zone around initial snake and 5 cells ahead of head
            if (oy >= headY - 2 && oy <= headY + 2 && ox >= headX - 5 && ox <= headX + 6) {
                continue;
            }
            if (obstacleGrid[ox][oy] || isOccupiedBySnake(ox, oy, false)) {
                continue;
            }
            // Tentatively place and verify full connectivity + no dead-end pockets
            obstacleGrid[ox][oy] = true;
            if (isBoardFullyReachable(headX, headY) && !createsDeadEnd(ox, oy)) {
                obstacles.add(new Cell(ox, oy));
            } else {
                obstacleGrid[ox][oy] = false;
            }
        }
    }

    private boolean createsDeadEnd(int ox, int oy) {
        // Check neighbors of placed obstacle to ensure no free cell has <= 1 open exit
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

    /**
     * Enqueues a player turn safely using a 2-slot turn buffer.
     * Prevents 180-degree self-reversal even when tapping two directions quickly within 1 tick.
     */
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

        tickAccumulator += dtSec;
        float interval = getTickIntervalSec();
        while (tickAccumulator >= interval && state == STATE_PLAYING) {
            tickAccumulator -= interval;
            stepTick();
            interval = getTickIntervalSec();
        }
    }

    /**
     * Executes a single discrete grid step. Exposed package-public for deterministic unit tests.
     */
    public void stepTick() {
        if (state != STATE_PLAYING) return;

        // Pop next queued turn if available
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
        int nx = head.x;
        int ny = head.y;
        if (currentDir == DIR_UP) ny--;
        else if (currentDir == DIR_DOWN) ny++;
        else if (currentDir == DIR_LEFT) nx--;
        else if (currentDir == DIR_RIGHT) nx++;

        // Wall check / Portal wrap
        if (mode == MODE_PORTAL) {
            if (nx < 0) nx = COLS - 1;
            else if (nx >= COLS) nx = 0;
            if (ny < 0) ny = ROWS - 1;
            else if (ny >= ROWS) ny = 0;
        } else {
            if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) {
                triggerGameOver(false);
                return;
            }
        }

        // Obstacle collision
        if (obstacleGrid[nx][ny]) {
            triggerGameOver(false);
            return;
        }

        boolean willEatFood = (food != null && food.same(nx, ny));
        boolean willEatBonus = (bonusFood != null && bonusFood.same(nx, ny));
        boolean willGrow = willEatFood || willEatBonus;

        // Self collision (note: moving onto tail cell is valid if NOT growing on this tick!)
        if (isOccupiedBySnake(nx, ny, !willGrow)) {
            triggerGameOver(false);
            return;
        }

        // Save previous positions for smooth interpolation
        copySnakeToPrev();

        // Advance snake
        snake.add(0, new Cell(nx, ny));
        if (!willGrow) {
            snake.remove(snake.size() - 1);
        } else {
            // Keep prevSnake same length by duplicating old tail so new tail interpolates smoothly
            if (!prevSnake.isEmpty()) {
                Cell lastPrev = prevSnake.get(prevSnake.size() - 1);
                prevSnake.add(new Cell(lastPrev.x, lastPrev.y));
            }
        }

        if (willEatFood) {
            applesEaten++;
            if (comboTimer > 0f) {
                comboCount = Math.min(5, comboCount + 1);
            } else {
                comboCount = 1;
            }
            comboTimer = COMBO_WINDOW_SEC;

            int pts = 10 + (comboCount >= 3 ? 10 : (comboCount == 2 ? 5 : 0));
            addScore(pts);

            if (listener != null) {
                listener.onEatFood(nx, ny, pts, comboCount);
            }

            // Spawn golden bonus every 5 apples if none active
            if (applesEaten % 5 == 0 && bonusFood == null) {
                bonusFood = spawnFreeCell(food);
                if (bonusFood != null) {
                    bonusTimeLeft = BONUS_DURATION_SEC;
                }
            }

            food = spawnFreeCell(bonusFood);
            if (food == null) {
                // Player filled the entire board!
                addScore(500);
                triggerGameOver(true);
            }
        } else if (willEatBonus) {
            bonusFood = null;
            bonusTimeLeft = 0f;
            int pts = 30;
            addScore(pts);
            if (listener != null) {
                listener.onEatBonus(nx, ny, pts);
            }
        }
    }

    private void addScore(int delta) {
        score += delta;
        if (score > bestScores[mode]) {
            bestScores[mode] = score;
            newBestThisRun = true;
        }
    }

    private void triggerGameOver(boolean won) {
        wonBoard = won;
        state = STATE_GAME_OVER;
        tickAccumulator = 0f;
        if (listener != null) {
            listener.onGameOver(won, newBestThisRun);
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

    private Cell spawnFreeCell(Cell avoidExtra) {
        List<Cell> free = new ArrayList<Cell>(COLS * ROWS);
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                if (obstacleGrid[x][y]) continue;
                if (avoidExtra != null && avoidExtra.same(x, y)) continue;
                if (isOccupiedBySnake(x, y, false)) continue;
                free.add(new Cell(x, y));
            }
        }
        if (free.isEmpty()) return null;
        return free.get(random.nextInt(free.size()));
    }

    // Package helper for testing food placement deterministically
    void setFoodForTest(int x, int y) {
        this.food = new Cell(x, y);
    }

    void setBonusFoodForTest(int x, int y, float durationSec) {
        this.bonusFood = new Cell(x, y);
        this.bonusTimeLeft = durationSec;
    }
}
