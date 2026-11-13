package com.snakestudio.snake;

public final class ThemePalette {
    public static final int THEME_NEON = 0;
    public static final int THEME_JUNGLE = 1;
    public static final int THEME_SUNSET = 2;
    public static final int THEME_RETRO = 3;
    public static final int THEME_COUNT = 4;

    public final int id;
    public final int bgOuter;
    public final int bgOuterTop;
    public final int bgOuterBottom;
    public final int ambientOrb;
    public final int bgTopBar;
    public final int boardFrame;
    public final int boardBorder;
    public final int portalBorder;
    public final int cellLight;
    public final int cellDark;
    public final int cellDot;

    public final int snakeHead;
    public final int snakeMid;
    public final int snakeTail;
    public final int snakeOutline;
    public final int snakeScale;
    public final int snakeHighlight;
    public final int tongueColor;

    public final int foodBody;
    public final int foodGlow;
    public final int foodLeaf;
    public final int bonusBody;
    public final int bonusGlow;

    public final int obstacleFill;
    public final int obstacleLight;
    public final int obstacleDark;

    public final int portalA;
    public final int portalB;
    public final int powerShield;
    public final int powerSlow;
    public final int powerDouble;

    public final int accent;
    public final int accentDark;
    public final int textPrimary;
    public final int textSecondary;
    public final int cardBg;
    public final int cardBorder;
    public final int pillBg;
    public final int pillSelectedBg;
    public final int dpadBg;
    public final int dpadPressed;
    public final int dpadBorder;
    public final int dpadIcon;
    public final boolean darkIconsOnAccent;

    private ThemePalette(
            int id,
            int bgOuter, int bgOuterTop, int bgOuterBottom, int ambientOrb,
            int bgTopBar, int boardFrame, int boardBorder, int portalBorder,
            int cellLight, int cellDark, int cellDot,
            int snakeHead, int snakeMid, int snakeTail, int snakeOutline, int snakeScale, int snakeHighlight, int tongueColor,
            int foodBody, int foodGlow, int foodLeaf, int bonusBody, int bonusGlow,
            int obstacleFill, int obstacleLight, int obstacleDark,
            int portalA, int portalB, int powerShield, int powerSlow, int powerDouble,
            int accent, int accentDark, int textPrimary, int textSecondary,
            int cardBg, int cardBorder, int pillBg, int pillSelectedBg,
            int dpadBg, int dpadPressed, int dpadBorder, int dpadIcon,
            boolean darkIconsOnAccent) {
        this.id = id;
        this.bgOuter = bgOuter;
        this.bgOuterTop = bgOuterTop;
        this.bgOuterBottom = bgOuterBottom;
        this.ambientOrb = ambientOrb;
        this.bgTopBar = bgTopBar;
        this.boardFrame = boardFrame;
        this.boardBorder = boardBorder;
        this.portalBorder = portalBorder;
        this.cellLight = cellLight;
        this.cellDark = cellDark;
        this.cellDot = cellDot;
        this.snakeHead = snakeHead;
        this.snakeMid = snakeMid;
        this.snakeTail = snakeTail;
        this.snakeOutline = snakeOutline;
        this.snakeScale = snakeScale;
        this.snakeHighlight = snakeHighlight;
        this.tongueColor = tongueColor;
        this.foodBody = foodBody;
        this.foodGlow = foodGlow;
        this.foodLeaf = foodLeaf;
        this.bonusBody = bonusBody;
        this.bonusGlow = bonusGlow;
        this.obstacleFill = obstacleFill;
        this.obstacleLight = obstacleLight;
        this.obstacleDark = obstacleDark;
        this.portalA = portalA;
        this.portalB = portalB;
        this.powerShield = powerShield;
        this.powerSlow = powerSlow;
        this.powerDouble = powerDouble;
        this.accent = accent;
        this.accentDark = accentDark;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.cardBg = cardBg;
        this.cardBorder = cardBorder;
        this.pillBg = pillBg;
        this.pillSelectedBg = pillSelectedBg;
        this.dpadBg = dpadBg;
        this.dpadPressed = dpadPressed;
        this.dpadBorder = dpadBorder;
        this.dpadIcon = dpadIcon;
        this.darkIconsOnAccent = darkIconsOnAccent;
    }

