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

    /**
     * 插入认证刷新会话；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param refreshTokenHash 刷新令牌哈希，供本方法插入认证刷新会话时使用
     * @param tokenVersion 令牌版本，供本方法插入认证刷新会话时使用
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param lastUsedAt 最后{@code used}时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     * @return 插入后的认证刷新会话结果，供调用方继续处理
     */
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

    /**
     * 查询令牌哈希；查询结果供调用方展示或继续处理。
     *
     * @param refreshTokenHash 刷新令牌哈希，供本方法查询令牌哈希时使用
     * @return 查询后的令牌哈希结果，供调用方继续处理
     */
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

    /**
     * 查询ID；查询结果供调用方展示或继续处理。
     *
     * @param sessionId 会话ID，后续用于查询ID时定位或关联目标
     * @return 查询后的ID结果，供调用方继续处理
     */
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

    /**
     * 处理更新访问时间，并将结果传给后续步骤。
     *
     * @param sessionId 会话ID，后续用于处理更新访问时间时定位或关联目标
     * @param lastUsedAt 最后{@code used}时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，供本方法处理更新访问时间时使用
     * @return 处理后的更新访问时间结果，供调用方继续处理
     */
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

    /**
     * 撤销ID；后续读取或执行将使用更新后的状态。
     *
     * @param sessionId 会话ID，后续用于撤销ID时定位或关联目标
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param reason 原因，供本方法撤销ID时使用
     * @return 撤销后的ID结果，供调用方继续处理
     */
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

    /**
     * 撤销用户ID；后续读取或执行将使用更新后的状态。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param reason 原因，供本方法撤销用户ID时使用
     * @return 撤销后的用户ID结果，供调用方继续处理
     */
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

    /**
     * 删除过期或已撤销之前；后续读取或执行将使用更新后的状态。
     *
     * @param expiredCutoff 过期截止点，供本方法删除过期或已撤销之前时使用
     * @param revokedCutoff 已撤销截止点，供本方法删除过期或已撤销之前时使用
     * @return 删除后的过期或已撤销之前结果，供调用方继续处理
     */
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
