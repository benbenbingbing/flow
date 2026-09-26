package com.workflow.storage.application;

import com.workflow.storage.application.error.FileUploadIdempotencyException;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.contracts.identity.port.CurrentAuthorizationPort;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbcTemplate;
    private final CurrentAuthorizationPort currentUserRoleService;
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
        if (idempotencyKey == null) {
            return UploadClaim.unkeyed(currentUserId());
        }
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new FileUploadIdempotencyException(
                    400,
                    "Idempotency-Key 必须为 1 到 128 个非空格可打印 ASCII 字符");
        }
        String owner = currentUserId();
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
     * 处理{@code register}，并将结果传给后续步骤。
     *
     * @param stored 已存储，作为 {@code UploadRegistration} 的输入影响后续处理
     * @param file 文件，供本方法处理{@code register}时使用
     * @param claim 认领，作为 {@code UploadRegistration} 的输入影响后续处理
     * @return 处理后的{@code register}结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Transactional
    public UploadRegistration register(
            Map<String, String> stored,
            MultipartFile file,
            UploadClaim claim) {
        if (claim.replay() != null) {
            return new UploadRegistration(claim.replay(), false);
        }
        String url = stored.get("url");
        String storageKey = stored.get("filename");
        if (!StringUtils.hasText(url) || !StringUtils.hasText(storageKey)) {
            throw new IllegalStateException("存储后端未返回文件标识");
        }
        int inserted;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        try {
            inserted = writeAttempt.execute(() -> jdbcTemplate.update("""
                    INSERT INTO storage_file_object (
                      id, storage_url, storage_key, owner_user_id,
                      idempotency_key, request_hash,
                      original_name, content_type, content_length,
                      deleted, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """,
                    UUID.randomUUID().toString().replace("-", ""),
                    url,
                    storageKey,
                    claim.ownerUserId(),
                    claim.idempotencyKey(),
                    claim.requestHash(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    now,
                    now));
        } catch (DuplicateKeyException exception) {
            if (claim.idempotencyKey() == null) {
                throw exception;
            }
            inserted = 0;
        }
        if (inserted == 1) {
            return new UploadRegistration(stored, true);
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
     * 校验并获取读取；不满足约束时阻止后续处理。
     *
     * @param storageUrl 存储URL，作为 {@code requireOwnerOrAdministrator} 的输入影响后续处理
     */
    @Transactional(readOnly = true)
    public void requireRead(String storageUrl) {
        requireOwnerOrAdministrator(storageUrl);
    }

    /**
     * 校验并获取删除；不满足约束时阻止后续处理。
     *
     * @param storageUrl 存储URL，作为 {@code requireOwnerOrAdministrator} 的输入影响后续处理
     */
    @Transactional(readOnly = true)
    public void requireDelete(String storageUrl) {
        requireOwnerOrAdministrator(storageUrl);
    }

    /**
     * 按唯一存储地址逻辑删除并记录数据库 UTC 时间；与调用方事务一起提交或回滚。
     *
     * @param storageUrl 存储URL，供本方法标记已删除时使用
     */
    @Transactional
    public void markDeleted(String storageUrl) {
        jdbcTemplate.update("""
                UPDATE storage_file_object
                SET deleted = 1,
                    update_time = %s
                WHERE storage_url = ?
                  AND deleted = 0
                """.formatted(DatabaseDialects.runtime(queryDialect.vendor()).utcTimestampExpression()),
                storageUrl);
    }

    /**
     * storage_url 唯一索引保证精确地址只返回一个所有者，保留原有删除及权限判断。
     *
     * @param storageUrl 存储URL，供本方法校验并获取归属方或管理员时使用
     */
    private void requireOwnerOrAdministrator(String storageUrl) {
        String owner = jdbcTemplate.query("""
                SELECT owner_user_id
                FROM storage_file_object
                WHERE storage_url = ?
                  AND deleted = 0
                """,
                resultSet -> resultSet.next()
                        ? resultSet.getString("owner_user_id")
                        : null,
                storageUrl);
        if (owner == null) {
            throw new ForbiddenException("文件不存在或无权访问");
        }
        if (owner.equals(currentUserId())
                || currentUserRoleService.isAdministrator()) {
            return;
        }
        throw new ForbiddenException("无权访问该文件");
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
        return jdbcTemplate.query("""
                SELECT storage_url, storage_key, original_name,
                       content_length, request_hash, deleted
                FROM storage_file_object
                WHERE owner_user_id = ?
                  AND idempotency_key = ?
                """ + (currentRead ? queryDialect.readGuardClause() : ""),
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    Map<String, String> response = new HashMap<>();
                    response.put("url", resultSet.getString("storage_url"));
                    response.put("filename", resultSet.getString("storage_key"));
                    response.put("originalName", resultSet.getString("original_name"));
                    response.put("size", String.valueOf(
                            resultSet.getLong("content_length")));
                    return new StoredUpload(
                            resultSet.getString("request_hash"),
                            resultSet.getBoolean("deleted"),
                            response);
                },
                owner,
                idempotencyKey);
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
    private String requestHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, file.getOriginalFilename());
            updateDigest(digest, file.getContentType());
            updateDigest(digest, String.valueOf(file.getSize()));
            try (InputStream input = file.getInputStream()) {
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
