package com.guncraft.game;

/**
 * 物品类型：泥土（可放置方块）、格洛克17（枪，左键射击）。
 */
public enum ItemType {
    NONE(""),
    DIRT("泥土"),
    GLOCK_17("格洛克17");

    private final String displayName;

    ItemType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBlock() {
        return this == DIRT;
    }

    public boolean isWeapon() {
        return this == GLOCK_17;
    }
}
