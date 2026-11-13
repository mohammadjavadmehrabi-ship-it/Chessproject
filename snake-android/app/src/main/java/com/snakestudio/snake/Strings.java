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
        return fa ? "بازی مار ۲" : "SNAKE PRO";
    }

    public static String subtitle(boolean fa) {
        return fa ? "۶ حالت بازی • قدرت‌های ویژه • گرافیک ۶۰fps" : "6 Modes • Power-Ups • 60fps Arcade";
    }

    public static String scoreLabel(boolean fa) {
        return fa ? "امتیاز" : "SCORE";
    }

    public static String bestLabel(boolean fa) {
        return fa ? "رکورد" : "BEST";
    }

    public static String modeSection(boolean fa) {
        return fa ? "حالت بازی (۶ حالت)" : "GAME MODE (6 MODES)";
    }

    public static String modeName(int mode, boolean fa) {
        switch (mode) {
            case GameEngine.MODE_PORTAL:
                return fa ? "بدون دیوار" : "No Walls";
            case GameEngine.MODE_OBSTACLES:
                return fa ? "چالشی" : "Obstacles";
            case GameEngine.MODE_MAZE:
                return fa ? "هزارتو" : "Maze";
            case GameEngine.MODE_WORMHOLE:
                return fa ? "تونل فضایی" : "Wormhole";
            case GameEngine.MODE_BLITZ:
                return fa ? "حمله زمانی" : "Time Blitz";
            default:
                return fa ? "کلاسیک" : "Classic";
        }
    }

    public static String modeDescription(int mode, boolean fa) {
        switch (mode) {
            case GameEngine.MODE_PORTAL:
                return fa ? "از دیوارها عبور کنید و از سمت مقابل بیرون بیایید"
                        : "Wrap seamlessly through every wall edge";
            case GameEngine.MODE_OBSTACLES:
                return fa ? "موانع سنگی تصادفی همراه با قدرت‌های ویژه"
                        : "Random crystal barriers with power-up orbs";
            case GameEngine.MODE_MAZE:
                return fa ? "مسیرهای مارپیچ و دروازه‌های عبوری با امتیاز ویژه"
                        : "Symmetric arcade labyrinth with wrap gates";
            case GameEngine.MODE_WORMHOLE:
                return fa ? "از یک پورتال وارد شوید و از پورتال جفت بیرون بیایید"
                        : "Teleport between twin animated space portals";
            case GameEngine.MODE_BLITZ:
                return fa ? "۶۰ ثانیه رقابت سرعتی! هر سیب زمان اضافه می‌دهد"
                        : "60-second rush! Apples add time + frequent power-ups";
            default:
                return fa ? "قوانین کلاسیک مار با میوه طلایی و سیستم کمبو"
                        : "Classic Snake with golden stars & combo streaks";
        }
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
        switch (theme) {
            case ThemePalette.THEME_JUNGLE:
                return fa ? "جنگل" : "Jungle";
            case ThemePalette.THEME_SUNSET:
                return fa ? "غروب" : "Sunset";
            case ThemePalette.THEME_RETRO:
                return fa ? "نوستالژی" : "Retro";
            default:
                return fa ? "نئون" : "Neon";
        }
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

    public static String timeUp(boolean fa) {
        return fa ? "پایان زمان!" : "TIME'S UP!";
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

    public static String powerShieldName(boolean fa) {
        return fa ? "سپر محافظ!" : "SHIELD ON!";
    }

    public static String powerShieldSaved(boolean fa) {
        return fa ? "سپر نجاتت داد!" : "SHIELD SAVED!";
    }

    public static String powerSlowName(boolean fa) {
        return fa ? "زمان آهسته!" : "SLOW-MO!";
    }

    public static String powerDoubleName(boolean fa) {
        return fa ? "امتیاز ×۲!" : "2X SCORE!";
    }

    public static String portalWarp(boolean fa) {
        return fa ? "تله‌پورت!" : "WARP!";
    }

    public static String timeLeftTag(int sec, boolean fa) {
        return fa ? (num(sec, true) + " ثانیه") : (sec + "s");
    }
}
