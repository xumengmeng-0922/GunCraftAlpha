package com.guncraft.launcher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 解析用于启动游戏 jar 的 java.exe（jpackage 自带 runtime 缺失时回退到系统 JDK）。 */
public final class JavaLocator {

    private JavaLocator() {}

    public static String resolveJavaExe() {
        Set<String> tried = new LinkedHashSet<>();
        for (String c : candidatePaths()) {
            if (c == null || c.isBlank() || !tried.add(c)) continue;
            Path p = Paths.get(c);
            if (Files.isRegularFile(p))
                return p.toAbsolutePath().normalize().toString();
        }
        return "java.exe";
    }

    private static List<String> candidatePaths() {
        List<String> out = new ArrayList<>();
        addJavaHomeCandidates(out, System.getProperty("java.home"));
        addJavaHomeCandidates(out, System.getenv("JAVA_HOME"));

        String pf = System.getenv("ProgramFiles");
        if (pf != null) {
            globJdkRoots(out, Paths.get(pf, "Java"));
            globJdkRoots(out, Paths.get(pf, "Eclipse Adoptium"));
            globJdkRoots(out, Paths.get(pf, "Microsoft"));
        }
        String local = System.getenv("LocalAppData");
        if (local != null)
            globJdkRoots(out, Paths.get(local, "Programs", "Eclipse Adoptium"));

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(";")) {
                if (dir == null || dir.isBlank()) continue;
                out.add(dir.trim() + "\\java.exe");
            }
        }
        return out;
    }

    private static void addJavaHomeCandidates(List<String> out, String javaHome) {
        if (javaHome == null || javaHome.isBlank()) return;
        Path home = Paths.get(javaHome.trim());
        out.add(home.resolve("bin").resolve("java.exe").toString());
        out.add(home.resolve("jre").resolve("bin").resolve("java.exe").toString());
    }

    private static void globJdkRoots(List<String> out, Path vendorDir) {
        if (!Files.isDirectory(vendorDir)) return;
        try (var s = Files.newDirectoryStream(vendorDir, "jdk-*")) {
            for (Path p : s) {
                if (Files.isDirectory(p))
                    out.add(p.resolve("bin").resolve("java.exe").toString());
            }
        } catch (Exception ignored) {}
    }
}
