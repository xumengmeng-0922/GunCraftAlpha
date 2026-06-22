package com.guncraft.launcher;

import javax.swing.*;

/**
 * GunCraft 启动器入口（仅 Windows）。
 */
public class Launcher {

    public static void main(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            JOptionPane.showMessageDialog(null,
                    "GunCraft 启动器仅支持 Microsoft Windows。",
                    "GunCraft 启动器",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            LauncherWindow.applyLookAndFeel();
            new LauncherWindow().setVisible(true);
        });
    }
}
