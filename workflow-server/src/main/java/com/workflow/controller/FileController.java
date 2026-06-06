package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.service.storage.FileStorageFactory;
import com.workflow.service.storage.FileStorageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件上传控制器
 * 当前使用本地文件存储策略。
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageFactory storageFactory;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }
        try {
            FileStorageStrategy strategy = storageFactory.getStrategy();
            Map<String, String> result = strategy.upload(file);
            return Result.success(result);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传图片（支持压缩）
     */
    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "maxWidth", defaultValue = "1920") int maxWidth,
            @RequestParam(value = "quality", defaultValue = "0.8") float quality) {

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只能上传图片文件");
        }
        // 目前使用与普通上传相同的逻辑，压缩功能可以后续添加
        return uploadFile(file);
    }

    /**
     * 删除文件
     */
    @DeleteMapping
    public Result<Void> deleteFile(@RequestParam("url") String fileUrl) {
        try {
            FileStorageStrategy strategy = storageFactory.getStrategy();
            boolean success = strategy.delete(fileUrl);
            if (success) {
                return Result.success();
            } else {
                return Result.error("文件不存在或删除失败");
            }
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 预览/下载文件
     * 本地存储模式下直接读取文件流
     */
    @GetMapping("/preview")
    public void previewFile(@RequestParam("url") String fileUrl, HttpServletResponse response) {
        try {
            String filename = extractSafeFilename(fileUrl);
            if (filename == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // 本地存储模式下直接读取文件
            File file = new File("./uploads", filename);
            if (!isFileInsideDirectory(file, "./uploads")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            if (!file.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename="
                    + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            response.setContentLength((int) file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            log.error("文件预览失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
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
