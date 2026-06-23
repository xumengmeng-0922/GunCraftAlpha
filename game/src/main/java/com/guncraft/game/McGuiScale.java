package com.guncraft.game;

/**
 * Minecraft 风格 GUI 缩放：槽位基准 18px，并按屏幕高度保证足够大（1080p 约 100px/格）。
 */
public final class McGuiScale {
    /** MC 纹理里单格边长（像素）。 */
    public static final int SLOT_BASE = 18;
    /** 单格高度占屏幕比例（1080p ≈ 99px，约为 MC 最大 GUI 的 1.4 倍）。 */
    private static final float SLOT_HEIGHT_RATIO = 0.092f;
    /** 主背包与快捷栏之间的间距（MC 为 4px @ scale 1）。 */
    public static final int HOTBAR_ROW_GAP = 4;
    /** 面板内边距（约等于 MC 物品栏 GUI 边距）。 */
    public static final int PANEL_PAD = 8;

    private McGuiScale() {}

    /** 与 MC「自动 GUI 缩放」相同的 1–4 倍系数。 */
    public static int computeScale(int framebufferWidth, int framebufferHeight) {
        int w = Math.max(1, framebufferWidth);
        int h = Math.max(1, framebufferHeight);
        int scale = 1;
        while (scale < 4 && w / (scale + 1) >= 320 && h / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    /** 物品栏 / 快捷栏用缩放：720p 及以上至少 4 倍（72px/格，MC「大」GUI）。 */
    public static int inventoryScale(int framebufferWidth, int framebufferHeight) {
        int auto = computeScale(framebufferWidth, framebufferHeight);
        int h = Math.max(1, framebufferHeight);
        if (h >= 720) return Math.max(auto, 4);
        if (h >= 480) return Math.max(auto, 3);
        return auto;
    }

    public static float slotSize(int framebufferWidth, int framebufferHeight) {
        int h = Math.max(1, framebufferHeight);
        float byMc = SLOT_BASE * inventoryScale(framebufferWidth, h);
        float byScreen = h * SLOT_HEIGHT_RATIO;
        return Math.max(byMc, byScreen);
    }

    public static float scaled(int pixels, int framebufferWidth, int framebufferHeight) {
        return pixels * inventoryScale(framebufferWidth, framebufferHeight);
    }
}
