package com.workflow.embed.management.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.EntityNewDataFormRuntimePort;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.RevisionMode;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.BindingRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FormTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.GrantRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ProviderRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ReleaseRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ViewRow;
import com.workflow.embed.management.port.EmbedManagementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis 驱动的 Embed 管理持久化适配器。 */
@Repository
public class MyBatisEmbedManagementRepository implements EmbedManagementRepository {

    private final EmbedManagementMapper mapper;
    private final PublishedUiResourceSnapshotParser snapshotParser;
    private final EntityNewDataFormRuntimePort newDataFormRuntimePort;

    public MyBatisEmbedManagementRepository(
            EmbedManagementMapper mapper,
            ObjectMapper objectMapper,
            EntityNewDataFormRuntimePort newDataFormRuntimePort) {
        this.mapper = mapper;
        this.snapshotParser = new PublishedUiResourceSnapshotParser(objectMapper);
        this.newDataFormRuntimePort = newDataFormRuntimePort;
    }

    @Override
    public Page<ViewState> findViews(ViewFilter filter) {
        List<ViewState> records = mapper.findViews(
                filter.keyword(), name(filter.status()), name(filter.surfaceType()),
                filter.applicationId(), filter.pageSize(), offset(filter.pageNum(), filter.pageSize()))
                .stream().map(this::view).toList();
        long total = mapper.countViews(filter.keyword(), name(filter.status()),
                name(filter.surfaceType()), filter.applicationId());
        return new Page<>(records, total, filter.pageNum(), filter.pageSize());
    }

    @Override
    public ViewState findView(String viewId) {
        return view(mapper.findView(viewId));
    }

    @Override
    public ViewState lockView(String viewId) {
        return view(mapper.lockView(viewId));
    }

    @Override
    public ViewState findViewByKey(String viewKey) {
        return view(mapper.findViewByKey(viewKey));
    }

    @Override
    public void insertView(ViewState view) {
        mapper.insertView(row(view));
    }

    @Override
    public int updateDraft(String viewId, long expectedVersion, String draftJson,
                           String actorId, LocalDateTime now) {
        return mapper.updateDraft(viewId, expectedVersion, draftJson, actorId, now);
    }

    @Override
    public long nextReleaseRevision(String viewId) {
        return mapper.nextReleaseRevision(viewId);
    }

    @Override
    public void insertRelease(ReleaseState release) {
        mapper.insertRelease(row(release));
    }

    @Override
    public int updateViewStatus(String viewId, long expectedVersion, String status,
                                String actorId, LocalDateTime now) {
        return mapper.updateViewStatus(viewId, expectedVersion, status, actorId, now);
    }

    @Override
    public ReleaseState findReleaseByConfigHash(
            String viewId, String configHash) {
        return release(mapper.findReleaseByConfigHash(viewId, configHash));
    }

    @Override
    public ResolvedResource resolvePublishedResource(
            SurfaceType surfaceType, JsonNode target, JsonNode releasePolicy) {
        String strategy = text(releasePolicy, "strategy");
        if (!Set.of("PINNED", "FOLLOW_ACTIVE").contains(strategy)) {
            return null;
        }
        String entityCode = text(target, "entityCode");
        String listReleaseId = "PINNED".equals(strategy)
                ? text(releasePolicy, "listReleaseId") : null;
        String formReleaseId = "PINNED".equals(strategy)
                ? text(releasePolicy, "formReleaseId") : null;
        ListTargetRow list = null;
        String listKey = null;
        if (surfaceType == SurfaceType.LIST) {
            listKey = text(target, "listKey");
            if ("PINNED".equals(strategy) && !StringUtils.hasText(listReleaseId)) {
                return null;
            }
            list = mapper.findListTarget(entityCode, listKey, listReleaseId);
            if (list == null) {
                return null;
            }
        }
        String formId = firstText(target, "formId", "defaultFormId");
        FormTargetRow form = null;
        if (surfaceType == SurfaceType.LIST
                && !StringUtils.hasText(formId)
                && "FOLLOW_ACTIVE".equals(strategy)) {
            // 管理员只选实体和列表；每次 Launch 复用 Flow 原生
            // new-data 解析（默认表单，无默认时回退流程首个可达用户任务）。
            // 只将当次解析出的精确发布坐标写入 Runtime Snapshot，
            // 后续 ACTIVE 变化不影响已打开 Session。
            EntityNewDataFormRuntimePort.ResolvedForm resolvedForm =
                    newDataFormRuntimePort.resolveForNewData(entityCode)
                            .orElse(null);
            if (resolvedForm != null) {
                formId = resolvedForm.formId();
                formReleaseId = resolvedForm.releaseId();
                form = mapper.findFormTarget(
                        entityCode, formId, formReleaseId);
                if (form == null
                        || !java.util.Objects.equals(
                                form.releaseVersion(),
                                resolvedForm.releaseVersion().longValue())) {
                    return null;
                }
            }
        }
        if (surfaceType == SurfaceType.FORM || StringUtils.hasText(formId)) {
            if (!StringUtils.hasText(formId)
                    || ("PINNED".equals(strategy) && !StringUtils.hasText(formReleaseId))) {
                return null;
            }
            if (form == null) {
                form = mapper.findFormTarget(
                        entityCode, formId, formReleaseId);
            }
            if (form == null) {
                return null;
            }
        }

        return snapshotParser.parse(surfaceType, entityCode, listKey, formId,
                list, form, mapper.findFields(entityCode));
    }

