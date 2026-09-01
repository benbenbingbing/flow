package com.workflow.embed.infrastructure.persistence.adapter;

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

    public MyBatisEmbedLaunchPersistenceAdapter(EmbedLaunchPersistenceMapper mapper) {
        this.mapper = mapper;
    }

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

    @Override
    public boolean claim(
            String providerId,
            String jtiDigest,
            Instant expiresAt,
            Instant now) {
        try {
            return mapper.insertAssertionReplay(
                    providerId, jtiDigest, local(expiresAt), local(now)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

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
                    launch.uiLocale(), launch.uiTheme(), launch.launchCodeDigest(),
                    local(launch.expiresAt()), launch.traceId(), launch.requestId(),
                    local(launch.createTime()));
            if (affected != 1) {
                throw unavailable(null);
            }
        } catch (DataAccessException error) {
            throw unavailable(error);
        }
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** Maps both the preflight and locked reload through one fail-closed projection. */
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

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed launch persistence is unavailable",
                null,
                error);
    }
}
