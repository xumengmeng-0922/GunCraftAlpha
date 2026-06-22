package com.guncraft.game;

/**
 * 单个背包格：物品类型 + 数量（方块可堆叠，枪为 1）。
 */
public class InventorySlot {
    private ItemType type = ItemType.NONE;
    private int count;

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isEmpty() {
        return type == ItemType.NONE || count <= 0;
    }

    public void set(ItemType type, int count) {
        this.type = type;
        this.count = count;
    }

    public void clear() {
        type = ItemType.NONE;
        count = 0;
    }

    /** 最大堆叠数：枪为 1，泥土 64。 */
    public static int getMaxStack(ItemType type) {
        if (type == ItemType.GLOCK_17) return 1;
        if (type == ItemType.DIRT) return 64;
        return 0;
    }
}
