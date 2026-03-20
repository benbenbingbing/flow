package com.workflow.controller;

import com.workflow.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.access.url:http://localhost:8088/uploads}")
    private String accessUrl;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        try {
            // 创建上传目录
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 生成文件名：时间戳_UUID.扩展名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            // 保存文件
            File destFile = new File(uploadDir, newFilename);
            file.transferTo(destFile);

            // 返回文件URL
            String fileUrl = accessUrl + "/" + newFilename;
            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", newFilename);
            result.put("originalName", originalFilename);
            result.put("size", String.valueOf(file.getSize()));

            log.info("文件上传成功: {}", newFilename);
            return Result.success(result);

        } catch (IOException e) {
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
        
        // 验证是否为图片
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
            // 从URL中提取文件名，并防止路径遍历攻击
            String filename = extractSafeFilename(fileUrl);
            if (filename == null) {
                return Result.error("非法的文件路径");
            }
            
            File file = new File(uploadPath, filename);
            
            // 安全检查：确保文件在指定目录内
            if (!isFileInsideDirectory(file, uploadPath)) {
                return Result.error("非法的文件路径");
            }
            
            if (file.exists() && file.delete()) {
                log.info("文件删除成功: {}", filename);
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
     */
    @GetMapping("/preview")
    public void previewFile(@RequestParam("url") String fileUrl, HttpServletResponse response) {
        try {
            // 提取安全的文件名，防止路径遍历攻击
            String filename = extractSafeFilename(fileUrl);
            if (filename == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            File file = new File(uploadPath, filename);
            
            // 安全检查：确保文件在指定目录内
            if (!isFileInsideDirectory(file, uploadPath)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            if (!file.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=" 
                    + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            response.setContentLength((int) file.length());

            // 写入响应流
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
     * 提取安全的文件名，防止路径遍历攻击
     * @param fileUrl 文件URL
     * @return 安全的文件名，如果非法则返回null
     */
    private String extractSafeFilename(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        
        String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        
        // 检查文件名是否包含非法字符
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return null;
        }
        
        return filename;
    }

    /**
     * 检查文件是否在指定目录内
     * @param file 待检查的文件
     * @param directory 允许的目录
     * @return 是否在目录内
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
