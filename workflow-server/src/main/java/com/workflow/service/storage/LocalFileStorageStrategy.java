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

    private final FileStorageProperties properties;

    @Override
    public Map<String, String> upload(MultipartFile file) {
        try {
            String uploadPath = properties.getLocal().getPath();
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
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

    @Override
    public boolean delete(String fileUrl) {
        try {
            String filename = extractSafeFilename(fileUrl);
            if (filename == null) {
                return false;
            }
            File file = new File(properties.getLocal().getPath(), filename);
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

    @Override
    public String getAccessUrl(String filename) {
        return properties.getLocal().getAccessUrl() + "/" + filename;
    }

    @Override
    public String getStorageType() {
        return "local";
    }

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
