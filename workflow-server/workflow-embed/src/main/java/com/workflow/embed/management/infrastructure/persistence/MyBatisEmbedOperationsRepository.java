package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionSummary;
import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.LaunchRow;
import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.SessionRow;
import com.workflow.embed.management.application.port.EmbedOperationsRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis Embed 运维管理适配器。 */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedOperationsRepository implements EmbedOperationsRepository {

    private final EmbedOperationsMapper mapper;

    /**
     * 初始化MyBatis嵌入式操作集合仓储，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedOperationsRepository(EmbedOperationsMapper mapper) {
        this.mapper = mapper;
    }

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
    @Override
    public List<LaunchSummary> findLaunches(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit) {
        return mapper.findLaunches(
                        applicationId, viewId, status, local(createdFrom), local(createdTo),
                        local(cursorTime), cursorId, fetchLimit)
                .stream().map(MyBatisEmbedOperationsRepository::launch).toList();
    }

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
    @Override
    public List<SessionSummary> findSessions(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit) {
        return mapper.findSessions(
                        applicationId, viewId, status, local(createdFrom), local(createdTo),
                        local(cursorTime), cursorId, fetchLimit)
                .stream().map(MyBatisEmbedOperationsRepository::session).toList();
    }

    /**
     * 查询启动记录；查询结果供调用方展示或继续处理。
     *
     * @param launchId 启动记录ID，后续用于查询启动记录时定位或关联目标
     * @return 匹配的启动记录；未找到时为空
     */
    @Override
    public Optional<LaunchSummary> findLaunch(String launchId) {
        return Optional.ofNullable(mapper.findLaunch(launchId)).map(MyBatisEmbedOperationsRepository::launch);
    }

    /**
     * 锁定单个 Launch 并只允许 ISSUED 进入 REVOKED；逻辑过期的行先收敛为 EXPIRED。
     *
     * @param launchId 启动记录ID，后续用于撤销已签发启动记录时定位或关联目标
     * @param now 当前时间，作为 {@code mapper.expireIssuedLaunch} 的输入影响后续处理
     * @return 撤销后的已签发启动记录结果，供调用方继续处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LaunchRevokeOutcome revokeIssuedLaunch(String launchId, Instant now) {
        LaunchRow row = mapper.lockLaunch(launchId);
        if (row == null) {
            return LaunchRevokeOutcome.NOT_FOUND;
        }
        if ("REVOKED".equals(row.status())) {
            return LaunchRevokeOutcome.ALREADY_REVOKED;
        }
        if ("CONSUMED".equals(row.status())) {
            return LaunchRevokeOutcome.CONSUMED;
        }
        if ("EXPIRED".equals(row.status())) {
            return LaunchRevokeOutcome.EXPIRED;
        }
        if (!row.expiresAt().isAfter(local(now))) {
            mapper.expireIssuedLaunch(launchId, local(now));
            return LaunchRevokeOutcome.EXPIRED;
        }
        return mapper.revokeIssuedLaunch(launchId, local(now)) == 1
                ? LaunchRevokeOutcome.REVOKED
                : LaunchRevokeOutcome.NOT_FOUND;
    }

    /**
     * 查询会话；查询结果供调用方展示或继续处理。
     *
     * @param sessionId 会话ID，后续用于查询会话时定位或关联目标
     * @return 匹配的会话；未找到时为空
     */
    @Override
    public Optional<SessionSummary> findSession(String sessionId) {
        return Optional.ofNullable(mapper.findSession(sessionId))
                .map(MyBatisEmbedOperationsRepository::session);
    }

    /**
     * 查询活动会话ID 集合；查询结果供调用方展示或继续处理。
     *
     * @param scope 作用域，作为 {@code List.copyOf} 的输入影响后续处理
     * @param scopeId 作用域ID，后续用于查询活动会话ID 集合时定位或关联目标
     * @param afterSessionId 之后会话ID，后续用于查询活动会话ID 集合时定位或关联目标
     * @param fetchLimit {@code fetch}上限，作为 {@code List.copyOf} 的输入影响后续处理
     * @return MyBatis嵌入式操作集合，供调用方遍历或展示
     */
    @Override
    public List<String> findActiveSessionIds(
            Scope scope, String scopeId, String afterSessionId, int fetchLimit) {
        return List.copyOf(scope == Scope.VIEW
                ? mapper.findActiveSessionIdsByView(scopeId, afterSessionId, fetchLimit)
                : mapper.findActiveSessionIdsByApplication(scopeId, afterSessionId, fetchLimit));
    }

    /**
     * 处理启动记录，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code LaunchSummary} 的输入影响后续处理
     * @return 处理后的启动记录结果，供调用方继续处理
     */
    private static LaunchSummary launch(LaunchRow row) {
        return new LaunchSummary(
                row.id(), row.applicationId(), row.grantId(), row.viewId(), row.viewReleaseId(),
                row.status(), row.entryMode(), instant(row.expiresAt()), instant(row.consumedAt()),
                instant(row.revokedAt()), instant(row.createTime()));
    }

    /**
     * 处理会话，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code SessionSummary} 的输入影响后续处理
     * @return 处理后的会话结果，供调用方继续处理
     */
    private static SessionSummary session(SessionRow row) {
        return new SessionSummary(
                row.id(), row.launchId(), row.applicationId(), row.grantId(), row.viewId(),
                row.viewReleaseId(), row.status(), row.entryMode(), instant(row.issuedAt()),
                instant(row.lastSeenAt()), instant(row.idleExpiresAt()),
                instant(row.absoluteExpiresAt()), instant(row.revokedAt()), row.revokeReason());
    }

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     */
    private static LocalDateTime local(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 处理绝对时间，并将结果传给后续步骤。
     *
     * @param value 待处理绝对时间的原始输入，结果供调用方继续使用
     * @return 处理后的绝对时间结果，供调用方继续处理
     */
    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
