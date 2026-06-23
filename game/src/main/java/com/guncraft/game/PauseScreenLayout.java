package com.guncraft.game;

/**
 * 暂停菜单布局：主菜单 → 设置 → 语言 → 选择语言。
 */
public class PauseScreenLayout {
    public static final int BTN_RESUME = 0;
    public static final int BTN_SETTINGS = 1;
    public static final int BTN_LAUNCHER = 2;
    public static final int BTN_BACK = 3;
    public static final int BTN_LANG_ZH = 4;
    public static final int BTN_LANG_EN = 5;
    public static final int BTN_LANG_REVERSE = 6;
    public static final int BTN_LANGUAGE = 7;

    private static final float BTN_W = 300f;
    private static final float BTN_H = 52f;
    private static final float BTN_GAP = 12f;

    private float resumeX, resumeY, settingsX, settingsY, launcherX, launcherY;
    private float backX, backY, languageX, languageY;
    private float langZhX, langZhY, langEnX, langEnY, langRevX, langRevY;
    private int framebufferWidth, framebufferHeight;

    public void computeMenu(int fbW, int fbH) {
        framebufferWidth = Math.max(1, fbW);
        framebufferHeight = Math.max(1, fbH);
        float cx = (framebufferWidth - BTN_W) * 0.5f;
        float totalH = BTN_H * 3 + BTN_GAP * 2;
        float startY = framebufferHeight * 0.5f - totalH * 0.5f + BTN_H * 0.5f;
        resumeX = cx;
        resumeY = startY;
        settingsX = cx;
        settingsY = startY + BTN_H + BTN_GAP;
        launcherX = cx;
        launcherY = startY + (BTN_H + BTN_GAP) * 2;
    }

    /** 设置页：语言 + 返回。 */
    public void computeSettings(int fbW, int fbH) {
        computeMenu(fbW, fbH);
        float cx = (framebufferWidth - BTN_W) * 0.5f;
        float startY = framebufferHeight * 0.46f;
        languageX = cx;
        languageY = startY;
        backX = cx;
        backY = startY + BTN_H + BTN_GAP;
    }

    /** 语言页：三种语言 + 返回。 */
    public void computeLanguage(int fbW, int fbH) {
        computeMenu(fbW, fbH);
        float cx = (framebufferWidth - BTN_W) * 0.5f;
        float startY = framebufferHeight * 0.40f;
        langZhX = cx;
        langZhY = startY;
        langEnX = cx;
        langEnY = startY + BTN_H + BTN_GAP;
        langRevX = cx;
        langRevY = startY + (BTN_H + BTN_GAP) * 2;
        backX = cx;
        backY = langRevY + BTN_H + BTN_GAP * 2;
    }

    public float getBtnW() { return BTN_W; }
    public float getBtnH() { return BTN_H; }

    public float getResumeX() { return resumeX; }
    public float getResumeY() { return resumeY; }
    public float getSettingsX() { return settingsX; }
    public float getSettingsY() { return settingsY; }
    public float getLauncherX() { return launcherX; }
    public float getLauncherY() { return launcherY; }
    public float getBackX() { return backX; }
    public float getBackY() { return backY; }
    public float getLanguageX() { return languageX; }
    public float getLanguageY() { return languageY; }
    public float getLangZhX() { return langZhX; }
    public float getLangZhY() { return langZhY; }
    public float getLangEnX() { return langEnX; }
    public float getLangEnY() { return langEnY; }
    public float getLangRevX() { return langRevX; }
    public float getLangRevY() { return langRevY; }

    public int pickMenuButton(float mouseX, float mouseY) {
        if (hit(resumeX, resumeY, mouseX, mouseY)) return BTN_RESUME;
        if (hit(settingsX, settingsY, mouseX, mouseY)) return BTN_SETTINGS;
        if (hit(launcherX, launcherY, mouseX, mouseY)) return BTN_LAUNCHER;
        return -1;
    }

    public int pickSettingsButton(float mouseX, float mouseY) {
        if (hit(languageX, languageY, mouseX, mouseY)) return BTN_LANGUAGE;
        if (hit(backX, backY, mouseX, mouseY)) return BTN_BACK;
        return -1;
    }

    public int pickLanguageButton(float mouseX, float mouseY) {
        if (hit(langZhX, langZhY, mouseX, mouseY)) return BTN_LANG_ZH;
        if (hit(langEnX, langEnY, mouseX, mouseY)) return BTN_LANG_EN;
        if (hit(langRevX, langRevY, mouseX, mouseY)) return BTN_LANG_REVERSE;
        if (hit(backX, backY, mouseX, mouseY)) return BTN_BACK;
        return -1;
    }

    private boolean hit(float x, float y, float mx, float my) {
        return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
    }
}
