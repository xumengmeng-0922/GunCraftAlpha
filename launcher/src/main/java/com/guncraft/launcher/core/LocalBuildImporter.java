package com.guncraft.launcher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * 从本机 Maven 构建目录复制 game/target 到安装目录（仅开发者调试用；正式玩家应使用云端 zipUrl 下载）。
 */
public final class LocalBuildImporter {

    private LocalBuildImporter() {}

    public static Path findGameTargetJar(String expectedJarName) {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path dir = start;
        for (int i = 0; i < 8 && dir != null; i++) {
            Path jar = dir.resolve("game").resolve("target").resolve(expectedJarName);
            if (Files.isRegularFile(jar)) return jar;
            Path alt = dir.resolve("target").resolve(expectedJarName);
            if (Files.isRegularFile(alt)) return alt;
            dir = dir.getParent();
        }
        return null;
    }

    public static void importFromLocal(GameVersion v) throws Exception {
        Path srcJar = findGameTargetJar(v.resolvedJarName());
        if (srcJar == null)
            throw new IllegalStateException("未找到本地构建: game/target/" + v.resolvedJarName());
        Path srcLib = srcJar.getParent().resolve("lib");
        if (!Files.isDirectory(srcLib))
            throw new IllegalStateException("未找到依赖目录: " + srcLib);

        Path destRoot = InstallPaths.versionDir(v.id);
        Files.createDirectories(destRoot);
        Path destJar = destRoot.resolve(v.resolvedJarName());
        Files.copy(srcJar, destJar, StandardCopyOption.REPLACE_EXISTING);

        Path destLib = destRoot.resolve("lib");
        if (Files.exists(destLib))
            deleteRecursive(destLib);
        Files.createDirectories(destLib);
        try (Stream<Path> stream = Files.walk(srcLib)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                Path rel = srcLib.relativize(p);
                Path out = destLib.resolve(rel);
                Files.createDirectories(out.getParent());
                Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void deleteRecursivePublic(Path root) throws Exception {
        deleteRecursive(root);
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            var list = s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path p : list) Files.deleteIfExists(p);
        }
    }
}
