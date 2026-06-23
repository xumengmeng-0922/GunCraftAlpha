package com.guncraft.game;

/** 界面文案（随 {@link GameSettings#getLanguage()} 切换）。 */
public final class Lang {
    private Lang() {}

    public static boolean isMirrorText() {
        return GameSettings.getLanguage() == GameLanguage.REVERSE_ZH;
    }

    private static String tr(String zh, String en) {
        return GameSettings.getLanguage() == GameLanguage.EN ? en : zh;
    }

    public static String pauseTitle() { return tr("游戏菜单", "Game Menu"); }
    public static String resume() { return tr("回到游戏", "Back to Game"); }
    public static String settings() { return tr("设置…", "Settings…"); }
    public static String backToLauncher() { return tr("回到启动器", "Back to Launcher"); }

    public static String settingsTitle() { return tr("设置", "Settings"); }
    public static String languageMenu() { return tr("语言", "Language"); }
    public static String selectLanguageTitle() { return tr("选择语言", "Select Language"); }
    public static String back() { return tr("返回", "Back"); }

    /** 语言选项标签（各语言下保持固定写法，便于识别）。 */
    public static String langChinese() { return "中文"; }
    public static String langEnglish() { return "English"; }
    public static String langReverseChinese() { return "倒着的中文"; }

    public static String inventoryTitle() { return tr("物品栏", "Inventory"); }

    public static String itemDirt() { return tr("泥土", "Dirt"); }
    public static String itemGlock() { return tr("格洛克17", "Glock 17"); }

    public static String itemName(ItemType type) {
        return switch (type) {
            case DIRT -> itemDirt();
            case GLOCK_17 -> itemGlock();
            default -> "";
        };
    }
}
