package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.access.url:/uploads}")
    private String accessUrl;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        try {
            // 确保上传目录存在
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString().replace("-", "") + extension;

            // 保存文件
            Path targetPath = uploadDir.resolve(newFilename);
            file.transferTo(targetPath.toFile());

            // 返回访问URL
            String fileUrl = accessUrl + "/" + newFilename;
            log.info("文件上传成功: {}, 原始文件名: {}", fileUrl, originalFilename);

            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/download/{filename}")
    public void download(@PathVariable String filename, HttpServletResponse response) {
        try {
            Path filePath = Paths.get(uploadPath).resolve(filename);
            File file = filePath.toFile();

            if (!file.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, "UTF-8"));
            response.setContentLength((int) file.length());

            // 写入响应流
            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            log.error("文件下载失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam String filename) {
        try {
            Path filePath = Paths.get(uploadPath).resolve(filename);
            Files.deleteIfExists(filePath);
            log.info("文件删除成功: {}", filename);
            return Result.success();
        } catch (IOException e) {
            log.error("文件删除失败", e);
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }
}