    @Override
    public GrantState findGrant(String viewId, String applicationId) {
        return grant(mapper.findGrant(viewId, applicationId));
    }

    @Override
    public GrantState lockGrant(String viewId, String applicationId) {
        return grant(mapper.lockGrant(viewId, applicationId));
    }

    @Override
    public List<GrantState> findGrants(String viewId) {
        return mapper.findGrants(viewId).stream().map(this::grant).toList();
    }

    @Override
    public void insertGrant(GrantState grant) {
        mapper.insertGrant(row(grant));
    }

    @Override
    public int updateGrant(GrantState grant, long expectedVersion,
                           String actorId, LocalDateTime now) {
        return mapper.updateGrant(row(grant), expectedVersion, actorId, now);
    }

    @Override
    public void replaceOrigins(String grantId, List<String> origins) {
        mapper.deleteOrigins(grantId);
        if (!origins.isEmpty()) {
            mapper.insertOrigins(grantId, origins);
        }
    }

    @Override
    public int changeGrantStatus(String grantId, long expectedVersion, String status,
                                 String actorId, LocalDateTime now, boolean revoked) {
        return mapper.changeGrantStatus(
                grantId, expectedVersion, status, actorId, now, revoked);
    }

    @Override
    public boolean applicationExistsAndEnabled(String applicationId) {
        return mapper.applicationExistsAndEnabled(applicationId);
    }

    @Override
    public Page<ProviderState> findProviders(ProviderFilter filter) {
        List<ProviderState> records = mapper.findProviders(
                filter.keyword(), name(filter.status()), name(filter.type()),
                filter.pageSize(), offset(filter.pageNum(), filter.pageSize()))
                .stream().map(this::provider).toList();
        long total = mapper.countProviders(filter.keyword(), name(filter.status()),
                name(filter.type()));
        return new Page<>(records, total, filter.pageNum(), filter.pageSize());
    }

    @Override
    public ProviderState findProvider(String providerId) {
        return provider(mapper.findProvider(providerId));
    }

    @Override
    public ProviderState lockProvider(String providerId) {
        return provider(mapper.lockProvider(providerId));
    }

    @Override
    public ProviderState findProviderByIssuerAndNamespace(String issuer, String namespace) {
        return provider(mapper.findProviderByIssuerAndNamespace(issuer, namespace));
    }

    @Override
    public ProviderState lockProviderByIssuerAndNamespace(String issuer, String namespace) {
        return provider(mapper.lockProviderByIssuerAndNamespace(issuer, namespace));
    }

    @Override
    public void insertProvider(ProviderState provider) {
        mapper.insertProvider(row(provider));
    }

    @Override
    public int updateProvider(ProviderState provider, long expectedVersion,
                              String actorId, LocalDateTime now) {
        return mapper.updateProvider(row(provider), expectedVersion, actorId, now);
    }

    @Override
    public int changeProviderStatus(String providerId, long expectedVersion, String status,
                                    String actorId, LocalDateTime now, boolean revoked) {
        return mapper.changeProviderStatus(
                providerId, expectedVersion, status, actorId, now, revoked);
    }

