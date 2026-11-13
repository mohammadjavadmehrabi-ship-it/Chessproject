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
        testMazeModeGatesAndSafety();
        testWormholeTeleportation();
        testTimeBlitzCountdownAndTimeGain();
        testPowerUpsShieldSlowDouble();
        testPauseResumeAndBestScores();
        testStringsAndThemes();
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

        int steps = 0;
        while (e.getState() == GameEngine.STATE_PLAYING && steps < 30) {
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

        for (int i = 0; i < GameEngine.COLS + 5; i++) {
            e.stepTick();
            check(e.getState() == GameEngine.STATE_PLAYING, "portal survives right wrap step " + i);
            GameEngine.Cell h = e.getSnake().get(0);
            check(h.x >= 0 && h.x < GameEngine.COLS, "head x in bounds after wrap");
        }

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

        check(!e.queueDirection(GameEngine.DIR_LEFT), "direct 180 reversal rejected");
        check(e.queueDirection(GameEngine.DIR_UP), "first turn UP accepted");
        check(e.queueDirection(GameEngine.DIR_LEFT), "second turn LEFT accepted in buffer");
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
            @Override public void onEatFood(int x, int y, int pts, int combo, boolean dbl) { eatEvents[0]++; }
            @Override public void onEatBonus(int x, int y, int pts, boolean dbl) { bonusEvents[0]++; }
            @Override public void onCollectPowerUp(int x, int y, int t) {}
            @Override public void onShieldSave(int x, int y) {}
            @Override public void onPortalWarp(int fx, int fy, int tx, int ty) {}
            @Override public void onTurn() {}
            @Override public void onGameOver(boolean won, boolean timeUp, boolean newBest) {}
        });

        for (int i = 0; i < 5; i++) {
            GameEngine.Cell h = e.getSnake().get(0);
            int fx = (h.x + 1) % GameEngine.COLS;
            e.setFoodForTest(fx, h.y);
            e.stepTick();
        }
        check(eatEvents[0] == 5, "5 eat events fired");
        check(e.getApplesEaten() == 5, "applesEaten == 5");
        check(e.getSnake().size() == 9, "snake grew from 4 to 9");
        check(e.getScore() == 85, "combo score == 85, got " + e.getScore());
        check(e.getBonusFood() != null, "golden bonus spawned on 5th apple");

        GameEngine.Cell h = e.getSnake().get(0);
        int bx = (h.x + 1) % GameEngine.COLS;
        e.setFoodForTest(0, 0);
        e.setBonusFoodForTest(bx, h.y, 5.0f);
        e.stepTick();
        check(bonusEvents[0] == 1, "bonus eat event fired");
        check(e.getBonusFood() == null, "bonus cleared after eating");
        check(e.getScore() == 115, "score increased by +30 to 115");

        e.setBonusFoodForTest(0, 1, 0.05f);
        e.update(0.06f);
        check(e.getBonusFood() == null, "bonus expires after timeout");
    }

    private static void testSelfCollision() {
        GameEngine e = new GameEngine(new Random(3));
        e.setMode(GameEngine.MODE_PORTAL);
        e.startGame();

        for (int i = 0; i < 2; i++) {
            GameEngine.Cell h = e.getSnake().get(0);
            e.setFoodForTest((h.x + 1) % GameEngine.COLS, h.y);
            e.stepTick();
        }
        e.setFoodForTest(0, 0);
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
                boolean inCorridor = (o.y == head.y && o.x >= head.x - 4 && o.x <= head.x + 5);
                check(!inCorridor, "no obstacle in spawn corridor seed=" + seed);
                check(e.getFood() != null && !o.same(e.getFood()), "food not on obstacle");
            }
        }
    }

    private static void testMazeModeGatesAndSafety() {
        GameEngine e = new GameEngine(new Random(19));
        e.setMode(GameEngine.MODE_MAZE);
        e.startGame();
        check(e.getObstacles().size() > 30, "maze has structured wall barriers");
        e.setFoodForTest(2, 2);

        // Travel horizontally straight across center gate and wrap cleanly
        for (int i = 0; i < GameEngine.COLS + 2; i++) {
            e.stepTick();
            check(e.getState() == GameEngine.STATE_PLAYING, "maze center wrap gate open step " + i);
        }
    }

    private static void testWormholeTeleportation() {
        GameEngine e = new GameEngine(new Random(23));
        e.setMode(GameEngine.MODE_WORMHOLE);
        e.startGame();
        check(e.getPortalA() != null && e.getPortalB() != null, "wormhole portals spawned");

        final int[] warps = {0};
        e.setListener(new GameEngine.Listener() {
            @Override public void onEatFood(int x, int y, int pts, int combo, boolean dbl) {}
            @Override public void onEatBonus(int x, int y, int pts, boolean dbl) {}
            @Override public void onCollectPowerUp(int x, int y, int t) {}
            @Override public void onShieldSave(int x, int y) {}
            @Override public void onPortalWarp(int fx, int fy, int tx, int ty) { warps[0]++; }
            @Override public void onTurn() {}
            @Override public void onGameOver(boolean won, boolean timeUp, boolean newBest) {}
        });

        GameEngine.Cell h = e.getSnake().get(0);
        e.setPortalsForTest(h.x + 1, h.y, 2, 2);
        e.setFoodForTest(0, 0);
        e.stepTick();
        check(warps[0] == 1, "portal warp event fired");
        check(e.getSnake().get(0).same(2, 2), "head warped to portal B (2,2)");
        e.stepTick();
        check(e.getSnake().get(0).same(3, 2), "continued moving right out of portal B");
    }

    private static void testTimeBlitzCountdownAndTimeGain() {
        GameEngine e = new GameEngine(new Random(31));
        e.setMode(GameEngine.MODE_BLITZ);
        e.startGame();
        check(e.getBlitzTimeLeft() == GameEngine.BLITZ_INITIAL_SEC, "blitz starts at 60s");

        // Advance time by 5 seconds in small steps
        e.setFoodForTest(0, 0);
        for (int i = 0; i < 100; i++) {
            e.update(0.05f);
        }
        check(e.getBlitzTimeLeft() < 56f && e.getBlitzTimeLeft() > 54f, "blitz timer counts down");

        float beforeEat = e.getBlitzTimeLeft();
        GameEngine.Cell h = e.getSnake().get(0);
        e.setFoodForTest((h.x + 1) % GameEngine.COLS, h.y);
        e.stepTick();
        check(e.getBlitzTimeLeft() > beforeEat + 1.5f, "eating apple adds +2s in Blitz");

        // Drain remaining time to zero
        e.setFoodForTest(0, 0);
        for (int i = 0; i < 1400 && e.getState() == GameEngine.STATE_PLAYING; i++) {
            e.update(0.05f);
        }
        check(e.getState() == GameEngine.STATE_GAME_OVER, "blitz ends when timer hits 0");
        check(e.isTimeExpired(), "timeExpired flag set on blitz timeout");
    }

    private static void testPowerUpsShieldSlowDouble() {
        GameEngine e = new GameEngine(new Random(55));
        e.setMode(GameEngine.MODE_CLASSIC);
        e.startGame();

        final int[] saves = {0};
        e.setListener(new GameEngine.Listener() {
            @Override public void onEatFood(int x, int y, int pts, int combo, boolean dbl) {}
            @Override public void onEatBonus(int x, int y, int pts, boolean dbl) {}
            @Override public void onCollectPowerUp(int x, int y, int t) {}
            @Override public void onShieldSave(int x, int y) { saves[0]++; }
            @Override public void onPortalWarp(int fx, int fy, int tx, int ty) {}
            @Override public void onTurn() {}
            @Override public void onGameOver(boolean won, boolean timeUp, boolean newBest) {}
        });

        // 1. Collect Shield right in front
        GameEngine.Cell h = e.getSnake().get(0);
        e.setFoodForTest(0, 0);
        e.setPowerUpForTest(h.x + 1, h.y, GameEngine.POWER_SHIELD);
        e.stepTick();
        check(e.isShieldActive(), "shield activated");

        // Drive straight into right wall: Shield MUST rescue & steer perpendicular!
        int guard = 0;
        while (e.isShieldActive() && guard < 20) {
            e.stepTick();
            guard++;
        }
        check(saves[0] == 1, "shield saved snake from wall crash");
        check(e.getState() == GameEngine.STATE_PLAYING, "snake still alive after shield rescue");
        check(!e.isShieldActive(), "shield consumed after rescue");

        // 2. Collect Slow-Mo and verify tick interval increases
        float normalInterval = e.getTickIntervalSec();
        e.startGame();
        h = e.getSnake().get(0);
        e.setFoodForTest(0, 0);
        e.setPowerUpForTest(h.x + 1, h.y, GameEngine.POWER_SLOW);
        e.stepTick();
        check(e.getSlowTimeLeft() > 5f, "slow-mo active");
        check(e.getTickIntervalSec() > normalInterval * 1.3f, "slow-mo increases tick interval");

        // 3. Collect 2x Score and verify doubled points
        e.startGame();
        h = e.getSnake().get(0);
        e.setPowerUpForTest(h.x + 1, h.y, GameEngine.POWER_DOUBLE);
        e.setFoodForTest(h.x + 2, h.y);
        e.stepTick(); // collect 2x
        check(e.getDoubleTimeLeft() > 7f, "2x score active");
        e.stepTick(); // eat apple -> should be 20 instead of 10!
        check(e.getScore() == 20, "doubled apple score == 20, got " + e.getScore());
    }

    private static void testPauseResumeAndBestScores() {
        GameEngine e = new GameEngine(new Random(5));
        e.setBestScore(GameEngine.MODE_MAZE, 140);
        e.setMode(GameEngine.MODE_MAZE);
        e.startGame();
        e.pause();
        check(e.getState() == GameEngine.STATE_PAUSED, "paused");
        int xBefore = e.getSnake().get(0).x;
        e.update(0.5f);
        check(e.getSnake().get(0).x == xBefore, "no movement while paused");
        e.resume();
        check(e.getState() == GameEngine.STATE_PLAYING, "resumed");
        check(e.getCurrentBestScore() == 140, "maze best score preserved");
    }

    private static void testStringsAndThemes() {
        check("۱۲۳۰".equals(Strings.num(1230, true)), "Persian digits formatted properly");
        check("1230".equals(Strings.num(1230, false)), "English digits formatted properly");
        check(ThemePalette.byId(ThemePalette.THEME_JUNGLE) == ThemePalette.JUNGLE, "Jungle theme lookup");
        check(ThemePalette.byId(ThemePalette.THEME_RETRO) == ThemePalette.RETRO, "Retro theme lookup");
        check(ThemePalette.byId(ThemePalette.THEME_SUNSET) == ThemePalette.SUNSET, "Sunset theme lookup");
        for (int m = 0; m < GameEngine.MODE_COUNT; m++) {
            check(Strings.modeName(m, true).length() > 0, "mode name fa " + m);
            check(Strings.modeDescription(m, true).length() > 0, "mode desc fa " + m);
        }
    }
}
