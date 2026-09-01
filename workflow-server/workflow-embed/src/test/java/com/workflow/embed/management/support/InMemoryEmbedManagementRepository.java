package com.workflow.embed.management.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.port.EmbedManagementRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试用内存持久化端口，刻意不启动 Spring 或数据库。 */
public class InMemoryEmbedManagementRepository implements EmbedManagementRepository {

    public final Map<String, ViewState> views = new LinkedHashMap<>();
    public final Map<String, List<ReleaseState>> releases = new LinkedHashMap<>();
    public final Map<String, GrantState> grants = new LinkedHashMap<>();
    public final Map<String, ProviderState> providers = new LinkedHashMap<>();
    public final Map<String, BindingState> bindings = new LinkedHashMap<>();
    public ResolvedResource resolvedResource;
    public boolean applicationEnabled = true;
    public boolean flowUserEnabled = true;
    public long activeSessions;

    @Override
    public Page<ViewState> findViews(ViewFilter filter) {
        List<ViewState> values = views.values().stream()
                .filter(view -> filter.status() == null || view.status() == filter.status())
                .filter(view -> filter.surfaceType() == null
                        || view.surfaceType() == filter.surfaceType())
                .toList();
        return new Page<>(values, values.size(), filter.pageNum(), filter.pageSize());
    }

    @Override
    public ViewState findView(String viewId) {
        return views.get(viewId);
    }

    @Override
    public ViewState lockView(String viewId) {
        return findView(viewId);
    }

    @Override
    public ViewState findViewByKey(String viewKey) {
        return views.values().stream()
                .filter(view -> view.viewKey().equals(viewKey)).findFirst().orElse(null);
    }

    @Override
    public void insertView(ViewState view) {
        views.put(view.id(), view);
    }

    @Override
    public int updateDraft(String viewId, long expectedVersion, String draftJson,
                           String actorId, LocalDateTime now) {
        ViewState current = views.get(viewId);
        if (current == null || current.lockVersion() != expectedVersion
                || current.status() == ViewStatus.RETIRED) {
            return 0;
        }
        views.put(viewId, new ViewState(
                current.id(), current.viewKey(), current.name(), current.description(),
                current.surfaceType(),
                current.status() == ViewStatus.DRAFT
                        ? ViewStatus.ACTIVE : current.status(),
                draftJson,
                current.draftRevision(), current.publishedReleaseId(),
                current.publishedRevision(), current.lockVersion() + 1,
                current.securityVersion(), current.createBy(), current.createTime(),
                actorId, now));
        return 1;
    }

    @Override
    public long nextReleaseRevision(String viewId) {
        return releases.getOrDefault(viewId, List.of()).stream()
                .map(ReleaseState::revision).max(Long::compareTo).orElse(0L) + 1;
    }

    @Override
    public void insertRelease(ReleaseState release) {
        releases.computeIfAbsent(release.viewId(), ignored -> new ArrayList<>()).add(release);
    }

    @Override
    public int updateViewStatus(String viewId, long expectedVersion, String status,
                                String actorId, LocalDateTime now) {
        ViewState current = views.get(viewId);
        if (current == null || current.lockVersion() != expectedVersion) {
            return 0;
        }
        views.put(viewId, new ViewState(
                current.id(), current.viewKey(), current.name(), current.description(),
                current.surfaceType(), ViewStatus.valueOf(status), current.draftConfigJson(),
                current.draftRevision(), current.publishedReleaseId(),
                current.publishedRevision(), current.lockVersion() + 1,
                current.securityVersion() + 1, current.createBy(), current.createTime(),
                actorId, now));
        return 1;
    }

    @Override
    public ReleaseState findReleaseByConfigHash(
            String viewId, String configHash) {
        return releases.getOrDefault(viewId, List.of()).stream()
                .filter(release -> release.configHash().equals(configHash))
                .max(java.util.Comparator.comparingLong(ReleaseState::revision))
                .orElse(null);
    }

    @Override
    public ResolvedResource resolvePublishedResource(
            SurfaceType surfaceType, JsonNode target, JsonNode releasePolicy) {
        return resolvedResource;
    }