    @Override
    public int rotateProviderKey(String providerId, long expectedVersion, String jwksJson,
                                 String actorId, LocalDateTime now) {
        return mapper.rotateProviderKey(
                providerId, expectedVersion, jwksJson, actorId, now);
    }

    @Override
    public Page<BindingState> findBindings(BindingFilter filter) {
        List<BindingState> records = mapper.findBindings(
                filter.applicationId(), filter.identityProviderId(), filter.flowUserId(),
                name(filter.status()), filter.pageSize(),
                offset(filter.pageNum(), filter.pageSize()))
                .stream().map(this::binding).toList();
        long total = mapper.countBindings(filter.applicationId(),
                filter.identityProviderId(), filter.flowUserId(), name(filter.status()));
        return new Page<>(records, total, filter.pageNum(), filter.pageSize());
    }

    @Override
    public BindingState findBinding(String bindingId) {
        return binding(mapper.findBinding(bindingId));
    }

    @Override
    public BindingState lockBinding(String bindingId) {
        return binding(mapper.lockBinding(bindingId));
    }

    @Override
    public BindingState findBindingByDigests(
            String applicationId, String providerId, List<String> digests) {
        if (digests == null || digests.isEmpty()) {
            return null;
        }
        return binding(mapper.findBindingByDigests(applicationId, providerId, digests));
    }

    @Override
    public void insertBinding(BindingState binding) {
        mapper.insertBinding(row(binding));
    }

    @Override
    public int changeBindingStatus(String bindingId, long expectedVersion, String status,
                                   String actorId, LocalDateTime now, boolean revoked) {
        return mapper.changeBindingStatus(
                bindingId, expectedVersion, status, actorId, now, revoked);
    }

    @Override
    public boolean flowUserExistsAndEnabled(String flowUserId) {
        return mapper.flowUserExistsAndEnabled(flowUserId);
    }

    @Override
    public long countActiveSessionsByView(String viewId) {
        return mapper.countActiveSessionsByView(viewId);
    }

    private ViewState view(ViewRow row) {
        return row == null ? null : new ViewState(
                row.id(), row.viewKey(), row.name(), row.description(),
                SurfaceType.valueOf(row.surfaceType()), ViewStatus.valueOf(row.status()),
                row.draftConfigJson(), row.draftRevision(), row.publishedReleaseId(),
                row.publishedRevision(), row.lockVersion(), row.securityVersion(),
                row.createBy(), row.createTime(), row.updateBy(), row.updateTime());
    }

    private static ViewRow row(ViewState value) {
        return new ViewRow(value.id(), value.viewKey(), value.name(), value.description(),
                value.surfaceType().name(), value.status().name(), value.draftConfigJson(),
                value.draftRevision(), value.publishedReleaseId(), value.publishedRevision(),
                value.lockVersion(), value.securityVersion(), value.createBy(),
                value.createTime(), value.updateBy(), value.updateTime());
    }

    private ReleaseState release(ReleaseRow row) {
        return row == null ? null : new ReleaseState(
                row.id(), row.viewId(), row.revision(), SurfaceType.valueOf(row.surfaceType()),
                row.entityCode(), row.listKey(), row.defaultFormId(), row.listReleaseId(),
                row.listReleaseVersion(), row.formReleaseId(), row.formReleaseVersion(),
                row.entryModesJson(), row.capabilitiesJson(), row.fieldPolicyJson(),
                row.actionPolicyJson(), row.contextSchemaJson(), row.contextBindingsJson(),
                row.uiConfigJson(), row.configJson(), row.configHash(), row.releaseNote(),
                row.publishedBy(), row.publishedAt());
    }

    private static ReleaseRow row(ReleaseState value) {
        return new ReleaseRow(value.id(), value.viewId(), value.revision(),
                value.surfaceType().name(), value.entityCode(), value.listKey(),
                value.defaultFormId(), value.listReleaseId(), value.listReleaseVersion(),
                value.formReleaseId(), value.formReleaseVersion(), value.entryModesJson(),
                value.capabilitiesJson(), value.fieldPolicyJson(), value.actionPolicyJson(),
                value.contextSchemaJson(), value.contextBindingsJson(), value.uiConfigJson(),
                value.configJson(), value.configHash(), value.releaseNote(),
                value.publishedBy(), value.publishedAt());
    }

