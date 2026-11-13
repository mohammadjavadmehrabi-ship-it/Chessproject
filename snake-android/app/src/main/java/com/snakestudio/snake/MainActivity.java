package com.snakestudio.snake;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

public final class MainActivity extends Activity {

    private SnakeGameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        gameView = new SnakeGameView(this);
        setContentView(gameView);

        gameView.setThemeChangeCallback(new SnakeGameView.ThemeChangeCallback() {
            @Override
            public void onThemeChanged(int bgOuterColor) {
                applySystemBarColors(bgOuterColor);
            }
        });
        applySystemBarColors(gameView.getCurrentBgColor());

        if (Build.VERSION.SDK_INT >= 30) {
            Api30InsetsHelper.enableEdgeToEdge(win, gameView);
        }
    }

    private void applySystemBarColors(int bgColor) {
        Window win = getWindow();
        if (win == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            win.setStatusBarColor(0x00000000);
            win.setNavigationBarColor(0x00000000);
        } else {
            win.setStatusBarColor(bgColor);
            win.setNavigationBarColor(bgColor);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.onHostPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) {
            gameView.onHostDestroy();
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (gameView != null && gameView.handleBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    /**
     * Isolated API 30+ helper so API 21-29 runtimes never load android.graphics.Insets.
     */
    private static final class Api30InsetsHelper {
        static void enableEdgeToEdge(Window win, final SnakeGameView view) {
            win.setDecorFitsSystemWindows(false);
            view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    Insets sys = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    view.setSafeInsets(sys.left, sys.top, sys.right, sys.bottom);
                    return WindowInsets.CONSUMED;
                }
            });
            view.requestApplyInsets();
        }
    }
}
