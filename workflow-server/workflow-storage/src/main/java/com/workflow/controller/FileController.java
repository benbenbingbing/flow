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

    /** 文件存储策略工厂 */
    private final FileStorageFactory storageFactory;

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return 文件信息（url、filename 等）或错误信息
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
     *
     * @param file     上传的图片文件
     * @param maxWidth  最大宽度，默认 1920
     * @param quality   压缩质量，默认 0.8
     * @return 文件信息或错误信息
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
     *
     * @param fileUrl 文件访问URL
     * @return 删除成功返回成功结果，否则返回错误信息
     */
    @PostMapping
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
     *
     * @param fileUrl  文件访问URL
     * @param response HTTP 响应，用于写出文件流
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

            // 以缓冲流方式写出文件内容
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
     * @param file      待检查文件
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
