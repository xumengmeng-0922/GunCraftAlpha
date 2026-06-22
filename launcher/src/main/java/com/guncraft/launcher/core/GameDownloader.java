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
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GameDownloader {

    private GameDownloader() {}

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

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(u)).GET().timeout(Duration.ofMinutes(10)).build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() / 100 != 2)
            throw new IllegalStateException("HTTP " + res.statusCode());
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
