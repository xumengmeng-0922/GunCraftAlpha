package com.guncraft.launcher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class InstallPaths {

    private InstallPaths() {}

    /**
     * 启动器仅支持 Windows：数据根为 {@code %APPDATA%\GunCraft}。
     */
    public static Path dataRoot() {
        String app = System.getenv("APPDATA");
        if (app != null && !app.isBlank())
            return Paths.get(app, "GunCraft");
        String profile = System.getenv("USERPROFILE");
        if (profile != null && !profile.isBlank())
            return Paths.get(profile, "AppData", "Roaming", "GunCraft");
        return Paths.get(System.getProperty("user.home", "."), "AppData", "Roaming", "GunCraft");
    }

    /** 各版本游戏解压根目录（「游戏文件夹」）：{@code <数据根>/game/<版本 id>/} */
    public static Path gameDir() {
        return dataRoot().resolve("game");
    }

    /** 新版本解压、下载写入目录（始终在 {@code game/<id>}） */
    public static Path versionDir(String versionId) {
        return gameDir().resolve(versionId);
    }

    private static Path legacyVersionDir(String versionId) {
        return dataRoot().resolve("versions").resolve(versionId);
    }

    /**
     * 该版本实际安装根目录：默认 {@code game/<id>}；若仅存在旧版 {@code versions/<id>} 则兼容（升级前数据仍可用）。
     */
    public static Path installRoot(GameVersion v) {
        Path g = versionDir(v.id);
        if (Files.isRegularFile(g.resolve(v.resolvedJarName()))) return g;
        Path o = legacyVersionDir(v.id);
        if (Files.isRegularFile(o.resolve(v.resolvedJarName()))) return o;
        return g;
    }

    public static Path jarPath(GameVersion v) {
        return installRoot(v).resolve(v.resolvedJarName());
    }

    public static Path libDir(GameVersion v) {
        return installRoot(v).resolve("lib");
    }

    public static void ensureDirs() throws Exception {
        Files.createDirectories(gameDir());
    }

    public static boolean isInstalled(GameVersion v) {
        if (!Files.isRegularFile(jarPath(v))) return false;
        Path lib = libDir(v);
        if (!Files.isDirectory(lib)) return false;
        try (Stream<Path> s = Files.list(lib)) {
            return s.anyMatch(p -> p.toString().endsWith(".jar"));
        } catch (Exception e) {
            return false;
        }
    }
}
