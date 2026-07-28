package com.workflow.storage.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.storage.application.FileStorageFactory;
import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件上传控制器
 * 当前使用本地文件存储策略。
 */
@Slf4j
@RequiresPermission("storage:file:read")
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
    @RequiresPermission("storage:file:write")
    @SystemAudit(
            module = AuditModule.STORAGE,
            action = AuditAction.UPLOAD,
            operation = "上传文件",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "FILE")
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
    @RequiresPermission("storage:file:write")
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
    @RequiresPermission("storage:file:delete")
    @SystemAudit(
            module = AuditModule.STORAGE,
            action = AuditAction.DELETE,
            operation = "删除文件",
            risk = AuditRiskLevel.HIGH,
            targetType = "FILE",
            targetIdArg = 0)
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
        FileStorageStrategy strategy = storageFactory.getStrategy();
        try (StoredFile file = strategy.open(fileUrl)) {
            response.setContentType(file.contentType());
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename="
                    + URLEncoder.encode(
                            file.filename(),
                            StandardCharsets.UTF_8));
            if (file.contentLength() >= 0) {
                response.setContentLengthLong(file.contentLength());
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead =
                    file.inputStream().read(buffer)) != -1) {
                response.getOutputStream()
                        .write(buffer, 0, bytesRead);
            }
        } catch (FileNotFoundException e) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (IOException e) {
            log.error("文件预览失败", e);
            if (!response.isCommitted()) {
                response.setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (RuntimeException e) {
            log.error("文件存储后端不可用", e);
            if (!response.isCommitted()) {
                response.setStatus(
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        }
    }
}
