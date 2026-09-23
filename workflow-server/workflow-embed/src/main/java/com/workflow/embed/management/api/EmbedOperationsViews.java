package com.workflow.embed.management.api;

import java.time.Instant;
import java.util.List;

/** 运维 API 的安全响应投影；不包含任何凭据、身份 Subject、Context 或其摘要。 */
public final class EmbedOperationsViews {

    /**
     * 初始化嵌入式操作集合视图，保存构造参数供后续方法使用。
     */
    private EmbedOperationsViews() {
    }

    /**
     * 封装分页的不可变数据；各分量供后续校验、传递或结果展示使用。
     */
    public record Page<T>(List<T> items, String nextCursor) {

        /**
         * 初始化分页，保存构造参数供后续方法使用。
         *
         * @param items 条目，保存在对象中供后续校验、查询或展示
         * @param nextCursor 下一步游标，保存在对象中供后续校验、查询或展示
         */
        public Page {
            items = List.copyOf(items);
        }
    }

    /**
     * 封装启动记录视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理启动记录视图时定位或关联目标
     * @param grantId 授权ID，后续用于处理启动记录视图时定位或关联目标
     * @param viewId 视图ID，后续用于处理启动记录视图时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理启动记录视图时定位或关联目标
     * @param status 状态标识，决定后续启动记录视图采用的处理分支
     * @param entryMode 入口模式标识，决定后续启动记录视图采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param consumedAt {@code consumed}时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param createdAt 已创建时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record LaunchView(
            String id,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            Instant expiresAt,
            Instant consumedAt,
            Instant revokedAt,
            Instant createdAt) {
    }

    /**
     * 封装会话视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param launchId 启动记录ID，后续用于处理会话视图时定位或关联目标
     * @param applicationId 应用ID，后续用于处理会话视图时定位或关联目标
     * @param grantId 授权ID，后续用于处理会话视图时定位或关联目标
     * @param viewId 视图ID，后续用于处理会话视图时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理会话视图时定位或关联目标
     * @param status 状态标识，决定后续会话视图采用的处理分支
     * @param entryMode 入口模式标识，决定后续会话视图采用的处理分支
     * @param issuedAt 已签发时间，后续用于判断有效期或展示该事件的发生时间
     * @param lastSeenAt 最后已见时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokeReason 撤销原因，保存在对象中供后续校验、查询或展示
     */
    public record SessionView(
            String id,
            String launchId,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            Instant issuedAt,
            Instant lastSeenAt,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt,
            Instant revokedAt,
            String revokeReason) {
    }

    /**
     * 封装启动记录撤销视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param launch 启动记录，保存在对象中供后续校验、查询或展示
     * @param revoked 已撤销，保存在对象中供后续校验、查询或展示
     * @param idempotent 幂等，保存在对象中供后续校验、查询或展示
     */
    public record LaunchRevocationView(
            LaunchView launch,
            boolean revoked,
            boolean idempotent) {
    }

    /**
     * 封装会话撤销视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sessionId 会话ID，后续用于处理会话撤销视图时定位或关联目标
     * @param status 状态标识，决定后续会话撤销视图采用的处理分支
     * @param revoked 已撤销，保存在对象中供后续校验、查询或展示
     * @param idempotent 幂等，保存在对象中供后续校验、查询或展示
     */
    public record SessionRevocationView(
            String sessionId,
            String status,
            boolean revoked,
            boolean idempotent) {
    }

    /**
     * 封装批量操作会话撤销视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param processed {@code processed}，保存在对象中供后续校验、查询或展示
     * @param revoked 已撤销，保存在对象中供后续校验、查询或展示
     * @param alreadyTerminal {@code already}终态，保存在对象中供后续校验、查询或展示
     * @param nextCursor 下一步游标，保存在对象中供后续校验、查询或展示
     */
    public record BulkSessionRevocationView(
            int processed,
            int revoked,
            int alreadyTerminal,
            String nextCursor) {
    }
}
