package com.example.ossmanager;

import com.example.ossmanager.ui.MainFrame;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 程序入口。
 *
 * @author example
 * @date 2026/09/15
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final int EXIT_CONFIG_ERROR = 1;

    public static void main(String[] args) {
        try {
            OssConfig.load();
        } catch (Exception e) {
            LOGGER.error("配置加载失败", e);
            System.exit(EXIT_CONFIG_ERROR);
            return;
        }

        // 设置系统默认外观（比 Metal 好看，贴近当前操作系统）
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.warn("设置系统外观失败，使用默认外观", e);
        }

        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            LOGGER.warn("FlatLaf 初始化失败，使用系统默认外观", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }

        // Swing 必须在 EDT（事件分发线程）里启动
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}