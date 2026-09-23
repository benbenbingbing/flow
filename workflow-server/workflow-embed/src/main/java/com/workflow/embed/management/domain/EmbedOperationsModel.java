package com.workflow.embed.management.domain;

import java.time.Instant;
import java.util.List;

/** Embed Launch/Session 运维查询与撤销用例的安全投影。 */
public final class EmbedOperationsModel {

    /**
     * 初始化嵌入式操作集合模型，保存构造参数供后续方法使用。
     */
    private EmbedOperationsModel() {
    }

    /**
     * 封装启动记录查询的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理启动记录查询时定位或关联目标
     * @param viewId 视图ID，后续用于处理启动记录查询时定位或关联目标
     * @param status 状态标识，决定后续启动记录查询采用的处理分支
     * @param createdFrom 已创建起始，保存在对象中供后续校验、查询或展示
     * @param createdTo 已创建截止，保存在对象中供后续校验、查询或展示
     * @param cursor 游标，保存在对象中供后续校验、查询或展示
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     */
    public record LaunchQuery(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            String cursor,
            Integer limit) {
    }

    /**
     * 封装会话查询的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param applicationId 应用ID，后续用于处理会话查询时定位或关联目标
     * @param viewId 视图ID，后续用于处理会话查询时定位或关联目标
     * @param status 状态标识，决定后续会话查询采用的处理分支
     * @param createdFrom 已创建起始，保存在对象中供后续校验、查询或展示
     * @param createdTo 已创建截止，保存在对象中供后续校验、查询或展示
     * @param cursor 游标，保存在对象中供后续校验、查询或展示
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     */
    public record SessionQuery(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            String cursor,
            Integer limit) {
    }

    /**
     * 封装启动记录摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param applicationId 应用ID，后续用于处理启动记录摘要时定位或关联目标
     * @param grantId 授权ID，后续用于处理启动记录摘要时定位或关联目标
     * @param viewId 视图ID，后续用于处理启动记录摘要时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理启动记录摘要时定位或关联目标
     * @param status 状态标识，决定后续启动记录摘要采用的处理分支
     * @param entryMode 入口模式标识，决定后续启动记录摘要采用的处理分支
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param consumedAt {@code consumed}时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record LaunchSummary(
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
            Instant createTime) {
    }

    /**
     * 封装会话摘要的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param launchId 启动记录ID，后续用于处理会话摘要时定位或关联目标
     * @param applicationId 应用ID，后续用于处理会话摘要时定位或关联目标
     * @param grantId 授权ID，后续用于处理会话摘要时定位或关联目标
     * @param viewId 视图ID，后续用于处理会话摘要时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于处理会话摘要时定位或关联目标
     * @param status 状态标识，决定后续会话摘要采用的处理分支
     * @param entryMode 入口模式标识，决定后续会话摘要采用的处理分支
     * @param issuedAt 已签发时间，后续用于判断有效期或展示该事件的发生时间
     * @param lastSeenAt 最后已见时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokeReason 撤销原因，保存在对象中供后续校验、查询或展示
     */
    public record SessionSummary(
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
     * 封装启动记录撤销的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param launch 启动记录，保存在对象中供后续校验、查询或展示
     * @param revoked 已撤销，保存在对象中供后续校验、查询或展示
     * @param idempotent 幂等，保存在对象中供后续校验、查询或展示
     */
    public record LaunchRevocation(
            LaunchSummary launch,
            boolean revoked,
            boolean idempotent) {
    }

    /**
     * 封装会话撤销的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sessionId 会话ID，后续用于处理会话撤销时定位或关联目标
     * @param status 状态标识，决定后续会话撤销采用的处理分支
     * @param revoked 已撤销，保存在对象中供后续校验、查询或展示
     * @param idempotent 幂等，保存在对象中供后续校验、查询或展示
     */
    public record SessionRevocation(
            String sessionId,
            String status,
            boolean revoked,
            boolean idempotent) {
    }

    /**
     * 封装批量操作会话撤销的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param processed {@code processed}，保存在对象中供后续校验、查询或展示
     * @param revoked 已撤销，保存在对象中供后续校验、查询或展示
     * @param alreadyTerminal {@code already}终态，保存在对象中供后续校验、查询或展示
     * @param nextCursor 下一步游标，保存在对象中供后续校验、查询或展示
     */
    public record BulkSessionRevocation(
            int processed,
            int revoked,
            int alreadyTerminal,
            String nextCursor) {
    }
}
