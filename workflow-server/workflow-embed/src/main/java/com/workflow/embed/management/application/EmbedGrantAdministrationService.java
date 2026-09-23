package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
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
    private final CurrentActorPort actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 初始化嵌入式授权管理服务，保存构造参数供后续方法使用。
     *
     * @param repository 仓储，保存在对象中供后续校验、查询或展示
     * @param validator 校验器，保存在对象中供后续校验、查询或展示
     * @param originPolicy 来源策略，保存在对象中供后续校验、查询或展示
     * @param actorProvider 操作人提供者，保存在对象中供后续校验、查询或展示
     * @param auditPort 审计端口，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EmbedGrantAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            ExactOriginPolicy originPolicy,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(repository, validator, originPolicy, actorProvider, auditPort, objectMapper,
                Clock.systemUTC());
    }

    /**
     * 初始化嵌入式授权管理服务，保存构造参数供后续方法使用。
     *
     * @param repository 仓储依赖，保存到当前对象供后续业务方法调用
     * @param validator 校验器依赖，保存到当前对象供后续业务方法调用
     * @param originPolicy 来源策略依赖，保存到当前对象供后续业务方法调用
     * @param actorProvider 操作人提供者依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    EmbedGrantAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            ExactOriginPolicy originPolicy,
            CurrentActorPort actorProvider,
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

    /**
     * 列出嵌入式授权管理；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于列出嵌入式授权管理时定位或关联目标
     * @return 授权状态集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<GrantState> list(String viewId) {
        requireView(viewId);
        return repository.findGrants(viewId);
    }

    /**
     * 首次 PUT 创建授权，后续 PUT 必须带当前 expectedVersion 才能覆盖。
     *
     * @param viewId 视图ID，后续用于处理新增或更新时定位或关联目标
     * @param applicationId 应用ID，后续用于处理新增或更新时定位或关联目标
     * @param command 本次命令，后续经校验后用于处理新增或更新
     * @return 处理后的新增或更新结果，供调用方继续处理
     */
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
                // INSERT 已由仓储恢复；继续持有 View 锁，以当前读取返回已提交版本。
                throw versionConflict(repository.lockGrant(viewId, applicationId));
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

    /**
     * 处理变更状态，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理变更状态时定位或关联目标
     * @param applicationId 应用ID，后续用于处理变更状态时定位或关联目标
     * @param command 本次命令，后续经校验后用于处理变更状态
     * @param revoke 撤销，作为 {@code EmbedManagementSupport.audit} 的输入影响后续处理
     * @return 处理后的变更状态结果，供调用方继续处理
     */
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

    /**
     * 校验命令；不满足约束时阻止后续处理。
     *
     * @param view 视图，作为 {@code validator.validateCurrentActive} 的输入影响后续处理
     * @param provider 提供者，供本方法校验命令时使用
     * @param command 本次命令，后续经校验后用于校验命令
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 读取视图能力集合；查询结果供调用方展示或继续处理。
     *
     * @param configJson 配置JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 嵌入式授权管理集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 读取配置；查询结果供调用方展示或继续处理。
     *
     * @param configJson 配置JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 读取后的配置结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private JsonNode readConfig(String configJson) {
        try {
            return objectMapper.readTree(configJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed View 配置 JSON 数据损坏", exception);
        }
    }

    /**
     * 写入能力集合；后续读取或执行将使用更新后的状态。
     *
     * @param capabilities 能力集合，作为 {@code objectMapper.writeValueAsString} 的输入影响后续处理
     * @return 写入后的能力集合文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String writeCapabilities(List<Capability> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities.stream().map(Enum::name).toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("capabilityCeiling 无法序列化", exception);
        }
    }

    /**
     * 校验并获取视图；不满足约束时阻止后续处理。
     *
     * @param viewId 视图ID，后续用于校验并获取视图时定位或关联目标
     * @return 校验并获取后的视图结果，供调用方继续处理
     */
    private ViewState requireView(String viewId) {
        return requireViewState(repository.findView(viewId));
    }

    /**
     * 校验并获取视图状态；不满足约束时阻止后续处理。
     *
     * @param view 视图，供本方法校验并获取视图状态时使用
     * @return 校验并获取后的视图状态结果，供调用方继续处理
     */
    private static ViewState requireViewState(ViewState view) {
        if (view == null) {
            throw notFound("Embed View 不存在");
        }
        return view;
    }

    /**
     * 校验并获取授权；不满足约束时阻止后续处理。
     *
     * @param viewId 视图ID，后续用于校验并获取授权时定位或关联目标
     * @param applicationId 应用ID，后续用于校验并获取授权时定位或关联目标
     * @return 校验并获取后的授权结果，供调用方继续处理
     */
    private GrantState requireGrant(String viewId, String applicationId) {
        GrantState grant = repository.findGrant(viewId, applicationId);
        if (grant == null) {
            throw notFound("Embed Grant 不存在");
        }
        return grant;
    }

    /**
     * 处理范围，并将结果传给后续步骤。
     *
     * @param value 待处理范围的原始输入，结果供调用方继续使用
     * @param min {@code min}，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param max 最大，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param name 名称，后续用于处理范围时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    /**
     * 解析{@code toggle}状态；输出作为后续校验或处理的输入。
     *
     * @param value 待解析{@code toggle}状态的原始输入，结果供调用方继续使用
     * @return 解析后的{@code toggle}状态结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的当前时间结果，供调用方继续处理
     */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 构造版本冲突异常，供调用方区分失败原因。
     *
     * @param current 当前，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的版本冲突结果，供调用方继续处理
     */
    private static EmbedManagementException versionConflict(GrantState current) {
        return new EmbedManagementException(409,
                "EMBED_CONFIGURATION_VERSION_CONFLICT", "Embed Grant 版本冲突",
                Map.of("currentVersion", current == null ? 0L : current.lockVersion()));
    }

    /**
     * 构造目标不存在异常，供调用方终止后续处理。
     *
     * @param message 消息，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的非已找到结果，供调用方继续处理
     */
    private static EmbedManagementException notFound(String message) {
        return new EmbedManagementException(404, "EMBED_RESOURCE_NOT_FOUND", message);
    }
}
