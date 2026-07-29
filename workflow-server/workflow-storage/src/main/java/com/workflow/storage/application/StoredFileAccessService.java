package com.workflow.storage.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Object-level ownership enforcement for stored files.
 */
@Service
@RequiredArgsConstructor
public class StoredFileAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserRoleService currentUserRoleService;

    @Transactional
    public void register(Map<String, String> stored, MultipartFile file) {
        String owner = currentUserId();
        String url = stored.get("url");
        String storageKey = stored.get("filename");
        if (!StringUtils.hasText(url) || !StringUtils.hasText(storageKey)) {
            throw new IllegalStateException("存储后端未返回文件标识");
        }
        jdbcTemplate.update("""
                INSERT INTO storage_file_object (
                  id, storage_url, storage_key, owner_user_id,
                  original_name, content_type, content_length,
                  deleted, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0,
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """,
                UUID.randomUUID().toString().replace("-", ""),
                url,
                storageKey,
                owner,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize());
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
}
