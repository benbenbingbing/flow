package com.workflow.embed.management.application.port;

import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Launch/Session 管理查询与 Launch 状态流转的持久化端口。 */
public interface EmbedOperationsRepository {

    /**
     * 查询启动记录；查询结果供调用方展示或继续处理。
     *
     * @param applicationId 应用ID，后续用于查询启动记录时定位或关联目标
     * @param viewId 视图ID，后续用于查询启动记录时定位或关联目标
     * @param status 状态标识，决定后续启动记录采用的处理分支
     * @param createdFrom 已创建起始，供本方法查询启动记录时使用
     * @param createdTo 已创建截止，供本方法查询启动记录时使用
     * @param cursorTime 游标时间，后续用于判断有效期或展示该事件的发生时间
     * @param cursorId 游标ID，后续用于查询启动记录时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询启动记录时使用
     * @return 启动记录摘要集合，供调用方遍历或展示
     */
    List<LaunchSummary> findLaunches(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit);

    /**
     * 查询会话；查询结果供调用方展示或继续处理。
     *
     * @param applicationId 应用ID，后续用于查询会话时定位或关联目标
     * @param viewId 视图ID，后续用于查询会话时定位或关联目标
     * @param status 状态标识，决定后续会话采用的处理分支
     * @param createdFrom 已创建起始，供本方法查询会话时使用
     * @param createdTo 已创建截止，供本方法查询会话时使用
     * @param cursorTime 游标时间，后续用于判断有效期或展示该事件的发生时间
     * @param cursorId 游标ID，后续用于查询会话时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询会话时使用
     * @return 会话摘要集合，供调用方遍历或展示
     */
    List<SessionSummary> findSessions(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit);

    /**
     * 查询启动记录；查询结果供调用方展示或继续处理。
     *
     * @param launchId 启动记录ID，后续用于查询启动记录时定位或关联目标
     * @return 匹配的启动记录；未找到时为空
     */
    Optional<LaunchSummary> findLaunch(String launchId);

    /**
     * 撤销已签发启动记录；后续读取或执行将使用更新后的状态。
     *
     * @param launchId 启动记录ID，后续用于撤销已签发启动记录时定位或关联目标
     * @param now 当前时间，供本方法撤销已签发启动记录时使用
     * @return 撤销后的已签发启动记录结果，供调用方继续处理
     */
    LaunchRevokeOutcome revokeIssuedLaunch(String launchId, Instant now);

    /**
     * 查询会话；查询结果供调用方展示或继续处理。
     *
     * @param sessionId 会话ID，后续用于查询会话时定位或关联目标
     * @return 匹配的会话；未找到时为空
     */
    Optional<SessionSummary> findSession(String sessionId);

    /**
     * 查询活动会话ID 集合；查询结果供调用方展示或继续处理。
     *
     * @param scope 作用域，供本方法查询活动会话ID 集合时使用
     * @param scopeId 作用域ID，后续用于查询活动会话ID 集合时定位或关联目标
     * @param afterSessionId 之后会话ID，后续用于查询活动会话ID 集合时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询活动会话ID 集合时使用
     * @return 嵌入式操作集合，供调用方遍历或展示
     */
    List<String> findActiveSessionIds(
            Scope scope, String scopeId, String afterSessionId, int fetchLimit);

    /**
     * 定义作用域的可选值；调用方据此选择对应的处理分支。
     */
    enum Scope {
        VIEW,
        APPLICATION
    }

    /**
     * 定义启动记录撤销结果的可选值；调用方据此选择对应的处理分支。
     */
    enum LaunchRevokeOutcome {
        REVOKED,
        ALREADY_REVOKED,
        EXPIRED,
        CONSUMED,
        NOT_FOUND
    }
}
