package com.guncraft.launcher.core;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 版本清单加载：优先联网拉取远程 JSON；失败则用内置离线列表。
 * <p>
 * 远程地址解析顺序（第一个非空即用）：<br>
 * 1) 用户在设置里填写的 URL<br>
 * 2) 内嵌 {@code versions-manifest.json} 里的 {@code remoteManifestUrl}<br>
 * 3) 根字段 {@code githubRepo}（{@code owner/repo}）+ {@code githubBranch} 推导 Raw 清单地址<br>
 * 4) 资源文件 {@code /builtin-remote-manifest.url} 中首个非注释、非空行
 * </p>
 * 发行新游戏版本时：只需更新服务器上的清单 JSON（及 Releases 里的 zip），玩家<strong>无需重装启动器</strong>。
 */
public final class ManifestLoader {
    private static final Gson GSON = new Gson();

    private ManifestLoader() {}

    public static VersionManifest loadEmbedded() {
        try (InputStream in = ManifestLoader.class.getResourceAsStream("/versions-manifest.json")) {
            if (in == null) return new VersionManifest();
            VersionManifest m = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), VersionManifest.class);
            return m != null ? m : new VersionManifest();
        } catch (Exception e) {
            return new VersionManifest();
        }
    }

    /** 读取打包时内置的默认远程地址（每行一条，# 开头为注释）。 */
    public static String readBuiltinRemoteUrl() {
        try (InputStream in = ManifestLoader.class.getResourceAsStream("/builtin-remote-manifest.url")) {
            if (in == null) return "";
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                if (s.startsWith("http://") || s.startsWith("https://")) return s;
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 解析最终使用的远程清单 URL。
     *
     * @param userSettingsUrl 设置里填写的地址（可空）
     */
    public static String resolveRemoteManifestUrl(String userSettingsUrl) {
        String u = userSettingsUrl != null ? userSettingsUrl.trim() : "";
        if (!u.isEmpty()) return u;
        VersionManifest embedded = loadEmbedded();
        if (embedded.remoteManifestUrl != null && !embedded.remoteManifestUrl.isBlank())
            return embedded.remoteManifestUrl.trim();
        String derived = ManifestUrls.derivedRemoteManifestUrl(embedded);
        if (!derived.isEmpty()) return derived;
        return readBuiltinRemoteUrl();
    }

    /** 仅 HTTP GET 远程清单，失败或无效返回 null。 */
    public static VersionManifest fetchRemoteManifest(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) return null;
            VersionManifest remote = GSON.fromJson(res.body(), VersionManifest.class);
            if (remote == null || remote.versions == null || remote.versions.isEmpty()) return null;
            for (GameVersion gv : remote.versions) {
                if (gv.id == null || gv.id.isBlank()) return null;
            }
            VersionManifest out = new VersionManifest();
            out.remoteManifestUrl = remote.remoteManifestUrl != null && !remote.remoteManifestUrl.isBlank()
                    ? remote.remoteManifestUrl.trim()
                    : url.trim();
            out.githubRepo = remote.githubRepo != null ? remote.githubRepo : "";
            out.githubBranch = remote.githubBranch != null ? remote.githubBranch : "";
            out.versions = new ArrayList<>(remote.versions);
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 联网拉取远程清单；成功则<strong>完全采用远程版本列表</strong>（新条目也会出现）。
     * 失败则返回内置清单。
     */
    public static VersionManifest loadOnlineFirst(String userSettingsUrl) {
        VersionManifest embedded = loadEmbedded();
        String url = resolveRemoteManifestUrl(userSettingsUrl);
        if (url.isEmpty()) return embedded;
        VersionManifest remote = fetchRemoteManifest(url);
        if (remote != null) return remote;
        return embedded;
    }

    /** @deprecated 保留兼容；请使用 {@link #loadOnlineFirst(String)} */
    @Deprecated
    public static VersionManifest loadMerged(String remoteOverride) {
        return loadOnlineFirst(remoteOverride != null ? remoteOverride : "");
    }
}
