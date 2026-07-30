package com.workflow.storage.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.security.context.UserContext;
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
    private final CurrentUserRoleService currentUserRoleService;

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
                idempotencyKey);
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
            inserted = jdbcTemplate.update("""
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
                    now);
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
                claim.idempotencyKey());
        if (existing == null) {
            throw new IllegalStateException("文件幂等记录读取失败");
        }
        validateReplay(existing, claim.requestHash());
        return new UploadRegistration(existing.response(), false);
    }

    @Transactional(readOnly = true)
    public void requireRead(String storageUrl) {
        requireOwnerOrAdministrator(storageUrl);
    }

    @Transactional(readOnly = true)
    public void requireDelete(String storageUrl) {
        requireOwnerOrAdministrator(storageUrl);
    }

    @Transactional
    public void markDeleted(String storageUrl) {
        jdbcTemplate.update("""
                UPDATE storage_file_object
                SET deleted = 1,
                    update_time = UTC_TIMESTAMP(6)
                WHERE storage_url = ?
                  AND deleted = 0
                """,
                storageUrl);
    }

    private void requireOwnerOrAdministrator(String storageUrl) {
        String owner = jdbcTemplate.query("""
                SELECT owner_user_id
                FROM storage_file_object
                WHERE storage_url = ?
                  AND deleted = 0
                LIMIT 1
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

    private String currentUserId() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new ForbiddenException("用户未登录");
        }
        return userId;
    }

    private StoredUpload findByIdempotencyKey(
            String owner,
            String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT storage_url, storage_key, original_name,
                       content_length, request_hash, deleted
                FROM storage_file_object
                WHERE owner_user_id = ?
                  AND idempotency_key = ?
                LIMIT 1
                """,
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

    public record UploadClaim(
            String ownerUserId,
            String idempotencyKey,
            String requestHash,
            Map<String, String> replay) {

        static UploadClaim unkeyed(String ownerUserId) {
            return new UploadClaim(ownerUserId, null, null, null);
        }

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

    public record UploadRegistration(
            Map<String, String> response,
            boolean currentObjectRegistered) {
    }

    private record StoredUpload(
            String requestHash,
            boolean deleted,
            Map<String, String> response) {
    }
}