    public static final ThemePalette NEON = new ThemePalette(
            THEME_NEON,
            0xFF0A1220, 0xFF0F2038, 0xFF070D18, 0x2222D3EE,
            0xFF111E33, 0xFF0D1729, 0xFF284161, 0xFF22D3EE,
            0xFF15253B, 0xFF101D30, 0x18FFFFFF,
            0xFF4ADE80, 0xFF10B981, 0xFF06B6D4, 0xFF053F31, 0x3A042F2E, 0x66ECFDF5, 0xFFFB7185,
            0xFFF43F5E, 0x66F43F5E, 0xFF4ADE80, 0xFFFACC15, 0x77FACC15,
            0xFF475569, 0xFF94A3B8, 0xFF1E293B,
            0xFF22D3EE, 0xFFE879F9, 0xFF38BDF8, 0xFF67E8F9, 0xFFC084FC,
            0xFF10B981, 0xFF059669, 0xFFF8FAFC, 0xFF94A3B8,
            0xF00D192B, 0xFF2B4669, 0xFF162740, 0xFF10B981,
            0xFF132238, 0xFF10B981, 0xFF2C4768, 0xFFE2E8F0,
            false
    );

    public static final ThemePalette JUNGLE = new ThemePalette(
            THEME_JUNGLE,
            0xFF091A12, 0xFF102E1F, 0xFF06120C, 0x224ADE80,
            0xFF11291D, 0xFF0B1F15, 0xFF2B5B3F, 0xFF86EFAC,
            0xFF183625, 0xFF132C1E, 0x18D9F99D,
            0xFFA3E635, 0xFF22C55E, 0xFF15803D, 0xFF052E16, 0x44052E16, 0x66F7FEE7, 0xFFEF4444,
            0xFFEF4444, 0x66EF4444, 0xFF86EFAC, 0xFFFDE047, 0x77FDE047,
            0xFF57534E, 0xFFA8A29E, 0xFF292524,
            0xFF34D399, 0xFFFBBF24, 0xFF38BDF8, 0xFF7DD3FC, 0xFFF59E0B,
            0xFF22C55E, 0xFF15803D, 0xFFF0FDF4, 0xFF86EFAC,
            0xF00D2318, 0xFF2F6344, 0xFF163524, 0xFF22C55E,
            0xFF132E1F, 0xFF22C55E, 0xFF2E6143, 0xFFDCFCE7,
            true
    );

    public static final ThemePalette SUNSET = new ThemePalette(
            THEME_SUNSET,
            0xFF160A1D, 0xFF2A1238, 0xFF0F0614, 0x22F472B6,
            0xFF24112E, 0xFF1B0C24, 0xFF59286E, 0xFFF472B6,
            0xFF2D1738, 0xFF23112D, 0x18FDE68A,
            0xFFFDE047, 0xFFFB923C, 0xFFF43F5E, 0xFF6B1D0F, 0x3D431407, 0x66FEF9C3, 0xFF22D3EE,
            0xFF22D3EE, 0x6622D3EE, 0xFF34D399, 0xFFFEF08A, 0x77FDE047,
            0xFF6B21A8, 0xFFC084FC, 0xFF3B0764,
            0xFF38BDF8, 0xFFF472B6, 0xFF22D3EE, 0xFF93C5FD, 0xFFFACC15,
            0xFFF97316, 0xFFEA580C, 0xFFFFF7ED, 0xFFD2AEE0,
            0xF0200E29, 0xFF632D78, 0xFF31173D, 0xFFF97316,
            0xFF2A1436, 0xFFF97316, 0xFF5F2B73, 0xFFFCE7F3,
            false
    );

    public static final ThemePalette RETRO = new ThemePalette(
            THEME_RETRO,
            0xFF132011, 0xFF1D311A, 0xFF0D170C, 0x1CA3E635,
            0xFF1E301A, 0xFF284022, 0xFF588737, 0xFFD9F99D,
            0xFF9BBC0F, 0xFF91B30C, 0x1C0F380F,
            0xFF0F380F, 0xFF1E4D1E, 0xFF306230, 0xFF061C06, 0x408BAC0F, 0x55A3C926, 0xFFDC2626,
            0xFFDC2626, 0x55DC2626, 0xFF14532D, 0xFFD97706, 0x66F59E0B,
            0xFF2D4C2D, 0xFF5C8244, 0xFF122612,
            0xFF0284C7, 0xFF9333EA, 0xFF0284C7, 0xFF0EA5E9, 0xFF7C3AED,
            0xFF84CC16, 0xFF65A30D, 0xFFF0FDF4, 0xFFA3C293,
            0xF0172614, 0xFF528212, 0xFF23391E, 0xFF84CC16,
            0xFF1F331B, 0xFF84CC16, 0xFF456D36, 0xFFD9F99D,
            true
    );

    public static ThemePalette byId(int id) {
        if (id == THEME_JUNGLE) return JUNGLE;
        if (id == THEME_SUNSET) return SUNSET;
        if (id == THEME_RETRO) return RETRO;
        return NEON;
    }

    public static int lerpColor(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
