package com.guncraft.game;

/**
 * Minecraft 风格玩家物品栏：36 格一体（0–8 快捷栏，9–35 主背包），光标手持堆叠。
 */
public class Inventory {
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_ROWS = 3;
    public static final int COLS = 9;
    public static final int MAIN_SIZE = MAIN_ROWS * COLS;
    public static final int TOTAL_SLOTS = HOTBAR_SIZE + MAIN_SIZE;
    public static final int MAIN_START = HOTBAR_SIZE;

    private final InventorySlot[] slots = new InventorySlot[TOTAL_SLOTS];
    private int selectedHotbar = 0;
    private ItemType cursorType = ItemType.NONE;
    private int cursorCount = 0;

    public Inventory() {
        for (int i = 0; i < TOTAL_SLOTS; i++) slots[i] = new InventorySlot();
        slots[0].set(ItemType.DIRT, 64);
        slots[1].set(ItemType.GLOCK_17, 1);
    }

    /** 统一槽位：0–8 快捷栏，9–35 主背包（自上而下三行）。 */
    public InventorySlot getSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return null;
        return slots[index];
    }

    public InventorySlot getHotbarSlot(int index) {
        return getSlot(index);
    }

    /** @param index 主背包局部索引 0–26，对应槽位 9–35 */
    public InventorySlot getBackpackSlot(int index) {
        return getSlot(MAIN_START + index);
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
        return slots[selectedHotbar].getType();
    }

    public String getSelectedItemName() {
        ItemType t = getSelectedItemType();
        return t == ItemType.NONE ? "" : t.getDisplayName();
    }

    public boolean hasCursor() {
        return cursorType != ItemType.NONE && cursorCount > 0;
    }

    public ItemType getCursorType() {
        return cursorType;
    }

    public int getCursorCount() {
        return cursorCount;
    }

    /** 关闭物品栏时把光标上的物品收回背包（Minecraft 行为）。 */
    public void returnCursorStack() {
        if (!hasCursor()) return;
        if (tryInsertCursor(false)) clearCursor();
    }

    /** Minecraft 槽位点击：左键整堆/交换/合并，右键半堆/单个；Shift 快速转移。 */
    public void handleSlotClick(int slotIndex, boolean rightClick, boolean shift) {
        if (slotIndex < 0 || slotIndex >= TOTAL_SLOTS) return;
        if (shift && !hasCursor()) {
            quickMove(slotIndex);
            return;
        }
        InventorySlot slot = slots[slotIndex];
        if (rightClick) handleRightClick(slot);
        else handleLeftClick(slot);
    }

    public void consumeSelectedOne() {
        InventorySlot slot = slots[selectedHotbar];
        if (slot.isEmpty() || slot.getType() == ItemType.GLOCK_17) return;
        int c = slot.getCount() - 1;
        if (c <= 0) slot.clear();
        else slot.setCount(c);
    }

    private void handleLeftClick(InventorySlot slot) {
        if (!hasCursor()) {
            if (!slot.isEmpty()) {
                cursorType = slot.getType();
                cursorCount = slot.getCount();
                slot.clear();
            }
            return;
        }
        if (slot.isEmpty()) {
            slot.set(cursorType, cursorCount);
            clearCursor();
        } else if (slot.getType() == cursorType) {
            int max = InventorySlot.getMaxStack(cursorType);
            int space = max - slot.getCount();
            if (space > 0) {
                int move = Math.min(space, cursorCount);
                slot.setCount(slot.getCount() + move);
                cursorCount -= move;
                if (cursorCount <= 0) clearCursor();
            }
        } else {
            swapWithCursor(slot);
        }
    }

    private void handleRightClick(InventorySlot slot) {
        if (!hasCursor()) {
            if (!slot.isEmpty()) {
                int half = (slot.getCount() + 1) / 2;
                cursorType = slot.getType();
                cursorCount = half;
                slot.setCount(slot.getCount() - half);
                if (slot.getCount() <= 0) slot.clear();
            }
            return;
        }
        if (slot.isEmpty()) {
            slot.set(cursorType, 1);
            cursorCount--;
            if (cursorCount <= 0) clearCursor();
        } else if (slot.getType() == cursorType
                && slot.getCount() < InventorySlot.getMaxStack(cursorType)) {
            slot.setCount(slot.getCount() + 1);
            cursorCount--;
            if (cursorCount <= 0) clearCursor();
        }
    }

    private void swapWithCursor(InventorySlot slot) {
        ItemType t = slot.getType();
        int c = slot.getCount();
        slot.set(cursorType, cursorCount);
        cursorType = t;
        cursorCount = c;
        if (cursorCount <= 0) cursorType = ItemType.NONE;
    }

    /** Shift+点击：快捷栏与主背包之间快速转移整堆。 */
    private void quickMove(int fromIndex) {
        InventorySlot src = slots[fromIndex];
        if (src.isEmpty()) return;
        boolean fromHotbar = fromIndex < HOTBAR_SIZE;
        int rangeStart = fromHotbar ? MAIN_START : 0;
        int rangeEnd = fromHotbar ? TOTAL_SLOTS : HOTBAR_SIZE;
        ItemType type = src.getType();
        int count = src.getCount();
        int max = InventorySlot.getMaxStack(type);

        for (int i = rangeStart; i < rangeEnd && count > 0; i++) {
            InventorySlot dst = slots[i];
            if (!dst.isEmpty() && dst.getType() == type && dst.getCount() < max) {
                int space = max - dst.getCount();
                int move = Math.min(space, count);
                dst.setCount(dst.getCount() + move);
                count -= move;
            }
        }
        for (int i = rangeStart; i < rangeEnd && count > 0; i++) {
            InventorySlot dst = slots[i];
            if (dst.isEmpty()) {
                int move = Math.min(max, count);
                dst.set(type, move);
                count -= move;
            }
        }
        if (count <= 0) src.clear();
        else src.set(type, count);
    }

    private boolean tryInsertCursor(boolean hotbarFirst) {
        if (!hasCursor()) return true;
        if (hotbarFirst) {
            if (insertIntoRange(0, HOTBAR_SIZE)) return true;
            if (insertIntoRange(MAIN_START, TOTAL_SLOTS)) return true;
        } else {
            if (insertIntoRange(MAIN_START, TOTAL_SLOTS)) return true;
            if (insertIntoRange(0, HOTBAR_SIZE)) return true;
        }
        return false;
    }

    private boolean insertIntoRange(int start, int end) {
        for (int i = start; i < end && hasCursor(); i++) {
            InventorySlot s = slots[i];
            if (!s.isEmpty() && s.getType() == cursorType
                    && s.getCount() < InventorySlot.getMaxStack(cursorType)) {
                int space = InventorySlot.getMaxStack(cursorType) - s.getCount();
                int move = Math.min(space, cursorCount);
                s.setCount(s.getCount() + move);
                cursorCount -= move;
            }
        }
        for (int i = start; i < end && hasCursor(); i++) {
            if (slots[i].isEmpty()) {
                slots[i].set(cursorType, cursorCount);
                cursorCount = 0;
            }
        }
        return !hasCursor();
    }

    private void clearCursor() {
        cursorType = ItemType.NONE;
        cursorCount = 0;
    }
}
