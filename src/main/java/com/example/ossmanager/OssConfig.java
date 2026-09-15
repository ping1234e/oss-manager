package com.example.ossmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * OSS 配置类，从程序同目录的 config.properties 加载。
 *
 * @author example
 * @date 2026/09/15
 */
public final class OssConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(OssConfig.class);

    /** 配置文件名称 */
    private static final String CONFIG_FILE_NAME = "config.properties";

    /** 配置项 Key */
    private static final String KEY_ENDPOINT = "oss.endpoint";
    private static final String KEY_ACCESS_KEY_ID = "oss.accessKeyId";
    private static final String KEY_ACCESS_KEY_SECRET = "oss.accessKeySecret";
    private static final String KEY_BUCKET_NAME = "oss.bucketName";
    private static final String KEY_STATIC_DOMAIN = "oss.staticDomain";
    private static final String KEY_ROOT_PREFIX = "oss.rootPrefix";

    /** 默认值 */
    private static final String DEFAULT_ENDPOINT = "oss-cn-shanghai.aliyuncs.com";
    private static final String DEFAULT_STATIC_DOMAIN = "https://pic.cscec83.cn";
    private static final String DEFAULT_ROOT_PREFIX = "ossfile";

    /** 字符串常量 */
    private static final String SLASH = "/";

    private static String endpoint;
    private static String accessKeyId;
    private static String accessKeySecret;
    private static String bucketName;
    private static String staticDomain;
    private static String rootPrefix;

    private static File configFile;

    private OssConfig() {
        // 工具类禁止实例化
    }

    /**
     * 加载配置文件，程序启动时调用一次。
     */
    public static void load() {
        configFile = resolveConfigFile();
        if (!configFile.exists()) {
            LOGGER.warn("未找到配置文件，正在生成默认配置: {}", configFile.getAbsolutePath());
            writeDefaultConfig(configFile);
        }

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("读取配置文件失败: " + configFile.getAbsolutePath(), e);
        }

        endpoint = trim(properties.getProperty(KEY_ENDPOINT));
        accessKeyId = trim(properties.getProperty(KEY_ACCESS_KEY_ID));
        accessKeySecret = trim(properties.getProperty(KEY_ACCESS_KEY_SECRET));
        bucketName = trim(properties.getProperty(KEY_BUCKET_NAME));
        staticDomain = stripTrailingSlash(trim(properties.getProperty(KEY_STATIC_DOMAIN)));
        rootPrefix = stripTrailingSlash(trim(properties.getProperty(KEY_ROOT_PREFIX)));

        LOGGER.info("已加载配置: {}", configFile.getAbsolutePath());
        LOGGER.info("Endpoint={}, Bucket={}, Domain={}, RootPrefix={}",
                endpoint, bucketName, staticDomain, rootPrefix);
    }

    private static File resolveConfigFile() {
        try {
            String jarPath = OssConfig.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                return new File(jarFile.getParentFile(), CONFIG_FILE_NAME);
            }
        } catch (Exception e) {
            LOGGER.warn("解析 jar 路径失败，回退到工作目录", e);
        }
        return new File(System.getProperty("user.dir"), CONFIG_FILE_NAME);
    }

    private static void writeDefaultConfig(File file) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("# ========== 阿里云 OSS 配置 ==========\n")
                .append(KEY_ENDPOINT).append('=').append(DEFAULT_ENDPOINT).append('\n')
                .append(KEY_ACCESS_KEY_ID).append("=xxx\n")
                .append(KEY_ACCESS_KEY_SECRET).append("=xx\n")
                .append(KEY_BUCKET_NAME).append("=cscec83\n")
                .append('\n')
                .append("# 静态域名（用于生成公开访问 URL）\n")
                .append(KEY_STATIC_DOMAIN).append('=').append(DEFAULT_STATIC_DOMAIN).append('\n')
                .append('\n')
                .append("# OSS 上的根目录前缀\n")
                .append(KEY_ROOT_PREFIX).append('=').append(DEFAULT_ROOT_PREFIX).append('\n');

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            throw new IllegalStateException("生成默认配置文件失败: " + file.getAbsolutePath(), e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        if (value != null && value.endsWith(SLASH)) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String getConfigPath() {
        return configFile == null ? "(尚未加载)" : configFile.getAbsolutePath();
    }

    public static String getEndpoint()        { return endpoint; }
    public static String getAccessKeyId()     { return accessKeyId; }
    public static String getAccessKeySecret() { return accessKeySecret; }
    public static String getBucketName()      { return bucketName; }
    public static String getStaticDomain()    { return staticDomain; }
    public static String getRootPrefix()      { return rootPrefix; }
}