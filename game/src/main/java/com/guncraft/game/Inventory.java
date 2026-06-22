package com.guncraft.game;

/**
 * 背包：9 格快捷栏 + 27 格背包（Minecraft 风格）。按 E 打开/关闭背包。
 */
public class Inventory {
    public static final int HOTBAR_SIZE = 9;
    public static final int INVENTORY_ROWS = 3;
    public static final int INVENTORY_COLS = 9;
    public static final int INVENTORY_SIZE = INVENTORY_ROWS * INVENTORY_COLS;

    private final InventorySlot[] hotbar = new InventorySlot[HOTBAR_SIZE];
    private final InventorySlot[] backpack = new InventorySlot[INVENTORY_SIZE];
    private int selectedHotbar = 0;

    public Inventory() {
        for (int i = 0; i < HOTBAR_SIZE; i++) hotbar[i] = new InventorySlot();
        for (int i = 0; i < INVENTORY_SIZE; i++) backpack[i] = new InventorySlot();
        hotbar[0].set(ItemType.DIRT, 64);
        hotbar[1].set(ItemType.GLOCK_17, 1);
    }

    public InventorySlot getHotbarSlot(int index) {
        if (index < 0 || index >= HOTBAR_SIZE) return null;
        return hotbar[index];
    }

    public InventorySlot getBackpackSlot(int index) {
        if (index < 0 || index >= INVENTORY_SIZE) return null;
        return backpack[index];
    }

    public int getSelectedHotbar() {
        return selectedHotbar;
    }

    public void setSelectedHotbar(int index) {
        if (index >= 0 && index < HOTBAR_SIZE) this.selectedHotbar = index;
    }

    public void scrollHotbar(int delta) {
        selectedHotbar = (selectedHotbar - Integer.signum(delta) + HOTBAR_SIZE) % HOTBAR_SIZE;
    }

    public ItemType getSelectedItemType() {
        return hotbar[selectedHotbar].getType();
    }

    /** 当前选中格物品显示名（用于 HUD）。 */
    public String getSelectedItemName() {
        ItemType t = getSelectedItemType();
        return t == ItemType.NONE ? "" : t.getDisplayName();
    }

    public void consumeSelectedOne() {
        InventorySlot slot = hotbar[selectedHotbar];
        if (slot.isEmpty() || slot.getType() == ItemType.GLOCK_17) return;
        int c = slot.getCount() - 1;
        if (c <= 0) slot.clear();
        else slot.setCount(c);
    }
}