    @Override
    public GrantState findGrant(String viewId, String applicationId) {
        return grants.get(grantKey(viewId, applicationId));
    }

    @Override
    public GrantState lockGrant(String viewId, String applicationId) {
        return findGrant(viewId, applicationId);
    }

    @Override
    public List<GrantState> findGrants(String viewId) {
        return grants.values().stream().filter(grant -> grant.viewId().equals(viewId)).toList();
    }

    @Override
    public void insertGrant(GrantState grant) {
        grants.put(grantKey(grant.viewId(), grant.applicationId()), grant);
    }

    @Override
    public int updateGrant(GrantState grant, long expectedVersion,
                           String actorId, LocalDateTime now) {
        GrantState current = findGrant(grant.viewId(), grant.applicationId());
        if (current == null || current.lockVersion() != expectedVersion) {
            return 0;
        }
        grants.put(grantKey(grant.viewId(), grant.applicationId()), grant);
        return 1;
    }

    @Override
    public void replaceOrigins(String grantId, List<String> origins) {
        grants.replaceAll((key, current) -> !current.id().equals(grantId) ? current
                : new GrantState(current.id(), current.applicationId(), current.viewId(),
                        current.identityProviderId(), current.status(),
                        current.trustedSubjectAssertion(), current.revisionMode(),
                        current.pinnedRevision(), current.capabilityCeilingJson(),
                        current.maxActiveSessionsPerUser(), current.maxSessionSeconds(),
                        current.launchLimitPerMinute(), current.runtimeLimitPerMinute(),
                        current.maxConcurrency(), current.expiresAt(), current.lockVersion(),
                        current.securityVersion(), List.copyOf(origins), current.createBy(),
                        current.createTime(), current.updateBy(), current.updateTime(),
                        current.revokedBy(), current.revokedAt()));
    }

