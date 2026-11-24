package com.workflow.storage.application;

import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.storage.application.error.FileUploadIdempotencyException;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import com.workflow.storage.infrastructure.persistence.mapper.StoredFileMapper;
import com.workflow.storage.infrastructure.web.MultipartFileUploadAdapter;
import com.workflow.core.database.mybatis.ParameterizedSql;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Object-level ownership enforcement for stored files.
 */
@Service
@RequiredArgsConstructor
public class StoredFileAccessService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "[\\x21-\\x7E]{1,128}");

    private final StoredFileMapper fileMapper;
    private final CurrentActorPort currentActor;
    private final JdbcWriteAttempt writeAttempt;
    private final DatabaseQueryDialect queryDialect;

    /**
     * 准备上传；结果供调用方的后续步骤使用。
     *
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param file 文件，作为 {@code requestHash} 的输入影响后续处理
     * @return 准备后的上传结果，供调用方继续处理
     */
    public UploadClaim prepareUpload(
            String idempotencyKey,
            MultipartFile file) {
        return prepareUpload(idempotencyKey, MultipartFileUploadAdapter.from(file));
    }

    /** 以文件内容和公开元数据生成幂等摘要，HTTP 和 Java 上传共享同一登记键。 */
    public UploadClaim prepareUpload(String idempotencyKey, FileUpload file) {
        return prepareUploadForOwner(idempotencyKey, file, currentUserId());
    }

    /** 用已授权的归属账号登记上传，以同一账号和幂等键隔离重复请求。 */
    UploadClaim prepareUploadForOwner(String idempotencyKey, FileUpload file, String owner) {
        if (!StringUtils.hasText(owner)) throw new IllegalArgumentException("文件归属账号不能为空");
        if (idempotencyKey == null) {
            return UploadClaim.unkeyed(owner);
        }
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new FileUploadIdempotencyException(
                    400,
                    "Idempotency-Key 必须为 1 到 128 个非空格可打印 ASCII 字符");
        }
        String requestHash = requestHash(file);
        StoredUpload existing = findByIdempotencyKey(
                owner,
                idempotencyKey, false);
        if (existing != null) {
            validateReplay(existing, requestHash);
            return UploadClaim.replay(
                    owner,
                    idempotencyKey,
                    requestHash,
                    existing.response());
        }
        return UploadClaim.pending(
                owner,
                idempotencyKey,
                requestHash);
    }

    /**
     * 登记存储对象归属并返回公开文件引用；并发幂等冲突时复用先提交的文件 ID。
     *
     * @param stored 存储策略返回的内部 url、filename，供登记与失败清理使用
     * @param file 原始上传文件，用于保存文件名、媒体类型与大小
     * @param claim 上传前取得的用户和幂等认领，必须属于本次上传
     * @return 面向前端的 fileId、originalName、size，以及本次对象是否登记成功
     * @throws IllegalStateException 存储策略缺少内部标识或文件登记失败
     */
    @Transactional
    public UploadRegistration register(
            Map<String, String> stored,
            MultipartFile file,
            UploadClaim claim) {
        return register(stored, MultipartFileUploadAdapter.from(file), claim);
    }

    /** 登记文件所有权。并发重放返回胜出的文件 ID，调用方清理重复上传的对象。 */
    @Transactional
    public UploadRegistration register(Map<String, String> stored, FileUpload file, UploadClaim claim) {
        if (claim.replay() != null) {
            return new UploadRegistration(claim.replay(), false);
        }
        String url = stored.get("url");
        String storageKey = stored.get("filename");
        if (!StringUtils.hasText(url) || !StringUtils.hasText(storageKey)) {
            throw new IllegalStateException("存储后端未返回文件标识");
        }
        int inserted;
        String fileId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        try {
            inserted = writeAttempt.execute(() -> fileMapper.execute(ParameterizedSql.of("""
                    INSERT INTO storage_file_object (
                      id, storage_url, storage_key, owner_user_id,
                      idempotency_key, request_hash,
                      original_name, content_type, content_length,
                      deleted, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """,
                    fileId,
                    url,
                    storageKey,
                    claim.ownerUserId(),
                    claim.idempotencyKey(),
                    claim.requestHash(),
                    file.originalFilename(),
                    file.contentType(),
                    file.size(),
                    now,
                    now)));
        } catch (DuplicateKeyException exception) {
            if (claim.idempotencyKey() == null) {
                throw exception;
            }
            inserted = 0;
        }
        if (inserted == 1) {
            // 存储策略的 url / filename 只用于登记和清理，禁止进入面向前端的响应。
            return new UploadRegistration(uploadResponse(fileId, file.originalFilename(), file.size()), true);
        }
        if (claim.idempotencyKey() == null) {
            throw new IllegalStateException("文件登记失败");
        }
        StoredUpload existing = findByIdempotencyKey(
                claim.ownerUserId(),
                claim.idempotencyKey(), true);
        if (existing == null) {
            throw new IllegalStateException("文件幂等记录读取失败");
        }
        validateReplay(existing, claim.requestHash());
        return new UploadRegistration(existing.response(), false);
    }

    /**
     * 校验文件归属并返回后端存储定位符，调用方只能在成功鉴权后读取对象。
     *
     * @param fileId 上传登记返回的业务文件 ID，不接受对象地址
     * @return 仅供后端存储策略使用的定位符
     * @throws ForbiddenException 文件不存在、已删除或当前用户无权访问
     */
    @Transactional(readOnly = true)
    public String requireRead(String fileId) {
        return requireOwnerOrAdministrator(fileId);
    }

    /** 读取已授权文件的登记信息，存储地址仅供后端打开文件流。 */
    @Transactional(readOnly = true)
    public StoredFileMapper.StoredFileDetails requireDetails(String fileId) {
        var details = fileMapper.findDetails(fileId);
        requireOwnership(details == null ? null : details.ownerUserId());
        return details;
    }

    /** 读取已登记且未逻辑删除的文件。Port 调用方负责确认文件访问权限。 */
    @Transactional(readOnly = true)
    public StoredFileMapper.StoredFileDetails requireRegisteredDetails(String fileId) {
        var details = fileMapper.findDetails(fileId);
        if (details == null) throw new java.util.NoSuchElementException("文件不存在");
        return details;
    }

    /**
     * 校验删除归属并返回实际存储定位符，避免让客户端指定删除对象的位置。
     *
     * @param fileId 业务文件 ID
     * @return 供存储策略执行删除的内部定位符
     */
    @Transactional(readOnly = true)
    public String requireDelete(String fileId) {
        return requireOwnerOrAdministrator(fileId);
    }

    /**
     * 按文件主键逻辑删除并记录数据库 UTC 时间；与调用方事务一起提交或回滚。
     *
     * @param fileId 已通过删除归属校验的文件 ID
     */
    @Transactional
    public void markDeleted(String fileId) {
        fileMapper.execute(ParameterizedSql.of("""
                UPDATE storage_file_object
                SET deleted = 1,
                    update_time = %s
                WHERE id = ?
                  AND deleted = 0
                """.formatted(DatabaseDialects.runtime(queryDialect.vendor()).utcTimestampExpression()),
                fileId));
    }

    /**
     * 主键查找与归属校验共享同一条记录；即使猜到文件 ID 也不能绕过所有者/管理员权限。
     *
     * @param fileId 文件主键
     * @return 鉴权通过的文件内部存储地址
     */
    private String requireOwnerOrAdministrator(String fileId) {
        var reference = fileMapper.findReference(fileId);
        requireOwnership(reference == null ? null : reference.ownerUserId());
        return reference.storageUrl();
    }

    private void requireOwnership(String ownerUserId) {
        if (ownerUserId == null) {
            throw new ForbiddenException("文件不存在或无权访问");
        }
        if (!ownerUserId.equals(currentUserId()) && !currentActor.isAdministrator()) {
            throw new ForbiddenException("无权访问该文件");
        }
    }

    /**
     * 生成当前用户ID文本，供后续匹配或展示。
     *
     * @return 处理后的当前用户ID文本，供调用方比较或展示
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private String currentUserId() {
        String userId = currentActor.current().userId();
        if (!StringUtils.hasText(userId)) {
            throw new ForbiddenException("用户未登录");
        }
        return userId;
    }

    /**
     * 唯一索引保证至多一条；冲突后用读守卫取得当前结果，不升级为排他锁。
     *
     * @param owner 归属方，供本方法查询幂等键时使用
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param currentRead 当前读取，供本方法查询幂等键时使用
     * @return 符合条件的已存储上传结果，供调用方继续处理
     */
    private StoredUpload findByIdempotencyKey(
            String owner,
            String idempotencyKey, boolean currentRead) {
        var row = fileMapper.findUpload(ParameterizedSql.of("""
                SELECT id AS file_id, original_name,
                       content_length, request_hash, deleted
                FROM storage_file_object
                WHERE owner_user_id = ? AND idempotency_key = ?
                """ + (currentRead ? queryDialect.readGuardClause() : ""), owner, idempotencyKey));
        if (row == null) return null;
        Map<String, String> response = uploadResponse(row.fileId(), row.originalName(), row.contentLength());
        return new StoredUpload(row.requestHash(), row.deleted(), response);
    }

    /** 首次上传和幂等重放使用相同的公开字段，后端存储地址不属于文件接口契约。 */
    private Map<String, String> uploadResponse(String fileId, String originalName, long size) {
        Map<String, String> response = new HashMap<>();
        response.put("fileId", fileId);
        response.put("originalName", originalName);
        response.put("size", String.valueOf(size));
        return response;
    }

    /**
     * 校验重放；不满足约束时阻止后续处理。
     *
     * @param existing 已有，供本方法校验重放时使用
     * @param requestHash 请求哈希，供本方法校验重放时使用
     */
    private void validateReplay(
            StoredUpload existing,
            String requestHash) {
        if (!requestHash.equals(existing.requestHash())) {
            throw new FileUploadIdempotencyException(
                    409,
                    "同一 Idempotency-Key 不能用于不同文件");
        }
        if (existing.deleted()) {
            throw new FileUploadIdempotencyException(
                    409,
                    "该 Idempotency-Key 对应的文件已删除");
        }
    }

    /**
     * 生成请求哈希文本，供后续匹配或展示。
     *
     * @param file 文件，作为 {@code updateDigest} 的输入影响后续处理
     * @return 处理后的请求哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String requestHash(FileUpload file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, file.originalFilename());
            updateDigest(digest, file.contentType());
            updateDigest(digest, String.valueOf(file.size()));
            try (InputStream input = file.openStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "无法计算上传文件摘要",
                    exception);
        }
    }

    /**
     * 更新摘要；后续读取或执行将使用更新后的状态。
     *
     * @param digest 摘要，供本方法更新摘要时使用
     * @param value 待更新摘要的原始输入，结果供调用方继续使用
     */
    private void updateDigest(
            MessageDigest digest,
            String value) {
        byte[] bytes = value == null
                ? new byte[0]
                : value.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[] {
                (byte) (bytes.length >>> 24),
                (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8),
                (byte) bytes.length
        });
        digest.update(bytes);
    }

    /**
     * 封装上传认领的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param ownerUserId 归属方用户ID，后续用于处理上传认领时定位或关联目标
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param requestHash 请求哈希，保存在对象中供后续校验、查询或展示
     * @param replay 重放，保存在对象中供后续校验、查询或展示
     */
    public record UploadClaim(
            String ownerUserId,
            String idempotencyKey,
            String requestHash,
            Map<String, String> replay) {

        /**
         * 处理{@code unkeyed}，并将结果传给后续步骤。
         *
         * @param ownerUserId 归属方用户ID，后续用于处理{@code unkeyed}时定位或关联目标
         * @return 处理后的{@code unkeyed}结果，供调用方继续处理
         */
        static UploadClaim unkeyed(String ownerUserId) {
            return new UploadClaim(ownerUserId, null, null, null);
        }

        /**
         * 处理待处理，并将结果传给后续步骤。
         *
         * @param ownerUserId 归属方用户ID，后续用于处理待处理时定位或关联目标
         * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
         * @param requestHash 请求哈希，作为 {@code UploadClaim} 的输入影响后续处理
         * @return 处理后的待处理结果，供调用方继续处理
         */
        static UploadClaim pending(
                String ownerUserId,
                String idempotencyKey,
                String requestHash) {
            return new UploadClaim(
                    ownerUserId,
                    idempotencyKey,
                    requestHash,
                    null);
        }

        /**
         * 处理重放，并将结果传给后续步骤。
         *
         * @param ownerUserId 归属方用户ID，后续用于处理重放时定位或关联目标
         * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
         * @param requestHash 请求哈希，作为 {@code UploadClaim} 的输入影响后续处理
         * @param response 响应，作为 {@code UploadClaim} 的输入影响后续处理
         * @return 处理后的重放结果，供调用方继续处理
         */
        static UploadClaim replay(
                String ownerUserId,
                String idempotencyKey,
                String requestHash,
                Map<String, String> response) {
            return new UploadClaim(
                    ownerUserId,
                    idempotencyKey,
                    requestHash,
                    response);
        }
    }

    /**
     * 封装上传{@code registration}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param response 响应，保存在对象中供后续校验、查询或展示
     * @param currentObjectRegistered 当前对象{@code registered}，保存在对象中供后续校验、查询或展示
     */
    public record UploadRegistration(
            Map<String, String> response,
            boolean currentObjectRegistered) {
    }

    /**
     * 封装已存储上传的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param requestHash 请求哈希，保存在对象中供后续校验、查询或展示
     * @param deleted 已删除，保存在对象中供后续校验、查询或展示
     * @param response 响应，保存在对象中供后续校验、查询或展示
     */
    private record StoredUpload(
            String requestHash,
            boolean deleted,
            Map<String, String> response) {
    }
}
