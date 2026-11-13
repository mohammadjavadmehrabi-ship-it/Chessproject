package com.snakestudio.snake;

public final class ThemePalette {
    public static final int THEME_NEON = 0;
    public static final int THEME_RETRO = 1;
    public static final int THEME_SUNSET = 2;
    public static final int THEME_COUNT = 3;

    public final int id;
    public final int bgOuter;
    public final int bgTopBar;
    public final int boardFrame;
    public final int boardBorder;
    public final int portalBorder;
    public final int cellLight;
    public final int cellDark;

    public final int snakeHead;
    public final int snakeMid;
    public final int snakeTail;
    public final int snakeOutline;
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
            int bgOuter, int bgTopBar, int boardFrame, int boardBorder, int portalBorder,
            int cellLight, int cellDark,
            int snakeHead, int snakeMid, int snakeTail, int snakeOutline, int snakeHighlight, int tongueColor,
            int foodBody, int foodGlow, int foodLeaf, int bonusBody, int bonusGlow,
            int obstacleFill, int obstacleLight, int obstacleDark,
            int accent, int accentDark, int textPrimary, int textSecondary,
            int cardBg, int cardBorder, int pillBg, int pillSelectedBg,
            int dpadBg, int dpadPressed, int dpadBorder, int dpadIcon,
            boolean darkIconsOnAccent) {
        this.id = id;
        this.bgOuter = bgOuter;
        this.bgTopBar = bgTopBar;
        this.boardFrame = boardFrame;
        this.boardBorder = boardBorder;
        this.portalBorder = portalBorder;
        this.cellLight = cellLight;
        this.cellDark = cellDark;
        this.snakeHead = snakeHead;
        this.snakeMid = snakeMid;
        this.snakeTail = snakeTail;
        this.snakeOutline = snakeOutline;
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
            0xFF0B1220, 0xFF111C2E, 0xFF0E1726, 0xFF233852, 0xFF22D3EE,
            0xFF152338, 0xFF101C2E,
            0xFF4ADE80, 0xFF10B981, 0xFF06B6D4, 0xFF064E3B, 0x66ECFDF5, 0xFFFB7185,
            0xFFF43F5E, 0x55F43F5E, 0xFF4ADE80, 0xFFFACC15, 0x66FACC15,
            0xFF475569, 0xFF94A3B8, 0xFF1E293B,
            0xFF10B981, 0xFF059669, 0xFFF8FAFC, 0xFF94A3B8,
            0xEE0F1B2D, 0xFF263E5C, 0xFF18283F, 0xFF10B981,
            0xFF142236, 0xFF10B981, 0xFF29405E, 0xFFE2E8F0,
            false
    );

    public static final ThemePalette RETRO = new ThemePalette(
            THEME_RETRO,
            0xFF142012, 0xFF1E2F1A, 0xFF273D22, 0xFF4F7A33, 0xFFB8E24B,
            0xFF9BBC0F, 0xFF91B30C,
            0xFF0F380F, 0xFF1E4D1E, 0xFF306230, 0xFF082208, 0x558BAC0F, 0xFFDC2626,
            0xFFDC2626, 0x55DC2626, 0xFF166534, 0xFFD97706, 0x66F59E0B,
            0xFF2F4F2F, 0xFF5A7F45, 0xFF142814,
            0xFF84CC16, 0xFF65A30D, 0xFFF0FDF4, 0xFFA3C293,
            0xEE182715, 0xFF4D7C0F, 0xFF23381E, 0xFF84CC16,
            0xFF1F331B, 0xFF84CC16, 0xFF426635, 0xFFD9F99D,
            true
    );

    public static final ThemePalette SUNSET = new ThemePalette(
            THEME_SUNSET,
            0xFF170B1A, 0xFF231229, 0xFF1D0E22, 0xFF4B2457, 0xFFF472B6,
            0xFF291630, 0xFF211127,
            0xFFFDE047, 0xFFFB923C, 0xFFF43F5E, 0xFF7C2D12, 0x66FEF9C3, 0xFF22D3EE,
            0xFF22D3EE, 0x5522D3EE, 0xFF34D399, 0xFFFEF08A, 0x66FDE047,
            0xFF581C87, 0xFFA855F7, 0xFF2E1065,
            0xFFF97316, 0xFFEA580C, 0xFFFFF7ED, 0xFFC4A1D1,
            0xEE221128, 0xFF5A2A69, 0xFF301838, 0xFFF97316,
            0xFF2B1633, 0xFFF97316, 0xFF592D69, 0xFFFDE7FF,
            false
    );

    public static ThemePalette byId(int id) {
        if (id == THEME_RETRO) return RETRO;
        if (id == THEME_SUNSET) return SUNSET;
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
