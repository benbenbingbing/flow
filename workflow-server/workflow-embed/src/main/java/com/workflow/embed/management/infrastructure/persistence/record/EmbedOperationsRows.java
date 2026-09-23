package com.workflow.embed.management.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Embed 运维查询专用数据库投影，刻意不包含任何 code/token/subject/context 列。 */
public final class EmbedOperationsRows {

    /**
     * 初始化嵌入式操作集合行，保存构造参数供后续方法使用。
     */
    private EmbedOperationsRows() {
    }

    /**
     * 封装启动记录行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理启动记录行时定位或关联目标
     * @param grantId 授权ID，后续用于处理启动记录行时定位或关联目标
     * @param viewId 视图ID，后续用于处理启动记录行时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理启动记录行时定位或关联目标
     * @param status 状态标识，决定后续启动记录行采用的处理分支
     * @param entryMode 入口模式标识，决定后续启动记录行采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param consumedAt {@code consumed}时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record LaunchRow(
            String id,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            LocalDateTime expiresAt,
            LocalDateTime consumedAt,
            LocalDateTime revokedAt,
            LocalDateTime createTime) {
    }

    /**
     * 封装会话行的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param launchId 启动记录ID，后续用于处理会话行时定位或关联目标
     * @param applicationId 应用ID，后续用于处理会话行时定位或关联目标
     * @param grantId 授权ID，后续用于处理会话行时定位或关联目标
     * @param viewId 视图ID，后续用于处理会话行时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理会话行时定位或关联目标
     * @param status 状态标识，决定后续会话行采用的处理分支
     * @param entryMode 入口模式标识，决定后续会话行采用的处理分支
     * @param issuedAt 已签发时间，后续用于判断有效期或展示该事件的发生时间
     * @param lastSeenAt 最后已见时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokeReason 撤销原因，保存在对象中供后续校验、查询或展示
     */
    public record SessionRow(
            String id,
            String launchId,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            LocalDateTime issuedAt,
            LocalDateTime lastSeenAt,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt,
            LocalDateTime revokedAt,
            String revokeReason) {
    }
}