    @Override
    public int changeGrantStatus(String grantId, long expectedVersion, String status,
                                 String actorId, LocalDateTime now, boolean revoked) {
        for (Map.Entry<String, GrantState> entry : grants.entrySet()) {
            GrantState current = entry.getValue();
            if (current.id().equals(grantId) && current.lockVersion() == expectedVersion) {
                entry.setValue(new GrantState(
                        current.id(), current.applicationId(), current.viewId(),
                        current.identityProviderId(), SecurityStatus.valueOf(status),
                        current.trustedSubjectAssertion(), current.revisionMode(),
                        current.pinnedRevision(), current.capabilityCeilingJson(),
                        current.maxActiveSessionsPerUser(), current.maxSessionSeconds(),
                        current.launchLimitPerMinute(), current.runtimeLimitPerMinute(),
                        current.maxConcurrency(), current.expiresAt(),
                        current.lockVersion() + 1, current.securityVersion() + 1,
                        current.allowedOrigins(), current.createBy(), current.createTime(),
                        actorId, now, revoked ? actorId : null, revoked ? now : null));
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean applicationExistsAndEnabled(String applicationId) {
        return applicationEnabled;
    }

    @Override
    public Page<ProviderState> findProviders(ProviderFilter filter) {
        List<ProviderState> values = providers.values().stream().toList();
        return new Page<>(values, values.size(), filter.pageNum(), filter.pageSize());
    }

    @Override
    public ProviderState findProvider(String providerId) {
        return providers.get(providerId);
    }

    @Override
    public ProviderState lockProvider(String providerId) {
        return findProvider(providerId);
    }

    @Override
    public ProviderState findProviderByIssuerAndNamespace(String issuer, String namespace) {
        return providers.values().stream()
                .filter(provider -> java.util.Objects.equals(provider.issuer(), issuer)
                        && provider.subjectNamespace().equals(namespace))
                .findFirst().orElse(null);
    }

    @Override
    public ProviderState lockProviderByIssuerAndNamespace(String issuer, String namespace) {
        return findProviderByIssuerAndNamespace(issuer, namespace);
    }

    @Override
    public void insertProvider(ProviderState provider) {
        providers.put(provider.id(), provider);
    }

    @Override
    public int updateProvider(ProviderState provider, long expectedVersion,
                              String actorId, LocalDateTime now) {
        ProviderState current = providers.get(provider.id());
        if (current == null || current.lockVersion() != expectedVersion) {
            return 0;
        }
        providers.put(provider.id(), provider);
        return 1;
    }

    @Override
    public int changeProviderStatus(String providerId, long expectedVersion, String status,
                                    String actorId, LocalDateTime now, boolean revoked) {
        ProviderState current = providers.get(providerId);
        if (current == null || current.lockVersion() != expectedVersion) {
            return 0;
        }
        providers.put(providerId, new ProviderState(
                current.id(), current.name(), current.type(), SecurityStatus.valueOf(status),
                current.issuer(), current.subjectNamespace(), current.audiencesJson(),
                current.algorithmsJson(), current.jwksMode(), current.jwksJson(), current.jwksUrl(),
                current.clockSkewSeconds(), current.maxAssertionLifetimeSeconds(),
                current.keyVersion(), current.lockVersion() + 1, current.securityVersion() + 1,
                current.createBy(), current.createTime(), actorId, now,
                revoked ? actorId : null, revoked ? now : null));
        return 1;
    }

    @Override
    public int rotateProviderKey(String providerId, long expectedVersion, String jwksJson,
                                 String actorId, LocalDateTime now) {
        ProviderState current = providers.get(providerId);
        if (current == null || current.lockVersion() != expectedVersion) {
            return 0;
        }
        providers.put(providerId, new ProviderState(
                current.id(), current.name(), current.type(), current.status(), current.issuer(),
                current.subjectNamespace(), current.audiencesJson(), current.algorithmsJson(),
                current.jwksMode(), jwksJson, current.jwksUrl(), current.clockSkewSeconds(),
                current.maxAssertionLifetimeSeconds(), current.keyVersion() + 1,
                current.lockVersion() + 1, current.securityVersion() + 1,
                current.createBy(), current.createTime(), actorId, now,
                current.revokedBy(), current.revokedAt()));
        return 1;
    }

    @Override
    public Page<BindingState> findBindings(BindingFilter filter) {
        List<BindingState> values = bindings.values().stream().toList();
        return new Page<>(values, values.size(), filter.pageNum(), filter.pageSize());
    }

    @Override
    public BindingState findBinding(String bindingId) {
        return bindings.get(bindingId);
    }

    @Override
    public BindingState lockBinding(String bindingId) {
        return findBinding(bindingId);
    }

    @Override
    public BindingState findBindingByDigests(
            String applicationId, String providerId, List<String> digests) {
        return bindings.values().stream()
                .filter(binding -> binding.applicationId().equals(applicationId)
                        && binding.identityProviderId().equals(providerId)
                        && digests.contains(binding.subjectDigest()))
                .findFirst().orElse(null);
    }

    @Override
    public void insertBinding(BindingState binding) {
        bindings.put(binding.id(), binding);
    }

    @Override
    public int changeBindingStatus(String bindingId, long expectedVersion, String status,
                                   String actorId, LocalDateTime now, boolean revoked) {
        BindingState current = bindings.get(bindingId);
        if (current == null || current.bindingVersion() != expectedVersion) {
            return 0;
        }
        bindings.put(bindingId, new BindingState(
                current.id(), current.applicationId(), current.identityProviderId(),
                current.subjectDigest(), current.subjectDigestKeyVersion(), current.subjectHint(),
                current.flowUserId(), SecurityStatus.valueOf(status),
                current.bindingVersion() + 1, current.effectiveAt(), current.expiresAt(),
                current.createBy(), current.createTime(), actorId, now,
                revoked ? actorId : null, revoked ? now : null));
        return 1;
    }

    @Override
    public boolean flowUserExistsAndEnabled(String flowUserId) {
        return flowUserEnabled;
    }

    @Override
    public long countActiveSessionsByView(String viewId) {
        return activeSessions;
    }

    private static String grantKey(String viewId, String applicationId) {
        return viewId + "\n" + applicationId;
    }

}
