package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedLaunchExchangeLookupPort;
import com.workflow.embed.application.port.EmbedSessionExchangeTransactionPort;
import com.workflow.embed.domain.EmbedApplicationSnapshot;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import com.workflow.embed.domain.EmbedFlowUser;
import com.workflow.embed.domain.EmbedGrantSnapshot;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedLaunchExchangeCandidate;
import com.workflow.embed.domain.EmbedSessionExchangePlan;
import com.workflow.embed.domain.EmbedViewSnapshot;
import com.workflow.embed.domain.PersistedEmbedLaunch;
import com.workflow.embed.domain.ProtectedContext;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedSessionExchangeMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedApplicationLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedBindingLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedFlowUserRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedGrantLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchExchangeRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedProviderLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedViewLockRow;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import com.workflow.core.database.JdbcLockedRow;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis implementation of the atomic Launch-to-Session exchange boundary. */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedSessionExchangeAdapter implements
        EmbedLaunchExchangeLookupPort,
        EmbedSessionExchangeTransactionPort {

    private final EmbedSessionExchangeMapper mapper;
    private final Clock clock;
    private final JdbcLockedRow lockedRows;

    /**
     * 初始化MyBatis嵌入式会话交换适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     * @param lockedRows 已锁定行依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedSessionExchangeAdapter(
            EmbedSessionExchangeMapper mapper,
            @Qualifier("embedClock") Clock clock,
            JdbcLockedRow lockedRows) {
        this.mapper = mapper;
        this.clock = clock;
        this.lockedRows = lockedRows;
    }

    /**
     * 按编码摘要查询嵌入式启动记录交换候选人；结果供后续展示或处理。
     *
     * @param launchCodeDigest 启动记录编码摘要，作为 {@code Optional.ofNullable} 的输入影响后续处理
     * @return 匹配的编码摘要；未找到时为空
     */
    @Override
    public Optional<EmbedLaunchExchangeCandidate> findByCodeDigest(String launchCodeDigest) {
        try {
            return Optional.ofNullable(mapper.findByCodeDigest(launchCodeDigest))
                    .map(this::mapCandidate);
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * Locks security roots in the documented global order, then Counter before Launch. The guarded
     * counter update, conditional launch consumption and session insert share one transaction.
     *
     * @param plan 执行方案，后续决定操作步骤和校验约束
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exchange(EmbedSessionExchangePlan plan) {
        try {
            PersistedEmbedLaunch candidate = plan.candidate().launch();
            EmbedApplicationLockRow application = mapper.lockApplication(candidate.applicationId());
            EmbedViewLockRow view = mapper.lockView(candidate.viewId());
            EmbedGrantLockRow grant = mapper.lockGrant(candidate.grantId());
            EmbedProviderLockRow provider = mapper.lockProvider(candidate.identityProviderId());
            EmbedBindingLockRow binding = mapper.lockBinding(candidate.identityBindingId());
            EmbedFlowUserRow user = mapper.lockFlowUser(candidate.flowUserId());
            Instant initialNow = clock.instant();
            validateSecuritySnapshot(
                    candidate, application, view, grant, provider, binding, user, initialNow);

            // 初始化不能覆盖已有会话计数；锁一直持有到本次兑换事务提交/回滚。
            lockedRows.ensureAndLock("embed_session_counter",
                    Map.of("grant_id", candidate.grantId(), "flow_user_id", candidate.flowUserId(),
                            "active_count", 0, "lock_version", 0L,
                            "create_time", local(initialNow), "update_time", local(initialNow)),
                    List.of("grant_id", "flow_user_id"));
            EmbedSessionCounterRow counter = mapper.lockCounter(
                    candidate.grantId(), candidate.flowUserId());
            if (counter == null || counter.activeCount() >= grant.maxActiveSessionsPerUser()) {
                throw new EmbedException(
                        429,
                        EmbedErrorCode.EMBED_SESSION_LIMIT_EXCEEDED,
                        "Active Embed session limit has been reached",
                        1L);
            }

            EmbedLaunchLockRow launch = mapper.lockLaunch(candidate.id());
            // A row-lock wait may outlive the 60-second Launch TTL, so time is sampled again only
            // after the Launch lock has been acquired.
            Instant transactionNow = clock.instant();
            validateLockedLaunch(candidate, launch, transactionNow);
            validateSecuritySnapshot(
                    candidate, application, view, grant, provider, binding, user, transactionNow);
            long plannedSessionSeconds = java.time.Duration.between(
                    plan.issuedAt(), plan.absoluteExpiresAt()).getSeconds();
            if (plannedSessionSeconds <= 0
                    || plannedSessionSeconds > grant.maxSessionSeconds()
                    || !plan.idleExpiresAt().isAfter(transactionNow)
                    || !plan.absoluteExpiresAt().isAfter(transactionNow)) {
                throw launchInvalid();
            }
            if (mapper.incrementCounter(
                    candidate.grantId(), candidate.flowUserId(),
                    grant.maxActiveSessionsPerUser(), local(transactionNow)) != 1) {
                throw new EmbedException(
                        429,
                        EmbedErrorCode.EMBED_SESSION_LIMIT_EXCEEDED,
                        "Active Embed session limit has been reached",
                        1L);
            }
            if (mapper.consumeLaunch(
                    candidate.id(), plan.sessionId(), candidate.launchCodeDigest(),
                    candidate.channelId(), candidate.parentOrigin(), local(transactionNow)) != 1) {
                throw launchInvalid();
            }
            if (mapper.insertSession(
                    plan,
                    local(transactionNow),
                    local(plan.idleExpiresAt()),
                    local(plan.absoluteExpiresAt())) != 1) {
                throw unavailable(null);
            }
        } catch (EmbedException expected) {
            throw expected;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 校验安全快照；不满足约束时阻止后续处理。
     *
     * @param launch 启动记录，供本方法校验安全快照时使用
     * @param application 应用，供本方法校验安全快照时使用
     * @param view 视图，作为 {@code equals} 的输入影响后续处理
     * @param grant 授权，作为 {@code expired} 的输入影响后续处理
     * @param provider 提供者，供本方法校验安全快照时使用
     * @param binding 绑定，作为 {@code expired} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param now 当前时间，供本方法校验安全快照时使用
     */
    private void validateSecuritySnapshot(
            PersistedEmbedLaunch launch,
            EmbedApplicationLockRow application,
            EmbedViewLockRow view,
            EmbedGrantLockRow grant,
            EmbedProviderLockRow provider,
            EmbedBindingLockRow binding,
            EmbedFlowUserRow user,
            Instant now) {
        if (application == null || view == null || grant == null || provider == null
                || binding == null || user == null
                || !"ACTIVE".equals(application.status())
                || expired(application.expiresAt(), now)
                || application.version() != launch.applicationVersion()
                || !"ACTIVE".equals(view.status())
                || view.securityVersion() != launch.viewSecurityVersion()
                || !"ACTIVE".equals(grant.status())
                || expired(grant.expiresAt(), now)
                || grant.securityVersion() != launch.grantSecurityVersion()
                || !"ACTIVE".equals(provider.status())
                || provider.securityVersion() != launch.providerSecurityVersion()
                || !"ACTIVE".equals(binding.status())
                || binding.bindingVersion() != launch.bindingVersion()
                || !launch.flowUserId().equals(binding.flowUserId())
                || binding.effectiveAt() == null
                || binding.effectiveAt().isAfter(local(now))
                || expired(binding.expiresAt(), now)
                || !"0".equals(user.status())
                || user.deleted() != 0
                || user.passwordResetRequired() != 0) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_SESSION_REVOKED,
                    "Embed security configuration has changed");
        }
    }

    /**
     * 校验已锁定启动记录；不满足约束时阻止后续处理。
     *
     * @param expected 预期，供本方法校验已锁定启动记录时使用
     * @param actual 实际，作为 {@code equals} 的输入影响后续处理
     * @param now 当前时间，供本方法校验已锁定启动记录时使用
     */
    private void validateLockedLaunch(
            PersistedEmbedLaunch expected,
            EmbedLaunchLockRow actual,
            Instant now) {
        if (actual == null
                || !"ISSUED".equals(actual.status())
                || expired(actual.expiresAt(), now)
                || !expected.applicationId().equals(actual.applicationId())
                || !expected.grantId().equals(actual.grantId())
                || !expected.viewId().equals(actual.viewId())
                || !expected.viewReleaseId().equals(actual.viewReleaseId())
                || !expected.identityProviderId().equals(actual.identityProviderId())
                || !expected.identityBindingId().equals(actual.identityBindingId())
                || !expected.flowUserId().equals(actual.flowUserId())
                || !expected.launchCodeDigest().equals(actual.launchCodeDigest())
                || !expected.channelId().equals(actual.channelId())
                || !expected.parentOrigin().equals(actual.parentOrigin())
                || expected.applicationVersion() != actual.applicationVersion()
                || expected.grantSecurityVersion() != actual.grantSecurityVersion()
                || expected.viewSecurityVersion() != actual.viewSecurityVersion()
                || expected.providerSecurityVersion() != actual.providerSecurityVersion()
                || expected.bindingVersion() != actual.bindingVersion()) {
            throw launchInvalid();
        }
    }

    /**
     * 处理映射候选人，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code PersistedEmbedLaunch} 的输入影响后续处理
     * @return 处理后的映射候选人结果，供调用方继续处理
     */
    private EmbedLaunchExchangeCandidate mapCandidate(EmbedLaunchExchangeRow row) {
        PersistedEmbedLaunch launch = new PersistedEmbedLaunch(
                row.id(), row.applicationId(), row.grantId(), row.viewId(), row.viewReleaseId(),
                row.identityProviderId(), row.providerSecurityVersion(), row.applicationVersion(),
                row.grantSecurityVersion(), row.viewSecurityVersion(), row.flowUserId(),
                row.identityBindingId(), row.bindingVersion(), row.subjectDigest(),
                row.subjectDigestKeyVersion(), row.parentOrigin(), row.channelId(), row.entryMode(),
                row.recordId(), new ProtectedContext(
                        row.contextCiphertext(), row.contextCipherKeyVersion(),
                        row.contextDigest(), row.contextDigestKeyVersion()),
                row.uiLocale(), row.uiTheme(), row.uiFormPresentation(),
                row.launchCodeDigest(), instant(row.expiresAt()),
                row.traceId(), row.requestId(), instant(row.createTime()));
        EmbedIdentityProviderSnapshot provider = new EmbedIdentityProviderSnapshot(
                row.identityProviderId(), row.providerType(), row.providerStatus(),
                row.providerIssuer(), row.subjectNamespace(), row.audiencesJson(),
                row.algorithmsJson(), row.jwksMode(), row.jwksJson(), row.jwksUrl(),
                row.clockSkewSeconds(), row.maxAssertionLifetimeSeconds(),
                row.providerKeyVersion(), row.currentProviderSecurityVersion());
        EmbedGrantSnapshot grant = new EmbedGrantSnapshot(
                row.grantId(), row.applicationId(), row.viewId(), row.grantStatus(),
                row.trustedSubjectAssertion(), row.grantCapabilitiesJson(),
                row.maxActiveSessionsPerUser(), row.maxSessionSeconds(),
                row.launchLimitPerMinute(), row.runtimeLimitPerMinute(),
                row.maxConcurrency(),
                instant(row.grantExpiresAt()), row.currentGrantSecurityVersion(), provider, Set.of());
        EmbedExternalIdentityBinding binding = new EmbedExternalIdentityBinding(
                row.identityBindingId(), row.bindingApplicationId(), row.bindingProviderId(),
                row.bindingSubjectDigest(), row.bindingSubjectDigestKeyVersion(),
                row.bindingFlowUserId(), row.bindingStatus(), row.currentBindingVersion(),
                instant(row.bindingEffectiveAt()), instant(row.bindingExpiresAt()));
        return new EmbedLaunchExchangeCandidate(
                launch, row.launchStatus(), instant(row.consumedAt()), instant(row.revokedAt()),
                row.maxActiveSessionsPerUser(), row.maxSessionSeconds(),
                row.releaseCapabilitiesJson(), row.grantCapabilitiesJson(),
                new EmbedApplicationSnapshot(
                        row.applicationId(), row.applicationStatus(),
                        instant(row.applicationExpiresAt()), row.currentApplicationVersion()),
                new EmbedViewSnapshot(
                        row.viewId(), row.viewKey(), row.viewSurfaceType(), row.viewStatus(),
                        row.currentViewSecurityVersion()),
                grant,
                binding,
                new EmbedFlowUser(
                        row.flowUserId(), row.flowUsername(), "0".equals(row.flowUserStatus()),
                        row.flowUserDeleted() != 0, row.flowUserPasswordResetRequired() != 0));
    }

    /**
     * 判断过期条件是否成立，供调用方选择后续分支。
     *
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，作为 {@code expiresAt.isAfter} 的输入影响后续处理
     * @return 过期条件成立时为 true，否则为 false
     */
    private static boolean expired(LocalDateTime expiresAt, Instant now) {
        return expiresAt != null && !expiresAt.isAfter(local(now));
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

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     */
    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 构造启动记录无效异常，供调用方区分失败原因。
     *
     * @return 处理后的启动记录无效结果，供调用方继续处理
     */
    private static EmbedException launchInvalid() {
        return new EmbedException(401, EmbedErrorCode.EMBED_LAUNCH_INVALID,
                "Embed launch is invalid");
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param error 错误，供本方法处理不可用时使用
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed exchange persistence is unavailable",
                null,
                error);
    }
}