    private GrantState grant(GrantRow row) {
        return row == null ? null : new GrantState(
                row.id(), row.applicationId(), row.viewId(), row.identityProviderId(),
                SecurityStatus.valueOf(row.status()), row.trustedSubjectAssertion(),
                RevisionMode.valueOf(row.revisionMode()), row.pinnedRevision(),
                row.capabilityCeilingJson(), row.maxActiveSessionsPerUser(),
                row.maxSessionSeconds(), row.launchLimitPerMinute(),
                row.runtimeLimitPerMinute(), row.maxConcurrency(), row.expiresAt(),
                row.lockVersion(), row.securityVersion(), mapper.findOrigins(row.id()),
                row.createBy(), row.createTime(), row.updateBy(), row.updateTime(),
                row.revokedBy(), row.revokedAt());
    }

    private static GrantRow row(GrantState value) {
        return new GrantRow(value.id(), value.applicationId(), value.viewId(),
                value.identityProviderId(), value.status().name(),
                value.trustedSubjectAssertion(), value.revisionMode().name(),
                value.pinnedRevision(), value.capabilityCeilingJson(),
                value.maxActiveSessionsPerUser(), value.maxSessionSeconds(),
                value.launchLimitPerMinute(), value.runtimeLimitPerMinute(),
                value.maxConcurrency(), value.expiresAt(), value.lockVersion(),
                value.securityVersion(), value.createBy(), value.createTime(),
                value.updateBy(), value.updateTime(), value.revokedBy(), value.revokedAt());
    }

    private ProviderState provider(ProviderRow row) {
        return row == null ? null : new ProviderState(
                row.id(), row.name(), ProviderType.valueOf(row.type()),
                SecurityStatus.valueOf(row.status()), row.issuer(), row.subjectNamespace(),
                row.audiencesJson(), row.algorithmsJson(),
                row.jwksMode() == null ? null : JwksMode.valueOf(row.jwksMode()),
                row.jwksJson(), row.jwksUrl(), row.clockSkewSeconds(),
                row.maxAssertionLifetimeSeconds(), row.keyVersion(), row.lockVersion(),
                row.securityVersion(), row.createBy(), row.createTime(), row.updateBy(),
                row.updateTime(), row.revokedBy(), row.revokedAt());
    }

    private static ProviderRow row(ProviderState value) {
        return new ProviderRow(value.id(), value.name(), value.type().name(),
                value.status().name(), value.issuer(), value.subjectNamespace(),
                value.audiencesJson(), value.algorithmsJson(),
                name(value.jwksMode()), value.jwksJson(), value.jwksUrl(),
                value.clockSkewSeconds(), value.maxAssertionLifetimeSeconds(),
                value.keyVersion(), value.lockVersion(), value.securityVersion(),
                value.createBy(), value.createTime(), value.updateBy(), value.updateTime(),
                value.revokedBy(), value.revokedAt());
    }

    private BindingState binding(BindingRow row) {
        return row == null ? null : new BindingState(
                row.id(), row.applicationId(), row.identityProviderId(), row.subjectDigest(),
                row.subjectDigestKeyVersion(), row.subjectHint(), row.flowUserId(),
                SecurityStatus.valueOf(row.status()), row.bindingVersion(), row.effectiveAt(),
                row.expiresAt(), row.createBy(), row.createTime(), row.updateBy(),
                row.updateTime(), row.revokedBy(), row.revokedAt());
    }

    private static BindingRow row(BindingState value) {
        return new BindingRow(value.id(), value.applicationId(), value.identityProviderId(),
                value.subjectDigest(), value.subjectDigestKeyVersion(), value.subjectHint(),
                value.flowUserId(), value.status().name(), value.bindingVersion(),
                value.effectiveAt(), value.expiresAt(), value.createBy(), value.createTime(),
                value.updateBy(), value.updateTime(), value.revokedBy(), value.revokedAt());
    }

    private static int offset(int pageNum, int pageSize) {
        return Math.max(0, (pageNum - 1) * pageSize);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.textValue())
                ? value.textValue().trim() : null;
    }

    private static String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value == null ? text(node, second) : value;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
