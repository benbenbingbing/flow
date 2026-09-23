package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.embed.application.port.EmbedAssertionReplayPort;
import com.workflow.embed.application.port.EmbedExternalIdentityBindingPort;
import com.workflow.embed.application.port.EmbedFlowUserPort;
import com.workflow.embed.application.port.EmbedLaunchConfigurationPort;
import com.workflow.embed.application.port.EmbedLaunchStorePort;
import com.workflow.embed.domain.EmbedApplicationSnapshot;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import com.workflow.embed.domain.EmbedFlowUser;
import com.workflow.embed.domain.EmbedGrantSnapshot;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedLaunchConfiguration;
import com.workflow.embed.domain.EmbedViewSnapshot;
import com.workflow.embed.domain.PersistedEmbedLaunch;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedLaunchPersistenceMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedExternalIdentityBindingRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedFlowUserRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchConfigurationRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for launch configuration, identity lookup, replay claims and launch writes. */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedLaunchPersistenceAdapter implements
        EmbedLaunchConfigurationPort,
        EmbedExternalIdentityBindingPort,
        EmbedFlowUserPort,
        EmbedAssertionReplayPort,
        EmbedLaunchStorePort {

    private final EmbedLaunchPersistenceMapper mapper;
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 初始化MyBatis嵌入式启动记录持久化适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param writeAttempt 写入{@code attempt}依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedLaunchPersistenceAdapter(EmbedLaunchPersistenceMapper mapper, JdbcWriteAttempt writeAttempt) {
        this.mapper = mapper;
        this.writeAttempt = writeAttempt;
    }

    /**
     * 查询嵌入式启动记录配置；结果供调用方展示或继续处理。
     *
     * @param applicationId 应用ID，后续用于查询MyBatis嵌入式启动记录持久化时定位或关联目标
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法查询MyBatis嵌入式启动记录持久化时使用
     * @return 匹配的MyBatis嵌入式启动记录持久化；未找到时为空
     */
    @Override
    public Optional<EmbedLaunchConfiguration> find(
            String applicationId,
            String viewKey,
            Instant now) {
        try {
            return configuration(mapper.findConfiguration(applicationId, viewKey));
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 锁定更新；避免后续并发处理覆盖状态。
     *
     * @param applicationId 应用ID，后续用于锁定更新时定位或关联目标
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法锁定更新时使用
     * @return 匹配的更新；未找到时为空
     */
    @Override
    public Optional<EmbedLaunchConfiguration> lockForUpdate(
            String applicationId,
            String viewKey,
            Instant now) {
        try {
            return configuration(mapper.lockConfiguration(applicationId, viewKey));
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 查询嵌入式外部身份绑定；结果供调用方展示或继续处理。
     *
     * @param applicationId 应用ID，后续用于查询MyBatis嵌入式启动记录持久化时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于查询MyBatis嵌入式启动记录持久化时定位或关联目标
     * @param subjectDigest 主体摘要，作为 {@code mapper.findBinding} 的输入影响后续处理
     * @param subjectDigestKeyVersion 主体摘要键版本，作为 {@code mapper.findBinding} 的输入影响后续处理
     * @param now 当前时间，供本方法查询MyBatis嵌入式启动记录持久化时使用
     * @return 匹配的MyBatis嵌入式启动记录持久化；未找到时为空
     */
    @Override
    public Optional<EmbedExternalIdentityBinding> find(
            String applicationId,
            String identityProviderId,
            String subjectDigest,
            String subjectDigestKeyVersion,
            Instant now) {
        try {
            EmbedExternalIdentityBindingRow row = mapper.findBinding(
                    applicationId, identityProviderId, subjectDigest, subjectDigestKeyVersion);
            return Optional.ofNullable(row).map(value -> new EmbedExternalIdentityBinding(
                    value.id(), value.applicationId(), value.identityProviderId(),
                    value.subjectDigest(), value.subjectDigestKeyVersion(), value.flowUserId(),
                    value.status(), value.bindingVersion(), instant(value.effectiveAt()),
                    instant(value.expiresAt())));
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 按ID查询嵌入式流程用户；结果供后续展示或处理。
     *
     * @param flowUserId 流程用户ID，后续用于查询ID时定位或关联目标
     * @return 匹配的ID；未找到时为空
     */
    @Override
    public Optional<EmbedFlowUser> findById(String flowUserId) {
        try {
            EmbedFlowUserRow row = mapper.findFlowUser(flowUserId);
            return Optional.ofNullable(row).map(value -> new EmbedFlowUser(
                    value.id(), value.username(), "0".equals(value.status()),
                    value.deleted() != 0, value.passwordResetRequired() != 0));
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 认领MyBatis嵌入式启动记录持久化；后续读取或执行将使用更新后的状态。
     *
     * @param providerId 提供者ID，后续用于认领MyBatis嵌入式启动记录持久化时定位或关联目标
     * @param jtiDigest {@code jti}摘要，供本方法认领MyBatis嵌入式启动记录持久化时使用
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，供本方法认领MyBatis嵌入式启动记录持久化时使用
     * @return MyBatis嵌入式启动记录持久化条件成立时为 true，否则为 false
     */
    @Override
    public boolean claim(
            String providerId,
            String jtiDigest,
            Instant expiresAt,
            Instant now) {
        try {
            return writeAttempt.execute(() -> mapper.insertAssertionReplay(
                    providerId, jtiDigest, local(expiresAt), local(now))) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    /**
     * 插入MyBatis嵌入式启动记录持久化；后续读取或执行将使用更新后的状态。
     *
     * @param launch 启动记录，作为 {@code mapper.insertLaunch} 的输入影响后续处理
     */
    @Override
    public void insert(PersistedEmbedLaunch launch) {
        try {
            int affected = mapper.insertLaunch(
                    launch.id(), launch.applicationId(), launch.grantId(), launch.viewId(),
                    launch.viewReleaseId(), launch.identityProviderId(),
                    launch.providerSecurityVersion(), launch.applicationVersion(),
                    launch.grantSecurityVersion(), launch.viewSecurityVersion(),
                    launch.flowUserId(), launch.identityBindingId(), launch.bindingVersion(),
                    launch.subjectDigest(), launch.subjectDigestKeyVersion(),
                    launch.parentOrigin(), launch.channelId(), launch.entryMode(), launch.recordId(),
                    launch.context().ciphertext(), launch.context().cipherKeyVersion(),
                    launch.context().digest(), launch.context().digestKeyVersion(),
                    launch.uiLocale(), launch.uiTheme(), launch.uiFormPresentation(),
                    launch.launchCodeDigest(),
                    local(launch.expiresAt()), launch.traceId(), launch.requestId(),
                    local(launch.createTime()));
            if (affected != 1) {
                throw unavailable(null);
            }
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
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
     * Maps both the preflight and locked reload through one fail-closed projection.
     *
     * @param row 行，作为 {@code mapper.findAllowedOrigins} 的输入影响后续处理
     * @return 匹配的配置；未找到时为空
     */
    private Optional<EmbedLaunchConfiguration> configuration(
            EmbedLaunchConfigurationRow row) {
        if (row == null) {
            return Optional.empty();
        }
        Set<String> origins = mapper.findAllowedOrigins(row.grantId());
        EmbedIdentityProviderSnapshot provider = new EmbedIdentityProviderSnapshot(
                row.providerId(), row.providerType(), row.providerStatus(), row.providerIssuer(),
                row.subjectNamespace(), row.audiencesJson(), row.algorithmsJson(), row.jwksMode(),
                row.jwksJson(), row.jwksUrl(), row.clockSkewSeconds(),
                row.maxAssertionLifetimeSeconds(), row.providerKeyVersion(),
                row.providerSecurityVersion());
        return Optional.of(new EmbedLaunchConfiguration(
                new EmbedApplicationSnapshot(
                        row.applicationId(), row.applicationStatus(),
                        instant(row.applicationExpiresAt()), row.applicationVersion()),
                new EmbedViewSnapshot(
                        row.viewId(), row.viewKey(), row.viewSurfaceType(), row.viewStatus(),
                        row.viewSecurityVersion()),
                row.currentConfigJson(),
                new EmbedGrantSnapshot(
                        row.grantId(), row.applicationId(), row.viewId(), row.grantStatus(),
                        row.trustedSubjectAssertion(), row.capabilityCeilingJson(),
                        row.maxActiveSessionsPerUser(), row.maxSessionSeconds(),
                        row.launchLimitPerMinute(), row.runtimeLimitPerMinute(),
                        row.maxConcurrency(),
                        instant(row.grantExpiresAt()), row.grantSecurityVersion(),
                        provider, origins == null ? Set.of() : Set.copyOf(origins))));
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
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param error 错误，供本方法处理不可用时使用
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed launch persistence is unavailable",
                null,
                error);
    }
}
