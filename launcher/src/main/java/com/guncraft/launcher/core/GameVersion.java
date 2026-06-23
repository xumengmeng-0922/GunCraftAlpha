package com.guncraft.launcher.core;

/**
 * 可下载 / 可启动的游戏版本条目。
 */
public class GameVersion {
    public String id;
    public String displayName;
    public String description = "";
    /** 完整游戏包 zip 的 HTTPS 直链（内含 jar + lib/）；与下面 GitHub 字段二选一 */
    public String zipUrl = "";
    /** 可选：覆盖清单根上的 {@link VersionManifest#githubRepo}（一般留空即可） */
    public String githubRepo = "";
    /** 与 {@link VersionManifest#githubRepo} 及 {@link #zipAssetName} 组合为 GitHub Releases 直链 */
    public String releaseTag = "";
    /** Releases 上附件文件名，例如 {@code guncraft-game-alpha-1.3.zip} */
    public String zipAssetName = "";
    public String jarName = "";
    /** GMT+8 发布日期，格式 yyyy-MM-dd */
    public String releaseDate = "";

    /** 带时区说明的发布日期，无则返回空串。 */
    public String releaseDateLabel() {
        if (releaseDate == null || releaseDate.isBlank()) return "";
        return releaseDate.trim() + " (GMT+8)";
    }

    public String resolvedJarName() {
        if (jarName != null && !jarName.isBlank()) return jarName.trim();
        return "guncraft-game-" + id + ".jar";
    }
}
