package com.guncraft.launcher.core;

import java.util.regex.Pattern;

/**
 * 从清单中的简短字段推导 Raw 清单地址与 Releases 下载地址，减少维护者手写完整 URL。
 * <p>
 * GitHub Releases 直链格式：<br>
 * {@code https://github.com/&lt;owner&gt;/&lt;repo&gt;/releases/download/&lt;tag&gt;/&lt;file&gt;}
 * </p>
 */
public final class ManifestUrls {

    private static final Pattern SAFE_REPO = Pattern.compile("^[\\w.-]+/[\\w.-]+$");
    private static final Pattern SAFE_TAG_ASSET = Pattern.compile("^[\\w.+-]+$");

    private static boolean safeGithubRef(String ref) {
        if (ref == null || ref.isBlank()) return false;
        if (ref.contains("..") || ref.startsWith("/")) return false;
        return ref.length() <= 256;
    }

    private ManifestUrls() {}

    public static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return "";
    }

    /**
     * 若未写 {@link GameVersion#zipUrl}，则用 {@code owner/repo} + release 标签 + zip 文件名拼出 GitHub 下载地址。
     */
    public static String resolvedZipUrl(GameVersion v, VersionManifest manifest) {
        if (v == null) return "";
        if (v.zipUrl != null && !v.zipUrl.isBlank()) return v.zipUrl.trim();
        String repo = firstNonBlank(v.githubRepo, manifest != null ? manifest.githubRepo : "");
        String tag = v.releaseTag != null ? v.releaseTag.trim() : "";
        String asset = v.zipAssetName != null ? v.zipAssetName.trim() : "";
        if (repo.isEmpty() || tag.isEmpty() || asset.isEmpty()) return "";
        if (!SAFE_REPO.matcher(repo).matches()) return "";
        if (!SAFE_TAG_ASSET.matcher(tag).matches() || !SAFE_TAG_ASSET.matcher(asset).matches()) return "";
        return "https://github.com/" + repo + "/releases/download/" + tag + "/" + asset;
    }

    /**
     * 从 {@code owner/repo} 与分支推导 {@code docs/versions-manifest.json} 的 Raw 地址（未写 {@code remoteManifestUrl} 时使用）。
     */
    public static String derivedRemoteManifestUrl(VersionManifest m) {
        if (m == null || m.githubRepo == null || m.githubRepo.isBlank()) return "";
        String repo = m.githubRepo.trim();
        if (!SAFE_REPO.matcher(repo).matches()) return "";
        String branch = (m.githubBranch != null && !m.githubBranch.isBlank()) ? m.githubBranch.trim() : "main";
        if (!safeGithubRef(branch)) return "";
        int slash = repo.indexOf('/');
        String owner = repo.substring(0, slash);
        String name = repo.substring(slash + 1);
        return "https://raw.githubusercontent.com/" + owner + "/" + name + "/" + branch + "/docs/versions-manifest.json";
    }
}
