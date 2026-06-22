package com.guncraft.launcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.guncraft.launcher.core.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * PCL2 风格界面：左侧导航 + 主内容区；游戏以 zip 分发（云端下载或本机选择 zip 解压），一键启动。
 * 仅面向 Windows（数据目录、启动使用 {@code java.exe}）。
 * （PCL2 为龙腾猫跃作品，此处仅为布局与交互风格的借鉴，非其源码。）
 */
public class LauncherWindow extends JFrame {

    public static final String LAUNCHER_VERSION = "Alpha 1.3";

    private static final Color SIDEBAR = new Color(0x25, 0x25, 0x26);
    private static final Color ACCENT = new Color(0x3B, 0x8E, 0xD0);
    private static final Color ACCENT_HOVER = new Color(0x4A, 0x9E, 0xE0);
    private static final Color CARD = new Color(0x2D, 0x2D, 0x30);

    private final CardLayout cards = new CardLayout();
    private final JPanel mainArea = new JPanel(cards);
    private final JList<GameVersion> versionList = new JList<>();
    private final DefaultListModel<GameVersion> versionModel = new DefaultListModel<>();
    private final JTextArea versionDetail = new JTextArea();
    private final JProgressBar downloadBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextField remoteUrlField = new JTextField(40);
    /** 主页：选择要启动的游戏版本（与「版本与下载」列表同步） */
    private JComboBox<GameVersion> homeVersionCombo;
    private boolean suppressHomeComboEvents;

    private List<GameVersion> manifestVersions;
    private GameVersion selectedForPlay;
    /** 当前生效的清单（用于从 githubRepo + releaseTag 等推导 zip 直链） */
    private VersionManifest activeManifest = new VersionManifest();

    public LauncherWindow() {
        super("GunCraft 启动器 — " + LAUNCHER_VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 560);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(null);

        loadSettingsToField();
        applyManifest(ManifestLoader.loadEmbedded());

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(mainArea, BorderLayout.CENTER);

        mainArea.add(buildHomePanel(), "home");
        mainArea.add(buildVersionsPanel(), "versions");
        mainArea.add(buildSettingsPanel(), "settings");
        cards.show(mainArea, "home");

        setContentPane(root);
        syncHomeVersionCombo();
        refreshManifestFromNetworkAsync();
    }

    /** 应用清单到列表，尽量保留当前选中版本与「启动版本」。 */
    private void applyManifest(VersionManifest m) {
        activeManifest = m != null ? m : new VersionManifest();
        String keepListId = null;
        GameVersion sel = versionList.getSelectedValue();
        if (sel != null) keepListId = sel.id;
        String keepPlayId = selectedForPlay != null ? selectedForPlay.id : null;

        manifestVersions = m.versions != null ? m.versions : List.of();
        versionModel.clear();
        for (GameVersion v : manifestVersions) versionModel.addElement(v);

        int idx = 0;
        if (keepListId != null) {
            for (int i = 0; i < manifestVersions.size(); i++) {
                if (keepListId.equals(manifestVersions.get(i).id)) {
                    idx = i;
                    break;
                }
            }
        }
        if (!manifestVersions.isEmpty())
            versionList.setSelectedIndex(Math.min(idx, manifestVersions.size() - 1));

        selectedForPlay = null;
        if (keepPlayId != null) {
            for (GameVersion v : manifestVersions) {
                if (keepPlayId.equals(v.id)) {
                    selectedForPlay = v;
                    break;
                }
            }
        }
        if (selectedForPlay == null && !manifestVersions.isEmpty())
            selectedForPlay = manifestVersions.get(0);
        syncHomeVersionCombo();
        refreshVersionDetail();
    }

    private void syncHomeVersionCombo() {
        if (homeVersionCombo == null) return;
        suppressHomeComboEvents = true;
        try {
            homeVersionCombo.removeAllItems();
            for (GameVersion v : manifestVersions) homeVersionCombo.addItem(v);
            if (selectedForPlay != null) {
                for (int i = 0; i < homeVersionCombo.getItemCount(); i++) {
                    GameVersion gv = homeVersionCombo.getItemAt(i);
                    if (gv != null && gv.id.equals(selectedForPlay.id)) {
                        homeVersionCombo.setSelectedIndex(i);
                        return;
                    }
                }
            }
            if (homeVersionCombo.getItemCount() > 0) homeVersionCombo.setSelectedIndex(0);
        } finally {
            suppressHomeComboEvents = false;
        }
    }

