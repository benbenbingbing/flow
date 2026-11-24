package com.workflow.storage.api.web;

import com.workflow.port.platform.model.audit.AuditAction;
import com.workflow.port.platform.model.audit.AuditModule;
import com.workflow.port.platform.model.audit.AuditRiskLevel;
import com.workflow.port.platform.audit.annotation.SystemAudit;
import com.workflow.port.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
import com.workflow.port.entity.EntityFileUploadPermissionPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.storage.application.FileStorageFactory;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import com.workflow.storage.infrastructure.web.MultipartFileUploadAdapter;
import com.workflow.storage.application.error.FileUploadIdempotencyException;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.storage.application.StoredFileAccessService;
import com.workflow.storage.application.StoredFileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传控制器
 * 通过存储策略工厂选择后端，统一执行文件归属鉴权与上传幂等控制。
 */
@Slf4j
@RequiresPermission("storage:file:read")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    // 仅让浏览器直接展示静态内容，HTML、SVG 等可执行内容及未知类型均按附件返回。
    private static final Set<String> INLINE_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "image/jpeg", "image/png",
            "image/gif", "image/webp", "image/bmp", "image/avif");

    /** 文件存储策略工厂 */
    private final FileStorageFactory storageFactory;
    private final StoredFileAccessService fileAccessService;
    private final List<EntityFileUploadPermissionPort> entityUploadAuthorizationPorts;
    private final StoredFileUploadService uploads;

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @return 文件信息（fileId、originalName、size）或错误信息
     */
    @PostMapping("/upload")
    @RequiresPermission("storage:file:write")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_WRITE)
    @SystemAudit(
            module = AuditModule.STORAGE,
            action = AuditAction.UPLOAD,
            operation = "上传文件",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "FILE")
    public Result<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey) {
        return store(file, idempotencyKey);
    }

    /**
     * 从实体表单上传文件。
     *
     * <p>该入口不要求业务用户额外拥有全局存储写权限，而是委托实体模块校验
     * 当前操作的实体权限和目标文件字段。鉴权必须在
     * {@link #store(MultipartFile, String)} 的异常兜底
     * 之外完成，确保权限异常保持为 HTTP 403。</p>
     *
     * @param entityCode     实体编码
     * @param action         当前实体动作（create、update、approve）
     * @param fieldCode      目标文件字段编码
     * @param file           上传的文件
     * @param idempotencyKey 上传幂等键
     * @return 文件信息（fileId、originalName、size）或错误信息
     */
    @PostMapping("/entity/{entityCode}/upload")
    @AuthenticatedApi(objectAuthorization = true)
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_WRITE)
    @SystemAudit(
            module = AuditModule.STORAGE,
            action = AuditAction.UPLOAD,
            operation = "实体字段上传文件",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_FILE")
    public Result<Map<String, String>> uploadEntityFile(
            @PathVariable String entityCode,
            @RequestParam("action") String action,
            @RequestParam("fieldCode") String fieldCode,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey) {
        entityUploadAuthorizer().requireUpload(
                entityCode,
                action,
                fieldCode);
        return store(file, idempotencyKey);
    }

    /**
     * 解析唯一的实体上传授权实现；缺失或重复实现都失败关闭，避免退回全局上传。
     *
     * @return 处理后的实体上传{@code authorizer}结果，供调用方继续处理
     */
    private EntityFileUploadPermissionPort entityUploadAuthorizer() {
        if (entityUploadAuthorizationPorts.size() != 1) {
            throw new ForbiddenException("实体文件上传授权服务暂不可用");
        }
        return entityUploadAuthorizationPorts.get(0);
    }

    /**
     * 处理{@code store}，并将结果传给后续步骤。
     *
     * @param file 文件，作为 {@code fileAccessService.prepareUpload} 的输入影响后续处理
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code store}结果，供调用方继续处理
     */
    private Result<Map<String, String>> store(
            MultipartFile file,
            String idempotencyKey) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }
        try {
            return Result.success(uploads.store(MultipartFileUploadAdapter.from(file), idempotencyKey));
        } catch (FileUploadIdempotencyException exception) {
            return Result.error(
                    exception.getResultCode(),
                    exception.getMessage());
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败，请稍后重试");
        }
    }

    /**
     * 上传图片（支持压缩）
     *
     * @param file     上传的图片文件
     * @param maxWidth  最大宽度，默认 1920
     * @param quality   压缩质量，默认 0.8
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @return 文件信息或错误信息
     */
    @PostMapping("/upload-image")
    @RequiresPermission("storage:file:write")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_WRITE)
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "maxWidth", defaultValue = "1920") int maxWidth,
            @RequestParam(value = "quality", defaultValue = "0.8") float quality,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey) {

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只能上传图片文件");
        }
        // 目前使用与普通上传相同的逻辑，压缩功能可以后续添加
        return store(file, idempotencyKey);
    }

    /**
     * 删除文件
     *
     * @param fileId 上传登记返回的业务文件 ID
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
    public Result<Void> deleteFile(@RequestParam("fileId") String fileId) {
        String storageUrl = fileAccessService.requireDelete(fileId);
        try {
            FileStorageProvider strategy = storageFactory.getStrategy();
            boolean success = strategy.delete(storageUrl);
            if (success) {
                fileAccessService.markDeleted(fileId);
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
     * 在归属鉴权后预览文件；浏览器可展示的静态类型使用 inline，其他类型退回下载。
     *
     * @param fileId  上传登记返回的业务文件 ID
     * @param response HTTP 响应，用于写出文件流
     */
    @GetMapping("/preview")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_READ)
    public void previewFile(@RequestParam("fileId") String fileId, HttpServletResponse response) {
        streamFile(fileId, response, false);
    }

    /**
     * 在归属鉴权后下载文件，以原始文件名返回附件，不向浏览器暴露对象存储访问凭据。
     *
     * @param fileId 业务文件 ID；实际存储位置由后端解析
     * @param response 用于写出文件流；对象不存在返回 404，存储服务不可用返回 503
     */
    @GetMapping("/download")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_READ)
    public void downloadFile(@RequestParam("fileId") String fileId, HttpServletResponse response) {
        streamFile(fileId, response, true);
    }

    /**
     * 两个读取入口共享权限、存储读取和异常语义；鉴权异常交由统一异常处理保持 HTTP 403。
     * 文件以流传输，避免后端为大附件分配整份文件大小的内存。
     */
    private void streamFile(String fileId, HttpServletResponse response, boolean download) {
        String storageUrl = fileAccessService.requireRead(fileId);
        try (StoredFile file = storageFactory.getStrategy().open(storageUrl)) {
            String contentType = file.contentType() == null
                    ? "application/octet-stream" : file.contentType();
            boolean inline = !download && INLINE_CONTENT_TYPES.contains(
                    contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT));
            // 前端通过 Blob 预览时响应头不会随之保留，未知或可执行类型也需降为二进制类型。
            response.setContentType(inline ? contentType : "application/octet-stream");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                            .filename(file.filename(), StandardCharsets.UTF_8).build().toString());
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
