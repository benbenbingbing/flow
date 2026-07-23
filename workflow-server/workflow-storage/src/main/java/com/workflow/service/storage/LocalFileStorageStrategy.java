package com.workflow.service.storage;

import com.workflow.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本地文件存储策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorageStrategy implements FileStorageStrategy {

    /** 文件存储配置属性 */
    private final FileStorageProperties properties;

    /**
     * 上传文件到本地磁盘。
     *
     * @param file 待上传的文件
     * @return 文件信息（包含 url、filename、originalName、size）
     * @throws RuntimeException 当文件写入失败时抛出
     */
    @Override
    public Map<String, String> upload(MultipartFile file) {
        try {
            String uploadPath = properties.getLocal().getPath();
            File uploadDir = new File(uploadPath);
            // 目录不存在则自动创建
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 解析原始文件名与扩展名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            // 生成时间戳+UUID 的唯一文件名，避免覆盖
            String newFilename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            File destFile = new File(uploadDir, newFilename);
            file.transferTo(destFile);

            String fileUrl = getAccessUrl(newFilename);
            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", newFilename);
            result.put("originalName", originalFilename);
            result.put("size", String.valueOf(file.getSize()));

            log.info("本地文件上传成功: {}", newFilename);
            return result;
        } catch (IOException e) {
            log.error("本地文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据访问 URL 删除本地文件。
     *
     * @param fileUrl 文件访问URL
     * @return 删除成功返回 true，文件不存在或安全检查不通过返回 false
     */
    @Override
    public boolean delete(String fileUrl) {
        try {
            String filename = extractSafeFilename(fileUrl);
            if (filename == null) {
                return false;
            }
            File file = new File(properties.getLocal().getPath(), filename);
            // 安全校验：确保文件路径未逃出存储目录
            if (!isFileInsideDirectory(file, properties.getLocal().getPath())) {
                return false;
            }
            if (file.exists() && file.delete()) {
                log.info("本地文件删除成功: {}", filename);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("本地文件删除失败", e);
            return false;
        }
    }

    /**
     * 拼接文件的访问 URL。
     *
     * @param filename 存储的文件名
     * @return 完整的访问URL
     */
    @Override
    public String getAccessUrl(String filename) {
        return properties.getLocal().getAccessUrl() + "/" + filename;
    }

    /**
     * 返回本地存储类型标识。
     *
     * @return 存储类型 "local"
     */
    @Override
    public String getStorageType() {
        return "local";
    }

    /**
     * 从访问 URL 中提取安全的文件名，过滤路径穿越字符。
     *
     * @param fileUrl 文件访问URL
     * @return 安全的文件名；包含 ..、/、\ 或为空时返回 null
     */
    private String extractSafeFilename(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return null;
        }
        return filename;
    }

    /**
     * 判断文件是否位于指定目录内（规范化路径后比较）。
     *
     * @param file     待检查文件
     * @param directory 目录路径
     * @return 文件在目录内返回 true
     */
    private boolean isFileInsideDirectory(File file, String directory) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File canonicalDir = new File(directory).getCanonicalFile();
            return canonicalFile.getPath().startsWith(canonicalDir.getPath());
        } catch (IOException e) {
            log.error("文件路径安全检查失败", e);
            return false;
        }
    }
}