    /** 启动时在后台联网拉取清单，不阻塞界面。 */
    private void refreshManifestFromNetworkAsync() {
        final String userUrl = remoteUrlField.getText().trim();
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                String url = ManifestLoader.resolveRemoteManifestUrl(userUrl);
                if (url.isEmpty())
                    return new Object[] { ManifestLoader.loadEmbedded(), "未配置远程清单，使用内置版本列表（可在设置中填写 URL）。" };
                VersionManifest remote = ManifestLoader.fetchRemoteManifest(url);
                if (remote != null)
                    return new Object[] { remote, "已从网络更新版本列表" };
                return new Object[] { ManifestLoader.loadEmbedded(),
                        "无法拉取远程清单（检查 URL、仓库是否已推送、Gist/Raw 是否可访问），已用内置列表" };
            }

            @Override
            protected void done() {
                try {
                    Object[] o = get();
                    applyManifest((VersionManifest) o[0]);
                    statusLabel.setText((String) o[1]);
                } catch (Exception ignored) {
                    statusLabel.setText("刷新版本列表时出错");
                }
            }
        }.execute();
    }

    /** 设置里保存后或手动刷新：同步拉取。 */
    private void reloadManifestSync() {
        final String userUrl = remoteUrlField.getText().trim();
        String url = ManifestLoader.resolveRemoteManifestUrl(userUrl);
        if (url.isEmpty()) {
            applyManifest(ManifestLoader.loadEmbedded());
            statusLabel.setText("已刷新：内置版本列表");
            return;
        }
        VersionManifest remote = ManifestLoader.fetchRemoteManifest(url);
        if (remote != null) {
            applyManifest(remote);
            statusLabel.setText("已刷新：远程版本列表");
        } else {
            applyManifest(ManifestLoader.loadEmbedded());
            statusLabel.setText("远程清单不可用，已加载内置列表（请检查 URL 与仓库是否已发布）");
        }
    }

    private void loadSettingsToField() {
        Path p = settingsFile();
        if (!Files.isRegularFile(p)) return;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            props.load(in);
            String u = props.getProperty("remoteManifestUrl", "");
            remoteUrlField.setText(u);
        } catch (Exception ignored) {}
    }

    private void saveSettings() {
        try {
            InstallPaths.ensureDirs();
            Properties props = new Properties();
            props.setProperty("remoteManifestUrl", remoteUrlField.getText().trim());
            Path p = settingsFile();
            Files.createDirectories(p.getParent());
            try (FileOutputStream out = new FileOutputStream(p.toFile())) {
                props.store(out, "GunCraft Launcher");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Path settingsFile() {
        return InstallPaths.dataRoot().resolve("settings.properties");
    }

    private JPanel buildSidebar() {
        JPanel bar = new JPanel();
        bar.setBackground(SIDEBAR);
        bar.setPreferredSize(new Dimension(168, 0));
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBorder(new EmptyBorder(24, 12, 24, 12));

        JLabel logo = new JLabel("GunCraft");
        logo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        logo.setForeground(Color.WHITE);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("启动器");
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        sub.setForeground(new Color(0x9A, 0x9A, 0x9A));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        bar.add(logo);
        bar.add(sub);
        bar.add(Box.createVerticalStrut(28));

        bar.add(navButton("开始游戏", () -> cards.show(mainArea, "home")));
        bar.add(Box.createVerticalStrut(8));
        bar.add(navButton("版本与下载", () -> cards.show(mainArea, "versions")));
        bar.add(Box.createVerticalStrut(8));
        bar.add(navButton("设置", () -> cards.show(mainArea, "settings")));
        bar.add(Box.createVerticalGlue());

        JLabel ver = new JLabel(LAUNCHER_VERSION);
        ver.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        ver.setForeground(new Color(0x80, 0x80, 0x80));
        ver.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.add(ver);

        return bar;
    }

    private JButton navButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(200, 36));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBackground(new Color(0x2D, 0x2D, 0x32));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        b.addActionListener(e -> action.run());
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(0x3E, 0x3E, 0x42));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(new Color(0x2D, 0x2D, 0x32));
            }
        });
        return b;
    }

    private JPanel buildHomePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(new Color(0x1E, 0x1E, 0x1E));
        p.setBorder(new EmptyBorder(40, 48, 48, 48));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("准备开始游戏");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hint = new JLabel("选择要启动的版本");
        hint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        hint.setForeground(new Color(0xA8, 0xA8, 0xA8));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(12, 0, 4, 0));

        homeVersionCombo = new JComboBox<>();
        homeVersionCombo.setMaximumSize(new Dimension(420, 32));
        homeVersionCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        homeVersionCombo.setBackground(CARD);
        homeVersionCombo.setForeground(Color.WHITE);
        homeVersionCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GameVersion gv)
                    l.setText(gv.displayName + "  [" + gv.id + "]");
                return l;
            }
        });
        homeVersionCombo.addActionListener(e -> {
            if (suppressHomeComboEvents) return;
            Object o = homeVersionCombo.getSelectedItem();
            if (o instanceof GameVersion gv) selectedForPlay = gv;
        });

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x40, 0x40, 0x45)),
                new EmptyBorder(20, 24, 24, 24)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(520, 260));

        card.add(title);
        card.add(hint);
        card.add(homeVersionCombo);
        JLabel oneVerHint = new JLabel("<html><span style='color:#666;font-size:11px'>当前仅一个版本；远程清单增加条目后，此处会出现多个选项。</span></html>");
        oneVerHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        oneVerHint.setBorder(new EmptyBorder(6, 0, 0, 0));
        card.add(oneVerHint);

        JButton play = new JButton("启动游戏");
        play.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        play.setBackground(ACCENT);
        play.setForeground(Color.WHITE);
        play.setFocusPainted(false);
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setBorder(new EmptyBorder(24, 0, 0, 0));
        play.putClientProperty("JButton.buttonType", "roundRect");
        play.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { play.setBackground(ACCENT_HOVER); }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { play.setBackground(ACCENT); }
        });
        play.addActionListener(e -> launchSelectedGame());
        play.setPreferredSize(new Dimension(280, 48));
        play.setMaximumSize(new Dimension(400, 52));
        card.add(play);

        center.add(card);
        center.add(Box.createVerticalStrut(16));
        JLabel tips = new JLabel("<html><body style='width:420px;color:#888;font-size:12px'>"
                + "若未安装版本，请先到「版本与下载」下载游戏包（清单中配置 zipUrl 或 GitHub Releases 字段即可）。<br>"
                + "WASD 移动 · 空格跳跃 · Shift 蹲下 · G 生成生物 · E 背包 · 左键射击/破坏 · 右键放泥土"
                + "</body></html>");
        tips.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(tips);

        p.add(center, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildVersionsPanel() {
        JPanel p = new JPanel(new BorderLayout(16, 8));
        p.setBackground(new Color(0x1E, 0x1E, 0x1E));
        p.setBorder(new EmptyBorder(24, 24, 24, 24));

        versionList.setModel(versionModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setBackground(CARD);
        versionList.setForeground(Color.WHITE);
        versionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GameVersion gv) {
                    l.setText(gv.displayName + "  [" + gv.id + "]");
                    l.setBorder(new EmptyBorder(6, 10, 6, 10));
                }
                return l;
            }
        });
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshVersionDetail();
        });

        JScrollPane listScroll = new JScrollPane(versionList);
        listScroll.setPreferredSize(new Dimension(260, 0));

        versionDetail.setEditable(false);
        versionDetail.setLineWrap(true);
        versionDetail.setWrapStyleWord(true);
        versionDetail.setBackground(CARD);
        versionDetail.setForeground(Color.WHITE);
        versionDetail.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setOpaque(false);
        right.add(new JScrollPane(versionDetail), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton dl = styledActionButton("下载 / 更新", this::downloadSelected);
        JButton localZip = styledActionButton("从本地 zip 安装…", this::installFromLocalZip);
        JButton usePlay = styledActionButton("设为启动版本", this::setAsPlayVersion);
        JButton refresh = styledActionButton("刷新版本列表", () -> refreshManifestFromNetworkAsync());
        actions.add(dl);
        actions.add(localZip);
        actions.add(usePlay);
        actions.add(refresh);
        right.add(actions, BorderLayout.NORTH);

        downloadBar.setStringPainted(true);
        downloadBar.setString("就绪");
        downloadBar.setVisible(false);
        right.add(downloadBar, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, right);
        split.setResizeWeight(0.35);
        split.setBorder(null);
        split.setOpaque(false);

        p.add(split, BorderLayout.CENTER);
        p.add(statusLabel, BorderLayout.SOUTH);
        return p;
    }

    private JButton styledActionButton(String text, Runnable r) {
        JButton b = new JButton(text);
        b.setBackground(new Color(0x3A, 0x3A, 0x40));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    private void refreshVersionDetail() {
        GameVersion v = versionList.getSelectedValue();
        if (v == null) {
            versionDetail.setText("");
            return;
        }
        String zip = ManifestUrls.resolvedZipUrl(v, activeManifest);
        if (zip.isEmpty())
            zip = "（无网络地址时可点「从本地 zip 安装」，选打包好的 guncraft-game-alpha-1.3.zip）";
        String inst = InstallPaths.isInstalled(v) ? "已安装" : "未安装";
        versionDetail.setText(v.description + "\n\n下载地址:\n" + zip + "\n\n状态: " + inst + "\nJAR: " + v.resolvedJarName());
    }

    private void setAsPlayVersion() {
        GameVersion v = versionList.getSelectedValue();
        if (v == null) return;
        selectedForPlay = v;
        syncHomeVersionCombo();
        statusLabel.setText("已设为启动版本: " + v.id);
    }

    private void downloadSelected() {
        GameVersion v = versionList.getSelectedValue();
        if (v == null) return;
        String zipHref = ManifestUrls.resolvedZipUrl(v, activeManifest);
        if (zipHref.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "该版本没有可用下载地址。\n\n"
                            + "请在清单中为该版本填写其一：\n"
                            + "· zipUrl（任意 HTTPS 直链），或\n"
                            + "· 根级 githubRepo（owner/repo）+ 本版本的 releaseTag + zipAssetName。\n\n"
                            + "或点「从本地 zip 安装」选择已打好的游戏 zip；开发调试也可用「设置」里「从本机导入」。",
                    "无法下载",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean localFile = zipHref.regionMatches(true, 0, "file:", 0, 5);
        downloadBar.setVisible(true);
        downloadBar.setIndeterminate(true);
        downloadBar.setString(localFile ? "解压中…" : "下载中…");
        statusLabel.setText(localFile ? "正在解压…" : "正在下载…");
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                InstallPaths.ensureDirs();
                Path dir = InstallPaths.versionDir(v.id);
                if (Files.exists(dir)) {
                    LocalBuildImporter.deleteRecursivePublic(dir);
                }
                GameDownloader.downloadZip(zipHref, dir, null);
                return null;
            }
            @Override
            protected void done() {
                downloadBar.setIndeterminate(false);
                downloadBar.setVisible(false);
                try {
                    get();
                    statusLabel.setText(localFile ? "解压完成: " + v.id : "下载完成: " + v.id);
                    refreshVersionDetail();
                    String msg = localFile ? "已从 zip 解压完成，可以点击「启动游戏」。"
                            : "下载并解压完成，可以点击「启动游戏」。";
                    JOptionPane.showMessageDialog(LauncherWindow.this, msg, "完成", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText(localFile ? "解压失败" : "下载失败");
                    JOptionPane.showMessageDialog(LauncherWindow.this,
                            (localFile ? "解压失败:\n" : "下载失败:\n") + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    /** 选择本机已打好的游戏 zip（与云端包格式相同：根目录 jar + lib/），解压到当前选中版本目录。 */
    private void installFromLocalZip() {
        GameVersion v = versionList.getSelectedValue();
        if (v == null) {
            JOptionPane.showMessageDialog(this, "请先在左侧选择一个游戏版本。", "本地 zip", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("GunCraft 游戏包 (*.zip)", "zip"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (f == null || !f.isFile()) return;
        String zipUri = f.toURI().toString();
        downloadBar.setVisible(true);
        downloadBar.setIndeterminate(true);
        downloadBar.setString("解压中…");
        statusLabel.setText("正在解压本地 zip…");
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                InstallPaths.ensureDirs();
                Path dir = InstallPaths.versionDir(v.id);
                if (Files.exists(dir)) {
                    LocalBuildImporter.deleteRecursivePublic(dir);
                }
                GameDownloader.downloadZip(zipUri, dir, null);
                return null;
            }

            @Override
            protected void done() {
                downloadBar.setIndeterminate(false);
                downloadBar.setVisible(false);
                try {
                    get();
                    statusLabel.setText("解压完成: " + v.id);
                    refreshVersionDetail();
                    JOptionPane.showMessageDialog(LauncherWindow.this,
                            "已从本地 zip 解压完成，可以点击「启动游戏」。", "完成", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("解压失败");
                    JOptionPane.showMessageDialog(LauncherWindow.this, "解压失败:\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    /** 开发者无云端包时，从本仓库 game/target 复制到安装目录。 */
    private void importSelected() {
        GameVersion v = versionList.getSelectedValue();
        if (v == null) {
            if (selectedForPlay != null) v = selectedForPlay;
            else if (!manifestVersions.isEmpty()) v = manifestVersions.get(0);
        }
        if (v == null) {
            JOptionPane.showMessageDialog(this, "没有可用版本。", "导入", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            LocalBuildImporter.importFromLocal(v);
            statusLabel.setText("已从本机导入: " + v.id);
            refreshVersionDetail();
            JOptionPane.showMessageDialog(this, "已从 game/target 复制到安装目录。", "完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "导入失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchSelectedGame() {
        if (selectedForPlay == null && !manifestVersions.isEmpty())
            selectedForPlay = manifestVersions.get(0);
        if (selectedForPlay == null) {
            JOptionPane.showMessageDialog(this, "没有可用版本。", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        GameVersion v = selectedForPlay;
        if (!InstallPaths.isInstalled(v)) {
            JOptionPane.showMessageDialog(this,
                    "该版本尚未安装。\n请先到「版本与下载」用「下载 / 更新」或「从本地 zip 安装」完成解压后再启动。",
                    "未安装",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path jar = InstallPaths.jarPath(v);
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java.exe";

        ProcessBuilder pb = new ProcessBuilder(javaBin, "-jar", jar.toAbsolutePath().toString());
        pb.directory(InstallPaths.installRoot(v).toFile());
        pb.inheritIO();
        try {
            pb.start();
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "启动失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildSettingsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x1E, 0x1E, 0x1E));
        p.setBorder(new EmptyBorder(32, 40, 40, 40));

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel t = new JLabel("设置");
        t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        t.setForeground(Color.WHITE);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel h = new JLabel("<html>远程版本清单 URL（HTTPS，JSON 格式与内嵌清单相同）<br>"
                + "<span style='color:#888'>填写一次即可：之后每次启动会自动联网拉取最新版本列表，发布新版本只需更新服务器上的 JSON，玩家无需重装启动器。</span></html>");
        h.setForeground(new Color(0xA0, 0xA0, 0xA0));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        h.setBorder(new EmptyBorder(16, 0, 8, 0));

        remoteUrlField.setMaximumSize(new Dimension(600, 28));
        remoteUrlField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton save = new JButton("保存并刷新列表");
        save.setAlignmentX(Component.LEFT_ALIGNMENT);
        save.setBorder(new EmptyBorder(16, 0, 0, 0));
        save.addActionListener(e -> {
            saveSettings();
            reloadManifestSync();
            JOptionPane.showMessageDialog(this, "已保存。" + statusLabel.getText(), "设置", JOptionPane.INFORMATION_MESSAGE);
        });

        JLabel data = new JLabel("<html>数据根目录（设置等）:<br><code style='color:#6ab0ff'>" + InstallPaths.dataRoot()
                + "</code><br><span style='color:#888;font-size:11px'>游戏解压位置：<code>…\\game\\&lt;版本 id&gt;</code></span></html>");
        data.setBorder(new EmptyBorder(24, 0, 0, 0));
        data.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel help = new JLabel("<html><body style='width:520px;color:#888;font-size:12px'>"
                + "<b>云端清单</b>：将 <code>docs/versions-manifest.json</code> 推到仓库；若 JSON 里写了 <code>githubRepo</code>（如 <code>xumengmeng-0922/GunCraftAlpha</code>），"
                + "启动器会自动拼 Raw 地址，一般不必再手填上方 URL。也可用完整 <code>zipUrl</code> 指向任意网盘直链。<br>"
                + "<b>发新版</b>：在 GitHub Releases 上传 zip，清单里改 <code>releaseTag</code> / <code>zipAssetName</code> 或 <code>zipUrl</code> 即可。<br>"
                + "<b>zip 格式</b>：根目录为游戏 jar + <code>lib/</code>（与 <code>打包游戏zip.bat</code> 产物一致）；无网时用「从本地 zip 安装」。<br>"
                + "<b>仓库</b>：清单默认指向 <code>xumengmeng-0922/GunCraftAlpha</code>；若你 Fork 了项目请改 JSON 或在此填写你的 Raw 地址。<br>"
                + "<b>打包启动器</b>：根目录 <code>打包启动器.bat</code>（JDK 17+）。"
                + "</body></html>");
        help.setBorder(new EmptyBorder(20, 0, 0, 0));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton devImport = new JButton("开发者：从本机 game/target 导入…");
        devImport.setAlignmentX(Component.LEFT_ALIGNMENT);
        devImport.setForeground(new Color(0x88, 0x88, 0x88));
        devImport.setBorder(new EmptyBorder(16, 0, 0, 0));
        devImport.addActionListener(e -> importSelected());

        box.add(t);
        box.add(h);
        box.add(remoteUrlField);
        box.add(save);
        box.add(data);
        box.add(help);
        box.add(devImport);

        p.add(box, BorderLayout.NORTH);
        return p;
    }

    public static void applyLookAndFeel() {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 8);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }
}
