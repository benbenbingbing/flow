package com.workflow.migration.infrastructure.archive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.util.StringUtils;

/** wfpack 的 ZIP 容器实现；只处理字节与路径，资产选择和清单语义由应用层决定。 */
public final class WfpackArchive {
    private static final int MAX_ENTRY_COUNT = 500;
    private static final int MAX_ENTRY_SIZE = 20 * 1024 * 1024;
    private static final int MAX_TOTAL_SIZE = 100 * 1024 * 1024;

    private WfpackArchive() {}

    /**
     * 按输入顺序打包并固定条目时间，避免容器元数据引入不必要的内容差异。
     *
     * @param entries 已由应用层生成的相对路径与文件内容
     * @return ZIP 字节，供应用层计算包摘要并下载
     * @throws IllegalStateException 容器写入失败
     */
    public static byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(0);
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("发布包生成失败", e);
        }
    }

    /**
     * 解包并校验输入大小、条目数量、路径、重复条目和解压后大小，阻止异常归档进入资产解析。
     *
     * @param data 外部上传的 wfpack 字节
     * @return 保持归档顺序的条目内容；不写入文件系统
     * @throws IllegalArgumentException 空包、格式错误、非法路径或容量超限
     */
    public static Map<String, byte[]> unzip(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("发布包内容为空");
        }
        if (data.length > MAX_TOTAL_SIZE) {
            throw new IllegalArgumentException("发布包超过最大限制 100MB");
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int totalSize = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                validateEntryPath(path);
                if (entries.size() >= MAX_ENTRY_COUNT) {
                    throw new IllegalArgumentException("发布包文件数量超过限制");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > MAX_ENTRY_SIZE) {
                        throw new IllegalArgumentException("发布包文件超过 20MB: " + path);
                    }
                }
                totalSize += output.size();
                if (totalSize > MAX_TOTAL_SIZE) {
                    throw new IllegalArgumentException("发布包解压后超过 100MB");
                }
                if (entries.put(path, output.toByteArray()) != null) {
                    throw new IllegalArgumentException("发布包包含重复路径: " + path);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("发布包不是有效的 wfpack 文件", e);
        }
        return entries;
    }

    /** 路径仅作为包内键使用，仍拒绝绝对路径和跨目录路径，避免下游误用。 */
    private static void validateEntryPath(String path) {
        if (!StringUtils.hasText(path) || path.startsWith("/") || path.contains("../")
                || path.contains("..\\") || path.contains(":")) {
            throw new IllegalArgumentException("发布包包含非法路径: " + path);
        }
    }
}
