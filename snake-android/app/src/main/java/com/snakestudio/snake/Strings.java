package com.snakestudio.snake;

public final class Strings {
    private static final char[] PERSIAN_DIGITS = {
            '\u06F0', '\u06F1', '\u06F2', '\u06F3', '\u06F4',
            '\u06F5', '\u06F6', '\u06F7', '\u06F8', '\u06F9'
    };

    public static String num(int value, boolean persian) {
        String s = Integer.toString(value);
        if (!persian) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(PERSIAN_DIGITS[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String title(boolean fa) {
        return fa ? "بازی مار" : "SNAKE";
    }

    public static String subtitle(boolean fa) {
        return fa ? "آرکید کلاسیک با حرکت روان" : "Classic Arcade Edition";
    }

    public static String scoreLabel(boolean fa) {
        return fa ? "امتیاز" : "SCORE";
    }

    public static String bestLabel(boolean fa) {
        return fa ? "رکورد" : "BEST";
    }

    public static String modeSection(boolean fa) {
        return fa ? "حالت بازی" : "GAME MODE";
    }

    public static String modeName(int mode, boolean fa) {
        if (mode == GameEngine.MODE_PORTAL) return fa ? "بدون دیوار" : "Portal";
        if (mode == GameEngine.MODE_OBSTACLES) return fa ? "چالشی" : "Obstacles";
        return fa ? "کلاسیک" : "Classic";
    }

    public static String speedSection(boolean fa) {
        return fa ? "سرعت" : "SPEED";
    }

    public static String speedName(int speed, boolean fa) {
        if (speed == GameEngine.SPEED_SLOW) return fa ? "آرام" : "Easy";
        if (speed == GameEngine.SPEED_FAST) return fa ? "سریع" : "Fast";
        return fa ? "معمولی" : "Normal";
    }

    public static String themeSection(boolean fa) {
        return fa ? "پوسته" : "THEME";
    }

    public static String themeName(int theme, boolean fa) {
        if (theme == ThemePalette.THEME_RETRO) return fa ? "نوستالژی" : "Retro";
        if (theme == ThemePalette.THEME_SUNSET) return fa ? "غروب" : "Sunset";
        return fa ? "نئون" : "Neon";
    }

    public static String dpadToggle(boolean enabled, boolean fa) {
        if (fa) return enabled ? "دکمه‌های جهت: روشن" : "دکمه‌های جهت: خاموش";
        return enabled ? "D-Pad: ON" : "D-Pad: OFF";
    }

    public static String langToggle(boolean fa) {
        return fa ? "EN / فارسی" : "فارسی / EN";
    }

    public static String play(boolean fa) {
        return fa ? "شروع بازی" : "PLAY";
    }

    public static String paused(boolean fa) {
        return fa ? "توقف بازی" : "PAUSED";
    }

    public static String resume(boolean fa) {
        return fa ? "ادامه بازی" : "RESUME";
    }

    public static String restart(boolean fa) {
        return fa ? "شروع مجدد" : "RESTART";
    }

    public static String mainMenu(boolean fa) {
        return fa ? "منوی اصلی" : "MAIN MENU";
    }

    public static String gameOver(boolean fa) {
        return fa ? "پایان بازی" : "GAME OVER";
    }

    public static String youWin(boolean fa) {
        return fa ? "پیروزی کامل!" : "PERFECT WIN!";
    }

    public static String newRecord(boolean fa) {
        return fa ? "رکورد جدید!" : "NEW BEST!";
    }

    public static String playAgain(boolean fa) {
        return fa ? "بازی مجدد" : "PLAY AGAIN";
    }

    public static String applesLabel(boolean fa) {
        return fa ? "سیب‌ها" : "APPLES";
    }

    public static String lengthLabel(boolean fa) {
        return fa ? "طول مار" : "LENGTH";
    }

    public static String swipeHint(boolean fa) {
        return fa ? "برای تغییر جهت روی صفحه بکشید" : "Swipe anywhere to steer";
    }

    public static String comboTag(int combo, boolean fa) {
        return fa ? ("کمبو ×" + num(combo, true)) : ("COMBO x" + combo);
    }
}
