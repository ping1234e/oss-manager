package com.example.ossmanager.ui;


import com.example.ossmanager.OssClientWrapper;
import com.example.ossmanager.OssClientWrapper.OssEntry;
import com.example.ossmanager.OssConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
/**
 * 主窗口（Swing 版）。
 *
 * @author example
 * @date 2026/09/15
 */
public class MainFrame extends JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainFrame.class);

    private static final long serialVersionUID = 1L;

    /** 窗口标题与尺寸 */
    private static final String WINDOW_TITLE = "OSS 文件管理器 - ";
    private static final int WINDOW_WIDTH = 1100;
    private static final int WINDOW_HEIGHT = 700;
    private static final int DIVIDER_LOCATION = 280;

    /** 列名 */
    private static final String[] COLUMN_NAMES = {"名称", "类型", "大小", "最后修改"};

    /** 文本常量 */
    private static final String TEXT_DIRECTORY = "目录";
    private static final String TEXT_FILE = "文件";
    private static final String TEXT_READY = "就绪";
    private static final String TEXT_UPLOADING = "上传中: ";
    private static final String TEXT_UPLOADED = "已上传: ";
    private static final String TEXT_DOWNLOADING = "下载中: ";
    private static final String TEXT_DOWNLOADED = "已下载到: ";
    private static final String TEXT_DELETED = "已删除 ";
    private static final String TEXT_COPIED = "已复制: ";
    private static final String TEXT_CURRENT_DIR = "当前目录: ";
    private static final String TEXT_CONFIG_FILE = "    配置文件: ";

    /** 尺寸单位 */
    private static final long SIZE_UNIT_KB = 1024L;
    private static final long SIZE_UNIT_MB = 1024L * 1024L;

    private final transient OssClientWrapper ossClient = new OssClientWrapper();

    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree dirTree;

    private final DefaultTableModel tableModel;
    private final JTable fileTable;

    private final JTextField statusField;

    /** 当前 OSS 前缀 */
    private String currentPrefix = "";
    /** 本地临时缓存目录名 */
    private static final String TEMP_DIR_NAME = "oss-manager-cache";

    /** 双击行为：打开临时文件的失败提示 */
    private static final String TEXT_OPEN_FAILED = "打开文件失败: ";
    private static final String TEXT_OPENING = "正在打开: ";
    private static final String TEXT_DOWNLOADING_TEMP = "正在下载到临时目录: ";
    /** 面包屑导航栏 */
    private final JPanel breadcrumbPanel;
    /** 搜索输入框 */
    private final JTextField searchField;
    /** 表格交替行背景色 */
    private static final Color TABLE_ALTERNATE_ROW_COLOR = new Color(0xF5F7FA);

    /** 面包屑底色 */
    private static final Color BREADCRUMB_BG_COLOR = new Color(0xFAFBFC);
    public MainFrame() {
        setTitle(WINDOW_TITLE + OssConfig.getBucketName());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        // ==== 左侧目录树 ====
        rootNode = new DefaultMutableTreeNode("📦 " + OssConfig.getBucketName() + " /");
        treeModel = new DefaultTreeModel(rootNode);
        dirTree = new JTree(treeModel);
        dirTree.setRootVisible(true);
        dirTree.setShowsRootHandles(true);

        dirTree.setRowHeight(26);
        dirTree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        dirTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 单击或双击都触发加载
                TreePath path = dirTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                if (userObject instanceof PrefixNode) {
                    currentPrefix = ((PrefixNode) userObject).prefix;
                    refreshFiles();
                } else if (node == rootNode) {
                    // 点击根节点，清空右侧（或者不处理）
                    currentPrefix = null;
                    tableModel.setRowCount(0);
                    setStatus(TEXT_READY);
                }
            }
        });

        JScrollPane treePane = new JScrollPane(dirTree);
        treePane.setPreferredSize(new Dimension(DIVIDER_LOCATION, 0));

        // ==== 右侧表格 ====
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setFillsViewportHeight(true);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(420);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(180);

        fileTable.setRowHeight(28);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.putClientProperty("JTable.alternateRowColor", TABLE_ALTERNATE_ROW_COLOR);
        fileTable.putClientProperty("JTable.showHorizontalLines", Boolean.TRUE);
        fileTable.putClientProperty("JTable.showVerticalLines", Boolean.FALSE);
        fileTable.getTableHeader().setReorderingAllowed(false);
        fileTable.getTableHeader().setFont(fileTable.getTableHeader().getFont().deriveFont(Font.BOLD));

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = fileTable.getSelectedRow();
                    if (row < 0) {
                        return;
                    }
                    OssEntry entry = (OssEntry) tableModel.getValueAt(row, 0);
                    if (entry == null) {
                        return;
                    }
                    if (entry.isDirectory()) {
                        currentPrefix = entry.getKey();
                        refreshFiles();
                    } else {
                        // 双击文件 → 下载到临时目录 → 用系统默认程序打开
                        openEntry(entry);
                    }
                }
            }
        });

        JScrollPane tablePane = new JScrollPane(fileTable);

        searchField = new JTextField(18);
        searchField.setToolTipText("输入文件名关键字，回车搜索（在当前目录下）");
        // ===== FlatLaf 美化：搜索框 =====
        searchField.putClientProperty("JTextField.placeholderText", "输入文件名关键字...");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.putClientProperty("JComponent.roundRect", true);
        // ==== 顶部工具栏 ====
        JToolBar toolBar = buildToolBar();

        // ==== 底部状态栏 ====
        statusField = new JTextField(TEXT_READY);
        statusField.setEditable(false);
        statusField.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        statusField.setBackground(new Color(0xF0F2F5));
        statusField.setForeground(new Color(0x404040));
        statusField.putClientProperty("JTextField.showClearButton", false);
        statusField.putClientProperty("JComponent.outline", null);

        // ==== 面包屑 ====
        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        breadcrumbPanel.setBackground(BREADCRUMB_BG_COLOR);
        breadcrumbPanel.putClientProperty("FlatLaf.style", "border: 0,0,1,0,#E0E0E0");


        // ==== 装配 ====
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(toolBar, BorderLayout.NORTH);
        northPanel.add(breadcrumbPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(northPanel, BorderLayout.NORTH);
        rightPanel.add(tablePane, BorderLayout.CENTER);
        rightPanel.add(statusField, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePane, rightPanel);
        splitPane.setDividerLocation(DIVIDER_LOCATION);
        splitPane.setOneTouchExpandable(false);   // FlatLaf 下更清爽
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.putClientProperty("JSplitPane.style", "plain");

        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);


        // 首次加载：只刷新左侧目录树，右侧表格等点击后再加载
        refreshDirectoryTree();
        setStatus(TEXT_READY + " 请点击左侧目录查看文件");
    }
    /**
     * 刷新面包屑导航栏。
     * 显示格式： 根 / ossfile / 子目录 ...，每一段可点击。
     */
    private void refreshBreadcrumb() {
        breadcrumbPanel.removeAll();

        // 根节点
        JButton rootBtn = createBreadcrumbButton(OssConfig.getBucketName() + " /");
        rootBtn.addActionListener(e -> navigateTo(""));
        breadcrumbPanel.add(rootBtn);

        // 如果当前没有前缀，只显示根
        if (currentPrefix == null || currentPrefix.isEmpty()) {
            breadcrumbPanel.revalidate();
            breadcrumbPanel.repaint();
            return;
        }

        // 拆分路径： "ossfile/sub/" -> ["ossfile", "sub", ""]
        String[] parts = currentPrefix.split("/");
        StringBuilder accumulated = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            accumulated.append(part).append("/");

            // 分隔符
            JLabel sep = new JLabel(">");
            sep.setForeground(java.awt.Color.GRAY);
            breadcrumbPanel.add(sep);

            // 是否是最后一段（当前目录）
            boolean isLast = (i == parts.length - 1) || (i == parts.length - 2 && parts[parts.length - 1].isEmpty());

            if (isLast) {
                JLabel label = new JLabel(part);
                label.setForeground(java.awt.Color.BLACK);
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                breadcrumbPanel.add(label);
            } else {
                String targetPrefix = accumulated.toString();
                JButton btn = createBreadcrumbButton(part);
                btn.addActionListener(e -> navigateTo(targetPrefix));
                breadcrumbPanel.add(btn);
            }
        }

        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }

    private JButton createBreadcrumbButton(String text) {
        JButton btn = new JButton(text);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        btn.setContentAreaFilled(false);
        btn.setForeground(new Color(0x0A66C2));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN));
        // ⭐ FlatLaf：无边框按钮，hover 时轻微变色
        btn.putClientProperty("JButton.buttonType", "borderless");
        btn.putClientProperty("JButton.selectedBackground", new Color(0xE8F0FE));
        return btn;
    }

    /**
     * 导航到指定的 OSS 前缀。
     *
     * @param prefix OSS 前缀，空字符串表示根
     */
    private void navigateTo(String prefix) {
        currentPrefix = prefix == null ? "" : prefix;
        refreshFiles();
    }

    private JToolBar buildToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JButton btnUploadFiles = createToolButton("上传文件");
        JButton btnUploadDir = createToolButton("上传文件夹");
        JButton btnRefresh = createToolButton("刷新");
        JButton btnDownload = createToolButton("下载");
        JButton btnDelete = createToolButton("删除");
        JButton btnCopyUrl = createToolButton("复制链接");
        JButton btnOpenConfig = createToolButton("打开配置");

        btnUploadFiles.addActionListener(e -> doUploadFiles());
        btnUploadDir.addActionListener(e -> doUploadDirectory());
        btnRefresh.addActionListener(e -> refreshAll());
        btnDownload.addActionListener(e -> doDownloadSelected());
        btnDelete.addActionListener(e -> doDeleteSelected());
        btnCopyUrl.addActionListener(e -> doCopyUrl());
        btnOpenConfig.addActionListener(e -> doOpenConfig());

        toolBar.add(btnUploadFiles);
        toolBar.add(btnUploadDir);
        toolBar.add(btnRefresh);
        toolBar.addSeparator();
        toolBar.add(btnDownload);
        toolBar.add(btnDelete);
        toolBar.add(btnCopyUrl);
        toolBar.addSeparator();
        toolBar.add(btnOpenConfig);
        toolBar.addSeparator();

        JLabel searchLabel = new JLabel(" 搜索: ");
        searchLabel.setForeground(new Color(0x666666));
        toolBar.add(searchLabel);

        toolBar.add(searchField);

        JButton btnSearch = createToolButton("搜索");
        btnSearch.addActionListener(e -> doSearch());
        toolBar.add(btnSearch);

        JButton btnClearSearch = createToolButton("清除");
        btnClearSearch.addActionListener(e -> {
            searchField.setText("");
            refreshFiles();
        });
        toolBar.add(btnClearSearch);

        searchField.addActionListener(e -> doSearch());

        return toolBar;
    }

    /**
     * 创建一个扁平化工具栏按钮。
     */
    private JButton createToolButton(String text) {
        JButton btn = new JButton(text);
        btn.putClientProperty("JButton.buttonType", "toolBarButton");
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN));
        return btn;
    }
    /**
     * 按关键字搜索。
     * 当前所在目录有前缀则在其中搜索，否则在根目录搜索。
     */
    private void doSearch() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.trim().isEmpty()) {
            // 空关键字 → 直接刷新当前目录
            refreshFiles();
            return;
        }
        keyword = keyword.trim();

        String searchPrefix = (currentPrefix == null) ? "" : currentPrefix;

        try {
            setStatus("搜索中: " + keyword + "  (范围: " + (searchPrefix.isEmpty() ? "/" : searchPrefix) + ")");
            List<OssEntry> entries = ossClient.search(searchPrefix, keyword);
            renderSearchResult(entries, keyword, searchPrefix);
            setStatus("搜索完成: 匹配 " + entries.size() + " 项    关键字: " + keyword
                    + "    范围: " + (searchPrefix.isEmpty() ? "/" : searchPrefix));
        } catch (Exception e) {
            LOGGER.error("搜索失败, keyword={}, prefix={}", keyword, searchPrefix, e);
            setStatus("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 把搜索结果渲染到右侧表格。
     */
    private void renderSearchResult(List<OssEntry> entries, String keyword, String searchPrefix) {
        tableModel.setRowCount(0);
        for (OssEntry entry : entries) {
            Object[] row = new Object[]{
                    entry,
                    entry.isDirectory() ? TEXT_DIRECTORY : TEXT_FILE,
                    entry.isDirectory() ? "" : humanSize(entry.getSize()),
                    entry.getLastModified() == null ? "" : entry.getLastModified().toString()
            };
            tableModel.addRow(row);
        }
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new EntryCellRenderer());

        // 面包屑显示「搜索: 关键字」
        renderSearchBreadcrumb(keyword, searchPrefix);
    }

    /**
     * 搜索状态下显示的面包屑： 根 / 目录 / 搜索: xxx
     */
    private void renderSearchBreadcrumb(String keyword, String searchPrefix) {
        breadcrumbPanel.removeAll();

        JButton rootBtn = createBreadcrumbButton(OssConfig.getBucketName() + " /");
        rootBtn.addActionListener(e -> {
            searchField.setText("");
            navigateTo("");
        });
        breadcrumbPanel.add(rootBtn);

        if (searchPrefix != null && !searchPrefix.isEmpty()) {
            String[] parts = searchPrefix.split("/");
            StringBuilder accumulated = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                accumulated.append(part).append("/");

                JLabel sep = new JLabel(">");
                sep.setForeground(java.awt.Color.GRAY);
                breadcrumbPanel.add(sep);

                String targetPrefix = accumulated.toString();
                JButton btn = createBreadcrumbButton(part);
                btn.addActionListener(e -> {
                    searchField.setText("");
                    navigateTo(targetPrefix);
                });
                breadcrumbPanel.add(btn);
            }
        }

        JLabel sep = new JLabel(">");
        sep.setForeground(java.awt.Color.GRAY);
        breadcrumbPanel.add(sep);

        JLabel searchLabel = new JLabel("搜索: " + keyword);
        searchLabel.setForeground(new java.awt.Color(200, 80, 0));
        searchLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        breadcrumbPanel.add(searchLabel);

        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }
    // ==================== 数据刷新 ====================

    private void refreshAll() {
        refreshDirectoryTree();
        refreshFiles();
    }

    private void refreshDirectoryTree() {
        rootNode.removeAllChildren();
        try {
            List<OssEntry> entries = ossClient.list("");
            for (OssEntry entry : entries) {
                if (entry.isDirectory()) {
                    DefaultMutableTreeNode child =
                            new DefaultMutableTreeNode(new PrefixNode("📁 " + shortName(entry.getKey()), entry.getKey()));
                    rootNode.add(child);
                }
            }
            treeModel.reload();
        } catch (Exception e) {
            LOGGER.error("刷新目录树失败", e);
            setStatus("刷新目录失败: " + e.getMessage());
        }
    }

    private void refreshFiles() {
        if (currentPrefix == null) {
            tableModel.setRowCount(0);
            refreshBreadcrumb();
            setStatus(TEXT_READY);
            return;
        }
        tableModel.setRowCount(0);
        try {
            List<OssEntry> entries = ossClient.list(currentPrefix);
            for (OssEntry entry : entries) {
                Object[] row = new Object[]{
                        entry,
                        entry.isDirectory() ? TEXT_DIRECTORY : TEXT_FILE,
                        entry.isDirectory() ? "" : humanSize(entry.getSize()),
                        entry.getLastModified() == null ? "" : entry.getLastModified().toString()
                };
                tableModel.addRow(row);
            }
            // 用 entry 做第一列的显示对象，需要设置渲染器
            fileTable.getColumnModel().getColumn(0).setCellRenderer(new EntryCellRenderer());
            refreshBreadcrumb();
            setStatus(TEXT_CURRENT_DIR + (currentPrefix.isEmpty() ? "/" : currentPrefix)
                    + "    共 " + entries.size() + " 项"
                    + TEXT_CONFIG_FILE + OssConfig.getConfigPath());
        } catch (Exception e) {
            LOGGER.error("刷新文件列表失败, prefix={}", currentPrefix, e);
            setStatus("刷新失败: " + e.getMessage());
        }
    }

    // ==================== 上传 ====================

    private void doUploadFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("音频文件 (*.mp3)", "mp3"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] files = chooser.getSelectedFiles();
        if (files == null || files.length == 0) {
            return;
        }
        for (File file : files) {
            String key = buildKey(file.getName());
            try {
                setStatus(TEXT_UPLOADING + file.getName());
                ossClient.upload(key, file);
                setStatus(TEXT_UPLOADED + file.getName() + "  →  " + ossClient.buildPublicUrl(key));
            } catch (Exception e) {
                LOGGER.error("上传文件失败, key={}", key, e);
                setStatus("上传失败: " + e.getMessage());
            }
        }
        refreshFiles();
    }

    private void doUploadDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File directory = chooser.getSelectedFile();
        try {
            setStatus("正在上传文件夹: " + directory.getAbsolutePath());
            String prefix = currentPrefix == null ? "" : currentPrefix;
            ossClient.uploadDirectory(prefix, directory, this::setStatus);
            setStatus("文件夹上传完成: " + directory.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("上传文件夹失败, dir={}", directory, e);
            setStatus("上传失败: " + e.getMessage());
        }
        refreshFiles();
    }

    // ==================== 下载 ====================

    private void doDownloadSelected() {
        int[] rows = fileTable.getSelectedRows();
        for (int row : rows) {
            OssEntry entry = (OssEntry) tableModel.getValueAt(row, 0);
            if (entry != null && !entry.isDirectory()) {
                downloadEntry(entry);
            }
        }
    }

    private void downloadEntry(OssEntry entry) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择保存目录");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = new File(chooser.getSelectedFile(), shortName(entry.getKey()));
        try {
            setStatus(TEXT_DOWNLOADING + entry.getKey());
            ossClient.download(entry.getKey(), target);
            setStatus(TEXT_DOWNLOADED + target.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("下载失败, key={}", entry.getKey(), e);
            setStatus("下载失败: " + e.getMessage());
        }
    }
    private void openEntry(OssEntry entry) {
        setStatus(TEXT_DOWNLOADING_TEMP + shortName(entry.getKey()));
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return ensureLocalCache(entry);
            }

            @Override
            protected void done() {
                try {
                    File localFile = get();
                    Desktop.getDesktop().open(localFile);
                    setStatus(TEXT_OPENING + localFile.getAbsolutePath());
                } catch (Exception e) {
                    LOGGER.error("打开文件失败", e);
                    setStatus(TEXT_OPEN_FAILED + e.getMessage());
                }
            }
        }.execute();
    }
    /**
     * 确保文件在本地临时目录存在（不存在则下载）。
     *
     * @param entry OSS 条目
     * @return 本地临时文件
     * @throws IOException 下载失败
     */
    private File ensureLocalCache(OssEntry entry) throws IOException {
        Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), TEMP_DIR_NAME);
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        String fileName = shortName(entry.getKey());
        File target = cacheDir.resolve(fileName).toFile();

        // 已存在且大小一致 → 直接用缓存
        if (target.exists() && target.length() == entry.getSize()) {
            return target;
        }

        // 否则下载（覆盖旧文件）
        setStatus(TEXT_DOWNLOADING_TEMP + fileName);
        ossClient.download(entry.getKey(), target);
        return target;
    }
    // ==================== 删除 ====================

    private void doDeleteSelected() {
        int[] rows = fileTable.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定删除选中的 " + rows.length + " 项？此操作不可恢复。",
                "确认删除", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        List<String> keys = new ArrayList<>(rows.length);
        for (int row : rows) {
            OssEntry entry = (OssEntry) tableModel.getValueAt(row, 0);
            if (entry != null && !entry.isDirectory()) {
                keys.add(entry.getKey());
            }
        }
        try {
            ossClient.deleteBatch(keys);
            setStatus(TEXT_DELETED + keys.size() + " 项");
        } catch (Exception e) {
            LOGGER.error("删除失败, keys={}", keys, e);
            setStatus("删除失败: " + e.getMessage());
        }
        refreshFiles();
    }

    // ==================== 复制链接 ====================

    private void doCopyUrl() {
        int row = fileTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        OssEntry entry = (OssEntry) tableModel.getValueAt(row, 0);
        if (entry == null) {
            return;
        }
        String url = ossClient.buildPublicUrl(entry.getKey());
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(url), null);
        setStatus(TEXT_COPIED + url);
    }

    // ==================== 打开配置 ====================

    private void doOpenConfig() {
        try {
            File configFile = new File(OssConfig.getConfigPath());
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(configFile);
            } else {
                setStatus("当前系统不支持打开文件: " + configFile.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.error("打开配置文件失败", e);
            setStatus("打开配置失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusField.setText(message));
    }

    private String buildKey(String fileName) {
        if (currentPrefix == null || currentPrefix.isEmpty()) {
            return fileName;
        }
        return currentPrefix.endsWith("/") ? currentPrefix + fileName : currentPrefix + "/" + fileName;
    }
    private String shortName(String key) {
        String value = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String humanSize(long bytes) {
        if (bytes < SIZE_UNIT_KB) {
            return bytes + " B";
        }
        if (bytes < SIZE_UNIT_MB) {
            return String.format("%.2f KB", bytes / (double) SIZE_UNIT_KB);
        }
        return String.format("%.2f MB", bytes / (double) SIZE_UNIT_MB);
    }

    /**
     * 目录树节点包装。
     */
    private static class PrefixNode {
        private final String displayName;
        private final String prefix;

        PrefixNode(String displayName, String prefix) {
            this.displayName = displayName;
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * 表格第一列的自定义渲染：显示 OssEntry 的短名。
     */
    private static class EntryCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Object display = value;
            if (value instanceof OssEntry) {
                OssEntry entry = (OssEntry) value;
                String key = entry.getKey();
                String name = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
                int idx = name.lastIndexOf('/');
                name = idx >= 0 ? name.substring(idx + 1) : name;
                display = (entry.isDirectory() ? "📁 " : "📄 ") + name;
            }
            return super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, column);
        }
    }
}