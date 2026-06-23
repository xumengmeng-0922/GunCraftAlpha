package com.guncraft.game;

/** 游戏界面语言。REVERSE_ZH 为彩蛋：中文镜像显示。 */
public enum GameLanguage {
    ZH,
    EN,
    REVERSE_ZH;

    public static GameLanguage fromId(String id) {
        if (id == null || id.isBlank()) return ZH;
        try {
            return valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ZH;
        }
    }
}
