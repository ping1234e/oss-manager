package com.example.ossmanager;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.StringUtils;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.OSSObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * OSS 客户端封装。
 *
 * @author example
 * @date 2026/09/15
 */
public class OssClientWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OssClientWrapper.class);

    /** 目录分隔符 */
    private static final String DELIMITER = "/";

    /** 单页最大返回条数 */
    private static final int MAX_KEYS = 1000;

    /** 集合初始容量 */
    private static final int DEFAULT_CAPACITY = 16;

    /** URL 编码后需要还原的字符 */
    private static final String PLUS = "+";
    private static final String ENCODED_SPACE = "%20";
    private static final String ENCODED_SLASH_LOWER = "%2f";
    private static final String ENCODED_SLASH_UPPER = "%2F";

    private final OSS client;

    public OssClientWrapper() {
        this.client = new OSSClientBuilder().build(
                OssConfig.getEndpoint(),
                OssConfig.getAccessKeyId(),
                OssConfig.getAccessKeySecret()
        );
        LOGGER.info("OSS 客户端初始化完成, bucket={}", OssConfig.getBucketName());
    }

    public List<OssEntry> list(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);
        List<OssEntry> result = new ArrayList<>(DEFAULT_CAPACITY);

        ListObjectsRequest request = new ListObjectsRequest(OssConfig.getBucketName())
                .withPrefix(normalizedPrefix)
                .withDelimiter(DELIMITER)
                .withMaxKeys(MAX_KEYS);
        ObjectListing listing = client.listObjects(request);

        // 一级子目录
        for (String commonPrefix : listing.getCommonPrefixes()) {
            result.add(OssEntry.ofDirectory(commonPrefix));
        }

        // 当前层文件（排除深层 key）
        for (OSSObjectSummary summary : listing.getObjectSummaries()) {
            String key = summary.getKey();
            if (key.equals(normalizedPrefix)) {
                continue;
            }
            String relative = key.substring(normalizedPrefix.length());
            if (relative.contains(DELIMITER)) {
                continue;
            }
            result.add(OssEntry.ofFile(key, summary.getSize(), summary.getLastModified()));
        }
        return result;
    }
    /**
     * 按关键字递归搜索。
     * OSS 没有服务端模糊搜索，只能列出所有对象后在客户端过滤。
     *
     * @param prefix  搜索的起始前缀，为空表示根
     * @param keyword 关键字（不区分大小写）
     * @return 匹配的条目列表
     */
    public List<OssEntry> search(String prefix, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return new ArrayList<>(0);
        }

        String normalizedPrefix;
        if (prefix == null || prefix.isEmpty()) {
            normalizedPrefix = "";
        } else {
            normalizedPrefix = prefix.endsWith(DELIMITER) ? prefix : prefix + DELIMITER;
        }

        String lowerKeyword = keyword.toLowerCase();
        List<OssEntry> result = new ArrayList<>(DEFAULT_CAPACITY);

        String nextMarker = null;
        do {
            ListObjectsRequest request = new ListObjectsRequest(OssConfig.getBucketName())
                    .withPrefix(normalizedPrefix)
                    .withMaxKeys(MAX_KEYS)
                    .withMarker(nextMarker);
            ObjectListing listing = client.listObjects(request);

            for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                String key = summary.getKey();
                if (key.endsWith(DELIMITER)) {
                    continue;
                }
                if (key.toLowerCase().contains(lowerKeyword)) {
                    result.add(OssEntry.ofFile(key, summary.getSize(), summary.getLastModified()));
                }
            }

            if (listing.isTruncated()) {
                nextMarker = listing.getNextMarker();
            } else {
                nextMarker = null;
            }
        } while (nextMarker != null);

        return result;
    }
    /**
     * 上传单个文件。
     *
     * @param ossKey    OSS 对象 key
     * @param localFile 本地文件
     */
    public void upload(String ossKey, File localFile) {
        client.putObject(OssConfig.getBucketName(), ossKey, localFile);
    }

    /**
     * 递归上传文件夹。
     *
     * @param ossPrefix OSS 目标前缀
     * @param localDir  本地目录
     * @param callback  上传进度回调，可为 null
     */
    public void uploadDirectory(String ossPrefix, File localDir, ProgressCallback callback) {
        String prefix = normalizePrefix(ossPrefix);
        File[] files = localDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                uploadDirectory(prefix + file.getName() + DELIMITER, file, callback);
            } else {
                String key = prefix + file.getName();
                if (callback != null) {
                    callback.onProgress(key);
                }
                client.putObject(OssConfig.getBucketName(), key, file);
            }
        }
    }

    /**
     * 下载文件到本地。
     *
     * @param ossKey    OSS 对象 key
     * @param localFile 本地目标文件
     */
    public void download(String ossKey, File localFile) {
        client.getObject(new GetObjectRequest(OssConfig.getBucketName(), ossKey), localFile);
    }

    /**
     * 删除单个对象。
     *
     * @param ossKey OSS 对象 key
     */
    public void delete(String ossKey) {
        client.deleteObject(OssConfig.getBucketName(), ossKey);
    }

    /**
     * 批量删除对象。
     *
     * @param keys 对象 key 列表
     */
    public void deleteBatch(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        DeleteObjectsRequest request = new DeleteObjectsRequest(OssConfig.getBucketName())
                .withKeys(keys);
        client.deleteObjects(request);
    }

    /**
     * 生成公网访问 URL。
     *
     * @param ossKey OSS 对象 key
     * @return 完整 URL
     */
    public String buildPublicUrl(String ossKey) {
        String key = ossKey;
        String prefix = OssConfig.getRootPrefix();
        if (prefix != null && !prefix.isEmpty() && !key.startsWith(prefix + DELIMITER)) {
            key = prefix + DELIMITER + key;
        }
        return OssConfig.getStaticDomain() + DELIMITER + encodeKey(key);
    }

    public void shutdown() {
        if (client != null) {
            client.shutdown();
            LOGGER.info("OSS 客户端已关闭");
        }
    }

    private String normalizePrefix(String prefix) {
        String normalizedPrefix;
        if (prefix == null || prefix.isEmpty()) {
            normalizedPrefix = "";
        } else {
            normalizedPrefix = prefix.endsWith(DELIMITER) ? prefix : prefix + DELIMITER;
        }
        return normalizedPrefix;
    }

    private String encodeKey(String key) {
        try {
            return URLEncoder.encode(key, StandardCharsets.UTF_8.name())
                    .replace(PLUS, ENCODED_SPACE)
                    .replace(ENCODED_SLASH_LOWER, DELIMITER)
                    .replace(ENCODED_SLASH_UPPER, DELIMITER);
        } catch (Exception e) {
            LOGGER.warn("URL 编码失败, key={}", key, e);
            return key;
        }
    }

    /**
     * 上传进度回调。
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * 单个对象上传完成时回调。
         *
         * @param key OSS 对象 key
         */
        void onProgress(String key);
    }

    /**
     * OSS 条目（文件或目录）。
     */
    public static class OssEntry {

        private boolean directory;
        private String key;
        private long size;
        private Date lastModified;

        public static OssEntry ofDirectory(String key) {
            OssEntry entry = new OssEntry();
            entry.directory = true;
            entry.key = key;
            return entry;
        }

        public static OssEntry ofFile(String key, long size, Date lastModified) {
            OssEntry entry = new OssEntry();
            entry.directory = false;
            entry.key = key;
            entry.size = size;
            entry.lastModified = lastModified;
            return entry;
        }

        public boolean isDirectory() { return directory; }
        public String getKey()       { return key; }
        public long getSize()        { return size; }
        public Date getLastModified(){ return lastModified; }

        @Override
        public String toString() {
            return "OssEntry{" +
                    "directory=" + directory +
                    ", key='" + key + '\'' +
                    ", size=" + size +
                    ", lastModified=" + lastModified +
                    '}';
        }
    }
}