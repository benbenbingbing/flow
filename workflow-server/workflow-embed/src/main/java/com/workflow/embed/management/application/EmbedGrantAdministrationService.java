package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedManagementModel.Capability;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.RevisionMode;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.UpsertGrantCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.port.EmbedManagementRepository;
import com.workflow.embed.management.security.ExactOriginPolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Integration Application 对 Embed View 的授权与精确 Origin 管理服务。 */
@Service
public class EmbedGrantAdministrationService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final EmbedManagementRepository repository;
    private final EmbedViewConfigurationValidator validator;
    private final ExactOriginPolicy originPolicy;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public EmbedGrantAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            ExactOriginPolicy originPolicy,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(repository, validator, originPolicy, actorProvider, auditPort, objectMapper,
                Clock.systemUTC());
    }

    EmbedGrantAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            ExactOriginPolicy originPolicy,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.originPolicy = originPolicy;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GrantState> list(String viewId) {
        requireView(viewId);
        return repository.findGrants(viewId);
    }

    /** 首次 PUT 创建授权，后续 PUT 必须带当前 expectedVersion 才能覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public GrantState upsert(
            String viewId,
            String applicationId,
            UpsertGrantCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        if (command == null || command.identityProviderId() == null) {
            throw new IllegalArgumentException("Grant 请求和 identityProviderId 为必填");
        }
        // 与 View 配置保存共用行锁，避免授权校验与配置更新交叉导致能力上限错配。
        ViewState view = requireViewState(repository.lockView(viewId));
        if (!repository.applicationExistsAndEnabled(applicationId)) {
            throw new EmbedManagementException(422, "EMBED_APPLICATION_INVALID",
                    "Integration Application 不存在、未启用或已过期");
        }
        ProviderState provider = repository.findProvider(command.identityProviderId());
        if (provider == null || provider.status() != SecurityStatus.ACTIVE) {
            throw new EmbedManagementException(422, "EMBED_IDENTITY_PROVIDER_INVALID",
                    "Identity Provider 不存在或未启用");
        }
        List<String> origins = originPolicy.normalizeAll(command.allowedOrigins());
        validateCommand(view, provider, command);
        GrantState current = repository.lockGrant(viewId, applicationId);
        if (current == null && command.expectedVersion() != null) {
            throw versionConflict(null);
        }
        if (current != null) {
            if (current.status() == SecurityStatus.REVOKED) {
                throw new EmbedManagementException(409, "EMBED_GRANT_REVOKED",
                        "已撤销 Grant 为终态，不能覆盖");
            }
            if (command.expectedVersion() == null
                    || current.lockVersion() != command.expectedVersion()) {
                throw versionConflict(current);
            }
        }

        LocalDateTime now = now();
        GrantState requested = new GrantState(
                current == null ? "egr_" + IdWorker.getIdStr() : current.id(),
                applicationId,
                viewId,
                provider.id(),
                command.status() == null ? SecurityStatus.ACTIVE : command.status(),
                command.trustedSubjectAssertion(),
                // revision_mode/pinned_revision 仅为存量表结构兼容，不再参与运行时选择。
                RevisionMode.FOLLOW_ACTIVE,
                null,
                writeCapabilities(command.capabilityCeiling()),
                command.maxActiveSessionsPerUser(),
                command.maxSessionSeconds(),
                command.launchLimitPerMinute(),
                command.runtimeLimitPerMinute(),
                command.maxConcurrency(),
                command.expiresAt(),
                current == null ? 1L : current.lockVersion() + 1L,
                current == null ? 1L : current.securityVersion() + 1L,
                origins,
                current == null ? actor.userId() : current.createBy(),
                current == null ? now : current.createTime(),
                actor.userId(),
                now,
                null,
                null);
        if (current == null) {
            try {
                repository.insertGrant(requested);
            } catch (DuplicateKeyException exception) {
                // 并发首次 PUT 只能有一个成功；输掉竞争的一方按已有 Grant 的版本冲突处理。
                throw versionConflict(repository.findGrant(viewId, applicationId));
            }
        } else if (repository.updateGrant(requested, command.expectedVersion(),
                actor.userId(), now) != 1) {
            throw versionConflict(repository.findGrant(viewId, applicationId));
        }
        // Origin 与 Grant 共用事务；先更新 Grant 的 securityVersion，再整体替换来源集合。
        repository.replaceOrigins(requested.id(), origins);
        GrantState result = requireGrant(viewId, applicationId);
        EmbedManagementSupport.audit(auditPort, actor,
                current == null ? AuditAction.CREATE : AuditAction.UPDATE,
                "配置 Embed Application Grant", "EMBED_GRANT", result.id(),
                applicationId + ":" + view.viewKey(), current, result, now);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public GrantState changeStatus(
            String viewId,
            String applicationId,
            ChangeStatusCommand command,
            boolean revoke) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        GrantState current = repository.lockGrant(viewId, applicationId);
        if (current == null) {
            throw notFound("Embed Grant 不存在");
        }
        if (current.lockVersion() != command.expectedVersion()) {
            throw versionConflict(current);
        }
        SecurityStatus target = revoke
                ? SecurityStatus.REVOKED : parseToggleStatus(command.status());
        if (current.status() == SecurityStatus.REVOKED && target != SecurityStatus.REVOKED) {
            throw new EmbedManagementException(409, "EMBED_GRANT_REVOKED",
                    "已撤销 Grant 不能恢复");
        }
        if (current.status() == target) {
            return current;
        }
        LocalDateTime now = now();
        if (repository.changeGrantStatus(current.id(), command.expectedVersion(), target.name(),
                actor.userId(), now, target == SecurityStatus.REVOKED) != 1) {
            throw versionConflict(repository.findGrant(viewId, applicationId));
        }
        GrantState result = requireGrant(viewId, applicationId);
        AuditAction action = target == SecurityStatus.ACTIVE
                ? AuditAction.ENABLE : AuditAction.DISABLE;
        EmbedManagementSupport.audit(auditPort, actor, action,
                revoke ? "撤销 Embed Grant" : "变更 Embed Grant 状态",
                "EMBED_GRANT", current.id(), applicationId, current, result, now);
        return result;
    }

    private void validateCommand(
            ViewState view,
            ProviderState provider,
            UpsertGrantCommand command) {
        if (command == null || command.identityProviderId() == null) {
            throw new IllegalArgumentException("identityProviderId 为必填");
        }
        if (command.status() == SecurityStatus.REVOKED) {
            throw new IllegalArgumentException("创建或更新 Grant 时不能直接设为 REVOKED");
        }
        if (view.status() != ViewStatus.ACTIVE) {
            throw new EmbedManagementException(422, "EMBED_VIEW_NOT_CONFIGURED",
                    "Grant 必须引用已启用且有效保存的 View 配置");
        }
        var validation = validator.validateCurrentActive(
                view.surfaceType(), readConfig(view.draftConfigJson()));
        if (!validation.valid()) {
            // Grant 创建时再次解析当前 ACTIVE，避免配置保存后底层资源失效造成授权闭环断裂。
            throw new EmbedManagementException(
                    422,
                    "EMBED_VIEW_VALIDATION_FAILED",
                    "Embed view validation failed",
                    Map.of("violations", validation.violations()));
        }
        Set<String> configuredCapabilities = readViewCapabilities(
                validation.canonicalConfig());
        List<Capability> requested = command.capabilityCeiling() == null
                ? List.of() : command.capabilityCeiling();
        if (requested.isEmpty() || new LinkedHashSet<>(requested).size() != requested.size()
                || requested.stream().map(Enum::name)
                .anyMatch(capability -> !configuredCapabilities.contains(capability))) {
            throw new EmbedManagementException(422, "EMBED_GRANT_CAPABILITY_INVALID",
                    "Grant Capability 必须是 View 当前配置 Capability 的非空子集");
        }
        boolean trustedProvider = provider.type() == ProviderType.TRUSTED_EXTERNAL_ID;
        if (command.trustedSubjectAssertion() != trustedProvider) {
            throw new EmbedManagementException(422, "EMBED_TRUSTED_SUBJECT_NOT_ALLOWED",
                    trustedProvider
                            ? "TRUSTED_EXTERNAL_ID Provider 必须显式启用可信外部用户 ID"
                            : "只有 TRUSTED_EXTERNAL_ID Provider 可以直接声明外部用户 ID");
        }
        range(command.maxActiveSessionsPerUser(), 1, 10_000,
                "maxActiveSessionsPerUser");
        range(command.maxSessionSeconds(), 60, 86_400, "maxSessionSeconds");
        range(command.launchLimitPerMinute(), 1, 10_000, "launchLimitPerMinute");
        range(command.runtimeLimitPerMinute(), 1, 100_000, "runtimeLimitPerMinute");
        range(command.maxConcurrency(), 1, 1_000, "maxConcurrency");
        if (command.expiresAt() != null && !command.expiresAt().isAfter(now())) {
            throw new IllegalArgumentException("expiresAt 必须晚于当前时间");
        }
    }

    private Set<String> readViewCapabilities(String configJson) {
        try {
            JsonNode capabilities = objectMapper.readTree(configJson).path("capabilities");
            if (!capabilities.isArray()) {
                throw new IllegalStateException("Embed View capabilities 数据损坏");
            }
            return new LinkedHashSet<>(objectMapper.convertValue(capabilities, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed View 配置 JSON 数据损坏", exception);
        }
    }

    private JsonNode readConfig(String configJson) {
        try {
            return objectMapper.readTree(configJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed View 配置 JSON 数据损坏", exception);
        }
    }

    private String writeCapabilities(List<Capability> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities.stream().map(Enum::name).toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("capabilityCeiling 无法序列化", exception);
        }
    }

    private ViewState requireView(String viewId) {
        return requireViewState(repository.findView(viewId));
    }

    private static ViewState requireViewState(ViewState view) {
        if (view == null) {
            throw notFound("Embed View 不存在");
        }
        return view;
    }

    private GrantState requireGrant(String viewId, String applicationId) {
        GrantState grant = repository.findGrant(viewId, applicationId);
        if (grant == null) {
            throw notFound("Embed Grant 不存在");
        }
        return grant;
    }

    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private static SecurityStatus parseToggleStatus(String value) {
        try {
            SecurityStatus status = SecurityStatus.valueOf(
                    value == null ? "" : value.trim().toUpperCase());
            if (status == SecurityStatus.REVOKED) {
                throw new IllegalArgumentException();
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status 仅允许 ACTIVE 或 DISABLED");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static EmbedManagementException versionConflict(GrantState current) {
        return new EmbedManagementException(409,
                "EMBED_CONFIGURATION_VERSION_CONFLICT", "Embed Grant 版本冲突",
                Map.of("currentVersion", current == null ? 0L : current.lockVersion()));
    }

    private static EmbedManagementException notFound(String message) {
        return new EmbedManagementException(404, "EMBED_RESOURCE_NOT_FOUND", message);
    }
}
