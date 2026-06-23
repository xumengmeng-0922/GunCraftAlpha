package com.guncraft.launcher;

import com.formdev.flatlaf.FlatLightLaf;
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

    public static final String LAUNCHER_VERSION = "Alpha 1.4";

    /** PCL 风格浅/深蓝配色 */
    private static final Color BLUE_DEEP = new Color(0x2B, 0x6C, 0xAD);
    private static final Color BLUE_DARK = new Color(0x3A, 0x7E, 0xC2);
    private static final Color ACCENT = new Color(0x4A, 0x9E, 0xE0);
    private static final Color ACCENT_HOVER = new Color(0x5B, 0xAD, 0xED);
    private static final Color SIDEBAR = new Color(0xEB, 0xF4, 0xFC);
    private static final Color MAIN_BG = new Color(0xD4, 0xE8, 0xF7);
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(0x1E, 0x4A, 0x72);
    private static final Color TEXT_MUTED = new Color(0x5B, 0x7C, 0x99);
    private static final Color BORDER = new Color(0xB8, 0xD4, 0xEA);
    private static final Color NAV_IDLE = new Color(0xD8, 0xEB, 0xF9);
    private static final Color NAV_HOVER = new Color(0xC2, 0xDE, 0xF4);

    private final CardLayout cards = new CardLayout();
    private final JPanel mainArea = new JPanel(cards);
    private final JList<GameVersion> versionList = new JList<>();
    private final DefaultListModel<GameVersion> versionModel = new DefaultListModel<>();
    private final JTextArea versionDetail = new JTextArea();
    private final JProgressBar downloadBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel versionsReleaseSummary = new JLabel(" ");
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
        root.setBackground(MAIN_BG);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 12));
        topBar.setBackground(BLUE_DARK);
        topBar.setPreferredSize(new Dimension(0, 52));
        JLabel topTitle = new JLabel("GunCraft 启动器");
        topTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        topTitle.setForeground(Color.WHITE);
        topBar.add(topTitle);
        JLabel topVer = new JLabel(LAUNCHER_VERSION);
        topVer.setForeground(new Color(0xD0, 0xE8, 0xFF));
        topBar.add(topVer);
        root.add(topBar, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(buildSidebar(), BorderLayout.WEST);
        body.add(mainArea, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        mainArea.setOpaque(false);
        mainArea.setBackground(MAIN_BG);

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
        updateVersionsReleaseSummary();
    }

    private static String formatVersionListLabel(GameVersion gv) {
        if (gv == null) return "";
        String date = gv.releaseDateLabel();
        if (date.isEmpty()) return gv.displayName + "  [" + gv.id + "]";
        return gv.displayName + "  ·  " + date + "  [" + gv.id + "]";
    }

    private void updateVersionsReleaseSummary() {
        if (versionsReleaseSummary == null) return;
        StringBuilder sb = new StringBuilder("<html><body style='color:#5B7C99;font-size:12px'>");
        boolean any = false;
        for (GameVersion v : manifestVersions) {
            String date = v.releaseDateLabel();
            if (date.isEmpty()) continue;
            if (any) sb.append("<br>");
            sb.append(v.displayName).append(" 发布：").append(date);
            any = true;
        }
        if (!any) sb.append("（清单未填写发布日期）");
        sb.append("</body></html>");
        versionsReleaseSummary.setText(sb.toString());
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
        logo.setForeground(BLUE_DEEP);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("启动器");
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        sub.setForeground(TEXT_MUTED);
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
        ver.setForeground(TEXT_MUTED);
        ver.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.add(ver);

        return bar;
    }

    private JButton navButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(200, 36));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBackground(NAV_IDLE);
        b.setForeground(BLUE_DEEP);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        b.addActionListener(e -> action.run());
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(NAV_HOVER);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(NAV_IDLE);
            }
        });
        return b;
    }

    private JPanel buildHomePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(MAIN_BG);
        p.setBorder(new EmptyBorder(40, 48, 48, 48));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("准备开始游戏");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hint = new JLabel("选择要启动的版本");
        hint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        hint.setForeground(TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(12, 0, 4, 0));

        homeVersionCombo = new JComboBox<>();
        homeVersionCombo.setMaximumSize(new Dimension(420, 32));
        homeVersionCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        homeVersionCombo.setBackground(Color.WHITE);
        homeVersionCombo.setForeground(TEXT);
        homeVersionCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GameVersion gv)
                    l.setText(formatVersionListLabel(gv));
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
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(20, 24, 24, 24)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(520, 260));

        card.add(title);
        card.add(hint);
        card.add(homeVersionCombo);
        JLabel oneVerHint = new JLabel("<html><span style='color:#5B7C99;font-size:11px'>当前仅一个版本；远程清单增加条目后，此处会出现多个选项。</span></html>");
        oneVerHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        oneVerHint.setBorder(new EmptyBorder(6, 0, 0, 0));
        card.add(oneVerHint);

        JButton play = new JButton("启动游戏");
        play.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        play.setBackground(Color.WHITE);
        play.setForeground(BLUE_DARK);
        play.setFocusPainted(false);
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(12, 24, 12, 24)));
        play.putClientProperty("JButton.buttonType", "roundRect");
        play.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { play.setBackground(new Color(0xF0, 0xF8, 0xFF)); }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { play.setBackground(Color.WHITE); }
        });
        play.addActionListener(e -> launchSelectedGame());
        play.setPreferredSize(new Dimension(280, 48));
        play.setMaximumSize(new Dimension(400, 52));
        card.add(Box.createVerticalStrut(16));
        card.add(play);

        center.add(card);
        center.add(Box.createVerticalStrut(16));
        JLabel tips = new JLabel("<html><body style='width:420px;color:#5B7C99;font-size:12px'>"
                + "若未安装版本，请先到「版本与下载」下载游戏包。"
                + "</body></html>");
        tips.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(tips);

        p.add(center, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildVersionsPanel() {
        JPanel p = new JPanel(new BorderLayout(12, 8));
        p.setBackground(MAIN_BG);
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        statusLabel.setForeground(TEXT);

        versionsReleaseSummary.setForeground(TEXT_MUTED);
        versionsReleaseSummary.setBorder(new EmptyBorder(0, 0, 10, 0));

        versionList.setModel(versionModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setBackground(CARD);
        versionList.setForeground(TEXT);
        versionList.setFixedCellHeight(40);
        versionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GameVersion gv)
                    l.setText(formatVersionListLabel(gv));
                l.setBorder(new EmptyBorder(4, 10, 4, 10));
                if (isSelected) {
                    l.setBackground(ACCENT);
                    l.setForeground(Color.WHITE);
                } else {
                    l.setBackground(CARD);
                    l.setForeground(TEXT);
                }
                return l;
            }
        });
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshVersionDetail();
        });

        JScrollPane listScroll = new JScrollPane(versionList);
        listScroll.setPreferredSize(new Dimension(200, 0));
        listScroll.setMinimumSize(new Dimension(160, 120));
        listScroll.getViewport().setBackground(CARD);
        listScroll.setBorder(BorderFactory.createLineBorder(BORDER));

        JPanel listCol = new JPanel(new BorderLayout());
        listCol.setOpaque(false);
        listCol.setPreferredSize(new Dimension(200, 0));
        listCol.setMinimumSize(new Dimension(160, 0));
        listCol.add(listScroll, BorderLayout.CENTER);

        versionDetail.setEditable(false);
        versionDetail.setLineWrap(true);
        versionDetail.setWrapStyleWord(true);
        versionDetail.setBackground(CARD);
        versionDetail.setForeground(TEXT);
        versionDetail.setBorder(new EmptyBorder(12, 12, 12, 12));
        versionDetail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane detailScroll = new JScrollPane(versionDetail);
        detailScroll.getViewport().setBackground(CARD);
        detailScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        detailScroll.setMinimumSize(new Dimension(280, 120));

        JPanel actions = new JPanel(new GridLayout(2, 2, 8, 8));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(0, 0, 8, 0));
        actions.add(styledActionButton("下载 / 更新", this::downloadSelected));
        actions.add(styledActionButton("从本地 zip 安装", this::installFromLocalZip));
        actions.add(styledActionButton("设为启动版本", this::setAsPlayVersion));
        actions.add(styledActionButton("刷新版本列表", () -> refreshManifestFromNetworkAsync()));

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        right.setMinimumSize(new Dimension(320, 0));
        right.add(actions, BorderLayout.NORTH);
        right.add(detailScroll, BorderLayout.CENTER);

        downloadBar.setStringPainted(true);
        downloadBar.setString("就绪");
        downloadBar.setVisible(false);
        right.add(downloadBar, BorderLayout.SOUTH);

        p.add(listCol, BorderLayout.WEST);
        p.add(right, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(versionsReleaseSummary, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JButton styledActionButton(String text, Runnable r) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        b.addActionListener(e -> r.run());
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(ACCENT_HOVER); }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { b.setBackground(ACCENT); }
        });
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
            zip = "（无网络地址时可点「从本地 zip 安装」，选打包好的 guncraft-game-alpha-1.4.zip）";
        String inst = InstallPaths.isInstalled(v) ? "已安装" : "未安装";
        String dateLine = v.releaseDateLabel();
        if (dateLine.isEmpty()) dateLine = "（未填写）";
        versionDetail.setText(v.description + "\n\n发布时间: " + dateLine
                + "\n\n下载地址:\n" + zip + "\n\n状态: " + inst + "\nJAR: " + v.resolvedJarName());
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
        String javaBin = JavaLocator.resolveJavaExe();

        ProcessBuilder pb = new ProcessBuilder(javaBin, "-jar", jar.toAbsolutePath().toString());
        pb.directory(InstallPaths.installRoot(v).toFile());
        pb.inheritIO();
        try {
            pb.start();
            statusLabel.setText("游戏已启动：" + v.id + "（启动器保持打开）");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "启动失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildSettingsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(MAIN_BG);
        p.setBorder(new EmptyBorder(32, 40, 40, 40));

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel t = new JLabel("设置");
        t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        t.setForeground(TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel h = new JLabel("<html>远程版本清单 URL（HTTPS，JSON 格式与内嵌清单相同）<br>"
                + "<span style='color:#5B7C99'>填写一次即可：之后每次启动会自动联网拉取最新版本列表，发布新版本只需更新服务器上的 JSON，玩家无需重装启动器。</span></html>");
        h.setForeground(TEXT_MUTED);
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

        JLabel data = new JLabel("<html>数据根目录（设置等）:<br><code style='color:#2B6CAD'>" + InstallPaths.dataRoot()
                + "</code><br><span style='color:#5B7C99;font-size:11px'>游戏解压位置：<code>…\\game\\&lt;版本 id&gt;</code></span></html>");
        data.setBorder(new EmptyBorder(24, 0, 0, 0));
        data.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel help = new JLabel("<html><body style='width:520px;color:#5B7C99;font-size:12px'>"
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
        devImport.setForeground(TEXT_MUTED);
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
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("TextComponent.arc", 8);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }
}
