package com.workflow.admin.auth.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 浏览器刷新会话的原子数据库操作。
 */
@Mapper
public interface AuthRefreshSessionMapper {

    @Insert("""
            INSERT INTO auth_refresh_session (
              id, user_id, refresh_token_hash, token_version,
              create_time, last_used_at, idle_expires_at,
              absolute_expires_at, revoked_at, revoked_reason
            ) VALUES (
              #{id}, #{userId}, #{refreshTokenHash}, #{tokenVersion},
              #{createTime}, #{lastUsedAt}, #{idleExpiresAt},
              #{absoluteExpiresAt}, NULL, NULL
            )
            """)
    int insert(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("tokenVersion") long tokenVersion,
            @Param("createTime") LocalDateTime createTime,
            @Param("lastUsedAt") LocalDateTime lastUsedAt,
            @Param("idleExpiresAt") LocalDateTime idleExpiresAt,
            @Param("absoluteExpiresAt") LocalDateTime absoluteExpiresAt);

    @Select("""
            SELECT s.id,
                   s.user_id AS userId,
                   s.refresh_token_hash AS refreshTokenHash,
                   s.token_version AS tokenVersion,
                   s.create_time AS createTime,
                   s.last_used_at AS lastUsedAt,
                   s.idle_expires_at AS idleExpiresAt,
                   s.absolute_expires_at AS absoluteExpiresAt,
                   s.revoked_at AS revokedAt,
                   s.revoked_reason AS revokedReason,
                   u.username,
                   u.status AS userStatus,
                   u.deleted AS userDeleted,
                   u.token_version AS userTokenVersion,
                   u.password_reset_required AS passwordResetRequired
              FROM auth_refresh_session s
              LEFT JOIN sys_user u ON u.id = s.user_id
             WHERE s.refresh_token_hash = #{refreshTokenHash}
            """)
    AuthRefreshSessionRecord selectByTokenHash(
            @Param("refreshTokenHash") String refreshTokenHash);

    @Select("""
            SELECT s.id,
                   s.user_id AS userId,
                   s.refresh_token_hash AS refreshTokenHash,
                   s.token_version AS tokenVersion,
                   s.create_time AS createTime,
                   s.last_used_at AS lastUsedAt,
                   s.idle_expires_at AS idleExpiresAt,
                   s.absolute_expires_at AS absoluteExpiresAt,
                   s.revoked_at AS revokedAt,
                   s.revoked_reason AS revokedReason,
                   u.username,
                   u.status AS userStatus,
                   u.deleted AS userDeleted,
                   u.token_version AS userTokenVersion,
                   u.password_reset_required AS passwordResetRequired
              FROM auth_refresh_session s
              LEFT JOIN sys_user u ON u.id = s.user_id
             WHERE s.id = #{sessionId}
            """)
    AuthRefreshSessionRecord selectById(
            @Param("sessionId") String sessionId);

    @Update("""
            UPDATE auth_refresh_session
               SET last_used_at = #{lastUsedAt},
                   idle_expires_at = #{idleExpiresAt}
             WHERE id = #{sessionId}
               AND revoked_at IS NULL
               AND idle_expires_at > #{now}
               AND absolute_expires_at > #{now}
            """)
    int touch(
            @Param("sessionId") String sessionId,
            @Param("lastUsedAt") LocalDateTime lastUsedAt,
            @Param("idleExpiresAt") LocalDateTime idleExpiresAt,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE auth_refresh_session
               SET revoked_at = #{revokedAt},
                   revoked_reason = #{reason}
             WHERE id = #{sessionId}
               AND revoked_at IS NULL
            """)
    int revokeById(
            @Param("sessionId") String sessionId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason);

    @Update("""
            UPDATE auth_refresh_session
               SET revoked_at = #{revokedAt},
                   revoked_reason = #{reason}
             WHERE user_id = #{userId}
               AND revoked_at IS NULL
            """)
    int revokeByUserId(
            @Param("userId") String userId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason);

    @Delete("""
            DELETE FROM auth_refresh_session
             WHERE absolute_expires_at < #{expiredCutoff}
                OR idle_expires_at < #{expiredCutoff}
                OR (revoked_at IS NOT NULL AND revoked_at < #{revokedCutoff})
            """)
    int deleteExpiredOrRevokedBefore(
            @Param("expiredCutoff") LocalDateTime expiredCutoff,
            @Param("revokedCutoff") LocalDateTime revokedCutoff);
}
