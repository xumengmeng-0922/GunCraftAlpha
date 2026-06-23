package com.guncraft.game;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** 游戏设置（语言等），保存到 %APPDATA%\\GunCraft\\game-settings.properties。 */
public final class GameSettings {
    private static final String KEY_LANGUAGE = "language";
    private static GameLanguage language = GameLanguage.ZH;

    private GameSettings() {}

    public static GameLanguage getLanguage() {
        return language;
    }

    public static void setLanguage(GameLanguage lang) {
        if (lang == null) lang = GameLanguage.ZH;
        language = lang;
        save();
    }

    public static void load() {
        Path file = settingsFile();
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            language = GameLanguage.fromId(p.getProperty(KEY_LANGUAGE));
        } catch (IOException ignored) { }
    }

    private static void save() {
        Path file = settingsFile();
        try {
            Files.createDirectories(file.getParent());
            Properties p = new Properties();
            p.setProperty(KEY_LANGUAGE, language.name());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "GunCraft game settings");
            }
        } catch (IOException ignored) { }
    }

    private static Path settingsFile() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank())
            return Paths.get(System.getProperty("user.home", "."), "GunCraft", "game-settings.properties");
        return Paths.get(appData, "GunCraft", "game-settings.properties");
    }
}
