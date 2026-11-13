package com.snakestudio.snake;

import java.util.List;
import java.util.Random;

public final class GameEngineTest {
    private static int passed = 0;

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("FAILED: " + msg);
        }
        passed++;
    }

    public static void main(String[] args) {
        testInitialAndClassicWallCrash();
        testPortalWrapAllEdges();
        testTwoSlotTurnQueuePreventsSuicide();
        testEatingGrowthComboAndGoldenBonus();
        testSelfCollision();
        testObstaclesSafetyAcrossSeeds();
        testPauseResumeAndBestScores();
        testStringsAndPersianDigits();
        System.out.println("ALL " + passed + " ENGINE ASSERTIONS PASSED!");
    }

    private static void testInitialAndClassicWallCrash() {
        GameEngine e = new GameEngine(new Random(42));
        check(e.getState() == GameEngine.STATE_MENU, "starts in MENU");
        e.setMode(GameEngine.MODE_CLASSIC);
        e.startGame();
        check(e.getState() == GameEngine.STATE_PLAYING, "transitions to PLAYING");
        check(e.getSnake().size() == 4, "starts with length 4");
        check(e.getFood() != null, "food spawned");

        // Move straight right until hitting right wall
        int steps = 0;
        while (e.getState() == GameEngine.STATE_PLAYING && steps < 30) {
            // Move food out of the way
            e.setFoodForTest(0, 0);
            e.stepTick();
            steps++;
        }
        check(e.getState() == GameEngine.STATE_GAME_OVER, "classic hits right wall -> GAME_OVER");
        check(!e.isWonBoard(), "wall crash is not a win");
    }

    private static void testPortalWrapAllEdges() {
        GameEngine e = new GameEngine(new Random(7));
        e.setMode(GameEngine.MODE_PORTAL);
        e.startGame();
        e.setFoodForTest(0, 0);

        // Travel far right across wrap boundary multiple times
        for (int i = 0; i < GameEngine.COLS + 5; i++) {
            e.stepTick();
            check(e.getState() == GameEngine.STATE_PLAYING, "portal survives right wrap step " + i);
            GameEngine.Cell h = e.getSnake().get(0);
            check(h.x >= 0 && h.x < GameEngine.COLS, "head x in bounds after wrap");
        }

        // Turn UP and travel across top wrap boundary
        check(e.queueDirection(GameEngine.DIR_UP), "turn UP queued");
        for (int i = 0; i < GameEngine.ROWS + 5; i++) {
            e.stepTick();
            check(e.getState() == GameEngine.STATE_PLAYING, "portal survives top wrap step " + i);
            GameEngine.Cell h = e.getSnake().get(0);
            check(h.y >= 0 && h.y < GameEngine.ROWS, "head y in bounds after wrap");
        }
    }

    private static void testTwoSlotTurnQueuePreventsSuicide() {
        GameEngine e = new GameEngine(new Random(11));
        e.setMode(GameEngine.MODE_CLASSIC);
        e.startGame();
        e.setFoodForTest(0, 0);

        // Initially heading RIGHT: direct LEFT must be rejected
        check(!e.queueDirection(GameEngine.DIR_LEFT), "direct 180 reversal rejected");

        // Rapid double-tap within 1 tick: UP then LEFT
        check(e.queueDirection(GameEngine.DIR_UP), "first turn UP accepted");
        check(e.queueDirection(GameEngine.DIR_LEFT), "second turn LEFT accepted in buffer");
        // Third turn while buffer full is ignored cleanly
        check(!e.queueDirection(GameEngine.DIR_DOWN), "third turn rejected when buffer full");

        int startX = e.getSnake().get(0).x;
        int startY = e.getSnake().get(0).y;

        e.stepTick();
        check(e.getState() == GameEngine.STATE_PLAYING, "alive after tick 1 (UP)");
        check(e.getCurrentDir() == GameEngine.DIR_UP, "dir is UP after tick 1");
        check(e.getSnake().get(0).same(startX, startY - 1), "moved UP by 1");

        e.stepTick();
        check(e.getState() == GameEngine.STATE_PLAYING, "alive after tick 2 (LEFT)");
        check(e.getCurrentDir() == GameEngine.DIR_LEFT, "dir is LEFT after tick 2");
        check(e.getSnake().get(0).same(startX - 1, startY - 1), "moved LEFT by 1 cleanly");
    }

    private static void testEatingGrowthComboAndGoldenBonus() {
        GameEngine e = new GameEngine(new Random(99));
        e.setMode(GameEngine.MODE_PORTAL);
        e.startGame();

        final int[] eatEvents = {0};
        final int[] bonusEvents = {0};
        e.setListener(new GameEngine.Listener() {
            @Override public void onEatFood(int x, int y, int pts, int combo) { eatEvents[0]++; }
            @Override public void onEatBonus(int x, int y, int pts) { bonusEvents[0]++; }
            @Override public void onTurn() {}
            @Override public void onGameOver(boolean won, boolean newBest) {}
        });

        // Eat 5 apples directly in front of snake
        for (int i = 0; i < 5; i++) {
            GameEngine.Cell h = e.getSnake().get(0);
            int fx = (h.x + 1) % GameEngine.COLS;
            e.setFoodForTest(fx, h.y);
            e.stepTick();
        }
        check(eatEvents[0] == 5, "5 eat events fired");
        check(e.getApplesEaten() == 5, "applesEaten == 5");
        check(e.getSnake().size() == 9, "snake grew from 4 to 9");
        // Score with combos: 10 + 15 + 20 + 20 + 20 = 85
        check(e.getScore() == 85, "combo score == 85, got " + e.getScore());
        check(e.getBonusFood() != null, "golden bonus spawned on 5th apple");

        // Place bonus right in front and eat it
        GameEngine.Cell h = e.getSnake().get(0);
        int bx = (h.x + 1) % GameEngine.COLS;
        e.setFoodForTest(0, 0);
        e.setBonusFoodForTest(bx, h.y, 5.0f);
        e.stepTick();
        check(bonusEvents[0] == 1, "bonus eat event fired");
        check(e.getBonusFood() == null, "bonus cleared after eating");
        check(e.getScore() == 115, "score increased by +30 to 115");

        // Test bonus timeout expiration
        e.setBonusFoodForTest(0, 1, 0.05f);
        e.update(0.06f);
        check(e.getBonusFood() == null, "bonus expires after timeout");
    }

    private static void testSelfCollision() {
        GameEngine e = new GameEngine(new Random(3));
        e.setMode(GameEngine.MODE_PORTAL);
        e.startGame();

        // Grow to length 6
        for (int i = 0; i < 2; i++) {
            GameEngine.Cell h = e.getSnake().get(0);
            e.setFoodForTest((h.x + 1) % GameEngine.COLS, h.y);
            e.stepTick();
        }
        e.setFoodForTest(0, 0);
        // Make tight U-turn into own body: UP -> LEFT -> DOWN
        e.queueDirection(GameEngine.DIR_UP);
        e.stepTick();
        e.queueDirection(GameEngine.DIR_LEFT);
        e.stepTick();
        e.queueDirection(GameEngine.DIR_DOWN);
        e.stepTick();
        check(e.getState() == GameEngine.STATE_GAME_OVER, "self collision triggers GAME_OVER");
    }

    private static void testObstaclesSafetyAcrossSeeds() {
        for (int seed = 1; seed <= 20; seed++) {
            GameEngine e = new GameEngine(new Random(seed * 131L));
            e.setMode(GameEngine.MODE_OBSTACLES);
            e.startGame();
            List<GameEngine.Cell> obs = e.getObstacles();
            check(obs.size() >= 8, "obstacles generated for seed " + seed);
            GameEngine.Cell head = e.getSnake().get(0);
            for (GameEngine.Cell o : obs) {
                // Ensure none sit in the 5-cell forward lane ahead of head
                boolean inCorridor = (o.y == head.y && o.x >= head.x - 4 && o.x <= head.x + 5);
                check(!inCorridor, "no obstacle in spawn corridor seed=" + seed);
                check(e.getFood() != null && !o.same(e.getFood()), "food not on obstacle");
            }
        }
    }

    private static void testPauseResumeAndBestScores() {
        GameEngine e = new GameEngine(new Random(5));
        e.setBestScore(GameEngine.MODE_CLASSIC, 40);
        e.setMode(GameEngine.MODE_CLASSIC);
        e.startGame();
        e.pause();
        check(e.getState() == GameEngine.STATE_PAUSED, "paused");
        int xBefore = e.getSnake().get(0).x;
        e.update(0.5f);
        check(e.getSnake().get(0).x == xBefore, "no movement while paused");
        e.resume();
        check(e.getState() == GameEngine.STATE_PLAYING, "resumed");
        check(e.getCurrentBestScore() == 40, "best score preserved");
    }

    private static void testStringsAndPersianDigits() {
        check("۱۲۳۰".equals(Strings.num(1230, true)), "Persian digits formatted properly");
        check("1230".equals(Strings.num(1230, false)), "English digits formatted properly");
        check(ThemePalette.byId(ThemePalette.THEME_RETRO) == ThemePalette.RETRO, "Retro theme lookup");
        check(ThemePalette.byId(ThemePalette.THEME_SUNSET) == ThemePalette.SUNSET, "Sunset theme lookup");
    }
}
