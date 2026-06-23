package com.guncraft.game;

/**
 * Minecraft 风格物品栏布局：主背包 3×9 + 快捷栏 1×9（18px 槽位 × GUI 缩放）。
 */
public class InventoryScreenLayout {
    public static final int COLS = 9;
    public static final int MAIN_ROWS = 3;

    private float panelX, panelY, panelW, panelH;
    private float slotStartX, slotStartY, hotbarY;
    private float slotSize, panelPad, titleH;
    private float hotbarRowGap;
    private int framebufferWidth, framebufferHeight;

    public void compute(int fbW, int fbH) {
        framebufferWidth = Math.max(1, fbW);
        framebufferHeight = Math.max(1, fbH);
        slotSize = McGuiScale.slotSize(framebufferWidth, framebufferHeight);
        hotbarRowGap = McGuiScale.scaled(McGuiScale.HOTBAR_ROW_GAP, framebufferWidth, framebufferHeight);
        panelPad = McGuiScale.scaled(McGuiScale.PANEL_PAD, framebufferWidth, framebufferHeight);
        titleH = McGuiScale.scaled(36, framebufferWidth, framebufferHeight);

        float gridW = COLS * slotSize;
        float gridH = MAIN_ROWS * slotSize + hotbarRowGap + slotSize;
        panelW = gridW + panelPad * 2f;
        panelH = titleH + gridH + panelPad * 2f;
        panelX = (framebufferWidth - panelW) * 0.5f;
        panelY = (framebufferHeight - panelH) * 0.5f;
        slotStartX = panelX + panelPad;
        slotStartY = panelY + titleH + panelPad;
        hotbarY = slotStartY + MAIN_ROWS * slotSize + hotbarRowGap;
    }

    public float getSlotSize() { return slotSize; }
    public float getSlotStartX() { return slotStartX; }
    public float getSlotStartY() { return slotStartY; }
    public float getHotbarY() { return hotbarY; }
    public float getPanelPad() { return panelPad; }
    public float getTitleH() { return titleH; }
    public float getPanelX() { return panelX; }
    public float getPanelY() { return panelY; }
    public float getPanelW() { return panelW; }
    public float getPanelH() { return panelH; }

    /** 槽位索引 0–35，未命中返回 -1。 */
    public int pickSlot(float mouseX, float mouseY) {
        for (int row = 0; row < MAIN_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = Inventory.MAIN_START + row * COLS + col;
                if (hitSlot(slotStartX + col * slotSize, slotStartY + row * slotSize, mouseX, mouseY))
                    return slot;
            }
        }
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            if (hitSlot(slotStartX + i * slotSize, hotbarY, mouseX, mouseY))
                return i;
        }
        return -1;
    }

    private boolean hitSlot(float x, float y, float mx, float my) {
        return mx >= x && mx < x + slotSize && my >= y && my < y + slotSize;
    }
}
