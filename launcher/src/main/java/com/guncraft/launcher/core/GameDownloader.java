package com.guncraft.launcher.core;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GameDownloader {

    private static final String USER_AGENT = "GunCraft-Launcher/1.4";

    private GameDownloader() {}

    private static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    private static HttpRequest buildGet(URI uri) {
        return HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build();
    }

    /** GitHub Releases 会 302 到 CDN；若 HttpClient 未自动跟随则手动跟 Location。 */
    private static HttpResponse<InputStream> getWithRedirects(HttpClient client, String startUrl) throws Exception {
        URI uri = URI.create(startUrl.trim());
        for (int hop = 0; hop < 10; hop++) {
            HttpResponse<InputStream> res = client.send(buildGet(uri), HttpResponse.BodyHandlers.ofInputStream());
            int code = res.statusCode();
            if (code / 100 == 2) return res;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                List<String> locs = res.headers().allValues("Location");
                if (locs == null || locs.isEmpty())
                    throw new IllegalStateException("HTTP " + code + " 无 Location 头");
                if (res.body() != null) res.body().close();
                uri = uri.resolve(locs.get(0).trim());
                continue;
            }
            if (res.body() != null) res.body().close();
            throw new IllegalStateException("HTTP " + code);
        }
        throw new IllegalStateException("重定向次数过多: " + startUrl);
    }

    /**
     * 将游戏包 zip 解压到版本目录。支持 {@code https?://} 下载，或 {@code file:} 本地文件（与启动器「从本地 zip 安装」一致）。
     */
    public static void downloadZip(String zipUrl, Path targetVersionDir, LongConsumer downloadedBytes) throws Exception {
        String u = zipUrl.trim();
        if (u.regionMatches(true, 0, "file:", 0, 5)) {
            URI uri = URI.create(u);
            Path zipPath = Paths.get(uri);
            if (!Files.isRegularFile(zipPath))
                throw new IllegalStateException("找不到本地 zip: " + zipPath);
            Files.createDirectories(targetVersionDir);
            if (downloadedBytes != null)
                downloadedBytes.accept(Files.size(zipPath));
            unzip(zipPath, targetVersionDir);
            return;
        }

        HttpClient client = httpClient();
        HttpResponse<InputStream> res = getWithRedirects(client, u);
        Files.createDirectories(targetVersionDir);
        Path tempZip = Files.createTempFile("guncraft-dl-", ".zip");
        try (InputStream in = res.body()) {
            long total = 0;
            byte[] buf = new byte[65536];
            int n;
            try (var out = Files.newOutputStream(tempZip)) {
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (downloadedBytes != null) downloadedBytes.accept(total);
                }
            }
        }
        unzip(tempZip, targetVersionDir);
        Files.deleteIfExists(tempZip);
    }

    private static void unzip(Path zipFile, Path destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/")) name = name.substring(1);
                Path out = destDir.resolve(name).normalize();
                if (!out.startsWith(destDir.normalize()))
                    throw new SecurityException("非法 zip 路径: " + name);
                Files.createDirectories(out.getParent());
                Files.copy(zis, out);
            }
        }
    }
}
