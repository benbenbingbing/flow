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
import com.workflow.embed.domain.EmbedIdentityProviderPolicy;
import com.workflow.embed.management.api.error.EmbedManagementException;
import com.workflow.embed.management.application.port.EmbedSubjectDigester;
import com.workflow.embed.management.application.port.EmbedSubjectDigester.Digest;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateBindingCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateProviderCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateProviderCommand;
import com.workflow.embed.management.application.port.EmbedManagementRepository;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Identity Provider 和外部主体精确 Binding 的管理应用服务。 */
@Service
public class EmbedIdentityAdministrationService {

    private static final String EMBED_ASSERTION_AUDIENCE = "flow-embed-launch";
    private static final Set<String> PRIVATE_JWK_FIELDS = Set.of(
            "d", "p", "q", "dp", "dq", "qi", "oth", "k", "seed");
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final EmbedManagementRepository repository;
    private final EmbedSubjectDigester subjectDigester;
    private final CurrentActorPort actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 初始化嵌入式身份管理服务，保存构造参数供后续方法使用。
     *
     * @param repository 仓储，保存在对象中供后续校验、查询或展示
     * @param subjectDigester 主体{@code digester}，保存在对象中供后续校验、查询或展示
     * @param actorProvider 操作人提供者，保存在对象中供后续校验、查询或展示
     * @param auditPort 审计端口，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EmbedIdentityAdministrationService(
            EmbedManagementRepository repository,
            EmbedSubjectDigester subjectDigester,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(repository, subjectDigester, actorProvider, auditPort, objectMapper,
                Clock.systemUTC());
    }

    /**
     * 初始化嵌入式身份管理服务，保存构造参数供后续方法使用。
     *
     * @param repository 仓储依赖，保存到当前对象供后续业务方法调用
     * @param subjectDigester 主体{@code digester}依赖，保存到当前对象供后续业务方法调用
     * @param actorProvider 操作人提供者依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    EmbedIdentityAdministrationService(
            EmbedManagementRepository repository,
            EmbedSubjectDigester subjectDigester,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.subjectDigester = subjectDigester;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 处理提供者集合，并将结果传给后续步骤。
     *
     * @param filter 过滤，作为 {@code repository.findProviders} 的输入影响后续处理
     * @return 处理后的提供者集合结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public Page<ProviderState> providers(ProviderFilter filter) {
        return repository.findProviders(filter);
    }

    /**
     * 处理提供者，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理提供者时定位或关联目标
     * @return 处理后的提供者结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public ProviderState provider(String providerId) {
        return requireProvider(providerId);
    }

    /**
     * 创建提供者；结果供后续流程传递或持久化。
     *
     * @param command 本次命令，后续经校验后用于创建提供者
     * @return 创建后的提供者结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public ProviderState createProvider(CreateProviderCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        if (command == null) {
            throw new IllegalArgumentException("Provider 请求不能为空");
        }
        ProviderMaterial material = validateProvider(command.type(), command.name(),
                command.issuer(), command.subjectNamespace(), command.audiences(),
                command.algorithms(), command.jwksMode(), command.jwks(), command.jwksUrl(),
                command.clockSkewSeconds(), command.maxAssertionLifetimeSeconds());
        // 对唯一索引范围加锁，默认 MySQL REPEATABLE_READ 下同时覆盖 issuer=NULL 的
        // TRUSTED_EXTERNAL_ID gap；数据库仍应以非 NULL discriminator 唯一约束作为最终防线。
        if (repository.lockProviderByIssuerAndNamespace(
                material.issuer(), material.subjectNamespace()) != null) {
            throw conflict("EMBED_PROVIDER_CONFLICT", "Issuer 与 Namespace 已存在");
        }
        LocalDateTime now = now();
        ProviderState provider = new ProviderState(
                "eidp_" + IdWorker.getIdStr(),
                command.name().trim(),
                command.type(),
                SecurityStatus.ACTIVE,
                material.issuer(),
                material.subjectNamespace(),
                write(material.audiences()),
                write(material.algorithms()),
                material.jwksMode(),
                material.jwks() == null ? null : write(material.jwks()),
                material.jwksUrl(),
                command.clockSkewSeconds(),
                command.maxAssertionLifetimeSeconds(),
                1L, 1L, 1L,
                actor.userId(), now, actor.userId(), now, null, null);
        try {
            repository.insertProvider(provider);
        } catch (DuplicateKeyException exception) {
            throw conflict("EMBED_PROVIDER_CONFLICT", "Issuer 与 Namespace 已存在");
        }
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.CREATE,
                "创建 Embed Identity Provider", "EMBED_IDENTITY_PROVIDER",
                provider.id(), provider.name(), null, auditProvider(provider), now);
        return provider;
    }

    /**
     * 更新提供者；后续读取或执行将使用更新后的状态。
     *
     * @param providerId 提供者ID，后续用于更新提供者时定位或关联目标
     * @param command 本次命令，后续经校验后用于更新提供者
     * @return 更新后的提供者结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public ProviderState updateProvider(String providerId, UpdateProviderCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        if (command == null) {
            throw new IllegalArgumentException("Provider 更新请求不能为空");
        }
        ProviderState current = requireProviderState(repository.lockProvider(providerId));
        if (current.status() == SecurityStatus.REVOKED) {
            throw conflict("EMBED_PROVIDER_REVOKED", "已撤销 Provider 不能修改");
        }
        requireVersion(current.lockVersion(), command.expectedVersion(), "Provider");
        String name = command.name() == null ? current.name() : command.name();
        String issuer = command.issuer() == null ? current.issuer() : command.issuer();
        String namespace = command.subjectNamespace() == null
                ? current.subjectNamespace() : command.subjectNamespace();
        if (!current.subjectNamespace().equals(namespace.trim())) {
            // Subject 摘要的 domain separation 包含 Namespace，而原始 Subject 从不落库；
            // 因此 Namespace 一旦变更就无法重算既有 Binding，只能新建 Provider 后重新绑定。
            throw conflict("EMBED_PROVIDER_NAMESPACE_IMMUTABLE",
                    "subjectNamespace 创建后不可修改，请新建 Provider 并重新绑定");
        }
        List<String> audiences = command.audiences() == null
                ? readList(current.audiencesJson()) : command.audiences();
        List<String> algorithms = command.algorithms() == null
                ? readList(current.algorithmsJson()) : command.algorithms();
        JwksMode jwksMode = command.jwksMode() == null ? current.jwksMode() : command.jwksMode();
        boolean changingJwksMode = command.jwksMode() != null
                && command.jwksMode() != current.jwksMode();
        // 模式切换时 null 表示清除另一种材料；同模式 PATCH 的 null 才表示保持原值。
        JsonNode jwks = changingJwksMode ? command.jwks()
                : command.jwks() == null ? readNullable(current.jwksJson()) : command.jwks();
        String jwksUrl = changingJwksMode ? command.jwksUrl()
                : command.jwksUrl() == null ? current.jwksUrl() : command.jwksUrl();
        int skew = command.clockSkewSeconds() == null
                ? current.clockSkewSeconds() : command.clockSkewSeconds();
        int lifetime = command.maxAssertionLifetimeSeconds() == null
                ? current.maxAssertionLifetimeSeconds() : command.maxAssertionLifetimeSeconds();
        ProviderMaterial material = validateProvider(current.type(), name, issuer, namespace,
                audiences, algorithms, jwksMode, jwks, jwksUrl, skew, lifetime);
        ProviderState duplicate = repository.findProviderByIssuerAndNamespace(
                material.issuer(), material.subjectNamespace());
        if (duplicate != null && !duplicate.id().equals(providerId)) {
            throw conflict("EMBED_PROVIDER_CONFLICT", "Issuer 与 Namespace 已存在");
        }
        LocalDateTime now = now();
        boolean keyChanged = !java.util.Objects.equals(current.jwksJson(),
                material.jwks() == null ? null : write(material.jwks()))
                || !java.util.Objects.equals(current.jwksUrl(), material.jwksUrl())
                || current.jwksMode() != material.jwksMode();
        ProviderState requested = new ProviderState(
                current.id(), name.trim(), current.type(), current.status(),
                material.issuer(), material.subjectNamespace(),
                write(material.audiences()), write(material.algorithms()),
                material.jwksMode(), material.jwks() == null ? null : write(material.jwks()),
                material.jwksUrl(), skew, lifetime,
                current.keyVersion() + (keyChanged ? 1 : 0),
                current.lockVersion() + 1,
                current.securityVersion() + 1,
                current.createBy(), current.createTime(), actor.userId(), now,
                current.revokedBy(), current.revokedAt());
        try {
            if (repository.updateProvider(requested, command.expectedVersion(),
                    actor.userId(), now) != 1) {
                throw versionConflict(requireProvider(providerId).lockVersion(), "Provider");
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("EMBED_PROVIDER_CONFLICT", "Issuer 与 Namespace 已存在");
        }
        ProviderState result = requireProvider(providerId);
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.UPDATE,
                "更新 Embed Identity Provider", "EMBED_IDENTITY_PROVIDER",
                providerId, result.name(), auditProvider(current), auditProvider(result), now);
        return result;
    }

    /**
     * 处理变更提供者状态，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理变更提供者状态时定位或关联目标
     * @param command 本次命令，后续经校验后用于处理变更提供者状态
     * @param revoke 撤销，作为 {@code EmbedManagementSupport.audit} 的输入影响后续处理
     * @return 处理后的变更提供者状态结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public ProviderState changeProviderStatus(
            String providerId, ChangeStatusCommand command, boolean revoke) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        ProviderState current = requireProviderState(repository.lockProvider(providerId));
        requireVersion(current.lockVersion(), command.expectedVersion(), "Provider");
        SecurityStatus target = revoke ? SecurityStatus.REVOKED : toggleStatus(command.status());
        if (current.status() == SecurityStatus.REVOKED && target != SecurityStatus.REVOKED) {
            throw conflict("EMBED_PROVIDER_REVOKED", "已撤销 Provider 不能恢复");
        }
        if (target == SecurityStatus.ACTIVE) {
            // 旧版本可能已经保存过当前 runtime 无法验证的 Provider。即使 ACTIVE 是幂等
            // 请求，也必须重新校验，禁止借状态切换继续发布历史漂移配置。
            requireStoredProviderValid(current, null);
        }
        if (current.status() == target) {
            return current;
        }
        LocalDateTime now = now();
        if (repository.changeProviderStatus(providerId, command.expectedVersion(), target.name(),
                actor.userId(), now, target == SecurityStatus.REVOKED) != 1) {
            throw versionConflict(requireProvider(providerId).lockVersion(), "Provider");
        }
        ProviderState result = requireProvider(providerId);
        EmbedManagementSupport.audit(auditPort, actor,
                target == SecurityStatus.ACTIVE ? AuditAction.ENABLE : AuditAction.DISABLE,
                revoke ? "撤销 Embed Identity Provider" : "变更 Embed Identity Provider 状态",
                "EMBED_IDENTITY_PROVIDER", providerId, current.name(),
                auditProvider(current), auditProvider(result), now);
        return result;
    }

    /**
     * 处理轮换提供者键，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理轮换提供者键时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理轮换提供者键时使用
     * @param jwks JWKS，作为 {@code validatePublicJwks} 的输入影响后续处理
     * @return 处理后的轮换提供者键结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public ProviderState rotateProviderKey(
            String providerId, long expectedVersion, JsonNode jwks) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        ProviderState current = requireProviderState(repository.lockProvider(providerId));
        requireVersion(current.lockVersion(), expectedVersion, "Provider");
        if (current.status() == SecurityStatus.REVOKED
                || current.type() != ProviderType.SIGNED_JWT
                || current.jwksMode() != JwksMode.STATIC_JWK_SET) {
            throw conflict("EMBED_PROVIDER_KEY_ROTATION_NOT_ALLOWED",
                    "只有未撤销的 STATIC_JWK_SET Provider 可以轮换静态公钥");
        }
        validatePublicJwks(jwks);
        // 新 JWKS 本身属于请求参数错误（400）；其余旧字段若仍不符合当前 V1 契约则属于
        // 不可发布的存量资源（422），不能通过只轮换 key 绕过全量 Provider 校验。
        requireStoredProviderValid(current, jwks);
        LocalDateTime now = now();
        if (repository.rotateProviderKey(providerId, expectedVersion, write(jwks),
                actor.userId(), now) != 1) {
            throw versionConflict(requireProvider(providerId).lockVersion(), "Provider");
        }
        ProviderState result = requireProvider(providerId);
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.CONFIGURE,
                "轮换 Embed Identity Provider 公钥", "EMBED_IDENTITY_PROVIDER",
                providerId, current.name(),
                Map.of("keyVersion", current.keyVersion()),
                Map.of("keyVersion", result.keyVersion()), now);
        return result;
    }

    /**
     * 处理绑定集合，并将结果传给后续步骤。
     *
     * @param filter 过滤，作为 {@code repository.findBindings} 的输入影响后续处理
     * @return 处理后的绑定集合结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public Page<BindingState> bindings(BindingFilter filter) {
        return repository.findBindings(filter);
    }

    /**
     * Body 中的原始 Subject 只用于计算所有接受版本摘要，响应永不回显原文。
     *
     * @param applicationId 应用ID，后续用于查找绑定时定位或关联目标
     * @param providerId 提供者ID，后续用于查找绑定时定位或关联目标
     * @param externalSubject 外部主体，作为 {@code normalizeSubject} 的输入影响后续处理
     * @return 查找后的绑定结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public BindingState lookupBinding(
            String applicationId, String providerId, String externalSubject) {
        String normalized = normalizeSubject(externalSubject);
        ProviderState provider = requireProvider(providerId);
        List<String> digests = subjectDigester.accepted(
                applicationId, providerId, provider.subjectNamespace(), normalized)
                .stream().map(Digest::value).toList();
        return repository.findBindingByDigests(applicationId, providerId, digests);
    }

    /**
     * 创建绑定；结果供后续流程传递或持久化。
     *
     * @param command 本次命令，后续经校验后用于创建绑定
     * @return 创建后的绑定结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public BindingState createBinding(CreateBindingCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        if (command == null || !StringUtils.hasText(command.applicationId())
                || !StringUtils.hasText(command.identityProviderId())
                || !StringUtils.hasText(command.flowUserId())) {
            throw new IllegalArgumentException(
                    "applicationId、identityProviderId 和 flowUserId 为必填");
        }
        if (!repository.applicationExistsAndEnabled(command.applicationId())) {
            throw new EmbedManagementException(422, "EMBED_APPLICATION_INVALID",
                    "Integration Application 不存在、未启用或已过期");
        }
        ProviderState provider = requireProvider(command.identityProviderId());
        if (provider.status() != SecurityStatus.ACTIVE) {
            throw new EmbedManagementException(422, "EMBED_IDENTITY_PROVIDER_INVALID",
                    "Identity Provider 未启用");
        }
        if (!repository.flowUserExistsAndEnabled(command.flowUserId())) {
            throw new EmbedManagementException(422, "EMBED_FLOW_USER_INVALID",
                    "Flow 用户不存在或未启用");
        }
        String subject = normalizeSubject(command.externalSubject());
        List<Digest> accepted = subjectDigester.accepted(
                command.applicationId(), command.identityProviderId(),
                provider.subjectNamespace(), subject);
        if (repository.findBindingByDigests(command.applicationId(),
                command.identityProviderId(), accepted.stream().map(Digest::value).toList()) != null) {
            throw conflict("EMBED_IDENTITY_BINDING_CONFLICT", "外部主体已经存在精确绑定");
        }
        Digest digest = subjectDigester.current(
                command.applicationId(), command.identityProviderId(),
                provider.subjectNamespace(), subject);
        LocalDateTime now = now();
        LocalDateTime effectiveAt = command.effectiveAt() == null ? now : command.effectiveAt();
        if (command.expiresAt() != null
                && (!command.expiresAt().isAfter(effectiveAt)
                || !command.expiresAt().isAfter(now))) {
            throw new IllegalArgumentException("expiresAt 必须晚于 effectiveAt 和当前时间");
        }
        BindingState binding = new BindingState(
                "eib_" + IdWorker.getIdStr(),
                command.applicationId(),
                command.identityProviderId(),
                digest.value(),
                digest.keyVersion(),
                subjectHint(subject),
                command.flowUserId().trim(),
                true,
                SecurityStatus.ACTIVE,
                1L,
                effectiveAt,
                command.expiresAt(),
                actor.userId(), now, actor.userId(), now, null, null);
        try {
            repository.insertBinding(binding);
        } catch (DuplicateKeyException exception) {
            // accepted-key 预查覆盖轮换窗口，唯一索引负责裁决同一当前摘要的并发创建。
            throw conflict("EMBED_IDENTITY_BINDING_CONFLICT", "外部主体已经存在精确绑定");
        }
        // 审计只写 HMAC 指纹/Hint，不携带请求中的原始 Subject 或 remark。
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.CREATE,
                "创建 Embed External Identity Binding", "EMBED_IDENTITY_BINDING",
                binding.id(), binding.subjectHint(), null, binding, now);
        return binding;
    }

    /**
     * 处理变更绑定状态，并将结果传给后续步骤。
     *
     * @param bindingId 绑定ID，后续用于处理变更绑定状态时定位或关联目标
     * @param command 本次命令，后续经校验后用于处理变更绑定状态
     * @param revoke 撤销，作为 {@code EmbedManagementSupport.audit} 的输入影响后续处理
     * @return 处理后的变更绑定状态结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public BindingState changeBindingStatus(
            String bindingId, ChangeStatusCommand command, boolean revoke) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        BindingState current = requireBinding(repository.lockBinding(bindingId));
        requireVersion(current.bindingVersion(), command.expectedVersion(), "Binding");
        SecurityStatus target = revoke ? SecurityStatus.REVOKED : toggleStatus(command.status());
        if (current.status() == SecurityStatus.REVOKED && target != SecurityStatus.REVOKED) {
            throw conflict("EMBED_BINDING_REVOKED", "已撤销 Binding 不能恢复");
        }
        if (current.status() == target) {
            // 加锁查询不联查 sys_user，避免改变安全状态更新的锁顺序；只读回查补全实时就绪态。
            return requireBinding(repository.findBinding(bindingId));
        }
        LocalDateTime now = now();
        if (repository.changeBindingStatus(bindingId, command.expectedVersion(), target.name(),
                actor.userId(), now, target == SecurityStatus.REVOKED) != 1) {
            throw versionConflict(requireBinding(repository.findBinding(bindingId)).bindingVersion(),
                    "Binding");
        }
        BindingState result = requireBinding(repository.findBinding(bindingId));
        EmbedManagementSupport.audit(auditPort, actor,
                target == SecurityStatus.ACTIVE ? AuditAction.ENABLE : AuditAction.DISABLE,
                revoke ? "撤销 Embed External Identity Binding" : "变更 Embed Binding 状态",
                "EMBED_IDENTITY_BINDING", bindingId, current.subjectHint(),
                current, result, now);
        return result;
    }

    /**
     * 校验提供者；不满足约束时阻止后续处理。
     *
     * @param type 类型标识，决定后续提供者采用的处理分支
     * @param name 名称，后续用于校验提供者时匹配或展示
     * @param issuer 签发方，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param namespace 命名空间，作为 {@code ProviderMaterial} 的输入影响后续处理
     * @param audiences {@code audiences}，作为 {@code uniqueStrings} 的输入影响后续处理
     * @param algorithms {@code algorithms}，作为 {@code uniqueStrings} 的输入影响后续处理
     * @param jwksMode JWKS模式标识，决定后续提供者采用的处理分支
     * @param jwks JWKS，作为 {@code validatePublicJwks} 的输入影响后续处理
     * @param jwksUrl JWKSURL，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param skew {@code skew}，供本方法校验提供者时使用
     * @param lifetime {@code lifetime}，后续用于判断有效期或展示该事件的发生时间
     * @return 校验后的提供者结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private ProviderMaterial validateProvider(
            ProviderType type,
            String name,
            String issuer,
            String namespace,
            List<String> audiences,
            List<String> algorithms,
            JwksMode jwksMode,
            JsonNode jwks,
            String jwksUrl,
            int skew,
            int lifetime) {
        if (type == null || !StringUtils.hasText(name) || name.trim().length() > 128) {
            throw new IllegalArgumentException("Provider type 与 1 到 128 字符的 name 为必填");
        }
        if (!StringUtils.hasText(namespace)
                || !NAMESPACE.matcher(namespace.trim()).matches()) {
            throw new IllegalArgumentException("subjectNamespace 格式不合法");
        }
        if (skew < 0 || skew > 300 || lifetime < 1 || lifetime > 300) {
            throw new IllegalArgumentException(
                    "clockSkewSeconds 必须为 0..300，maxAssertionLifetimeSeconds 必须为 1..300");
        }
        if (type == ProviderType.TRUSTED_EXTERNAL_ID) {
            if (StringUtils.hasText(issuer) || jwksMode != null || jwks != null
                    || StringUtils.hasText(jwksUrl)
                    || (audiences != null && !audiences.isEmpty())
                    || (algorithms != null && !algorithms.isEmpty())) {
                throw new IllegalArgumentException(
                        "TRUSTED_EXTERNAL_ID 不能配置 issuer、audience、algorithm 或 JWKS 验签材料");
            }
            return new ProviderMaterial(null, namespace.trim(), List.of(), List.of(),
                    null, null, null);
        }
        String normalizedIssuer = exactHttpsUri(issuer, "issuer", false);
        List<String> normalizedAudiences = uniqueStrings(
                audiences, "audiences", 1, 20,
                EmbedIdentityProviderPolicy.MAX_AUDIENCE_LENGTH);
        // 人员断言必须使用 Embed 专用 audience，不能把 Flow 普通 API Token 当作人员断言
        // 重放。Provider 可以为迁移期保留其他 audience，但固定值不可缺失。
        if (!normalizedAudiences.contains(EMBED_ASSERTION_AUDIENCE)) {
            throw new IllegalArgumentException(
                    "audiences 必须包含 Embed 专用值 " + EMBED_ASSERTION_AUDIENCE);
        }
        List<String> normalizedAlgorithms = uniqueStrings(
                algorithms, "algorithms", 1, 10, 64);
        if (normalizedAlgorithms.stream()
                .anyMatch(algorithm -> !EmbedIdentityProviderPolicy
                        .supportsSigningAlgorithm(algorithm))) {
            throw new IllegalArgumentException("algorithms 只允许平台支持的非对称签名算法");
        }
        if (jwksMode == JwksMode.STATIC_JWK_SET) {
            validatePublicJwks(jwks);
            if (StringUtils.hasText(jwksUrl)) {
                throw new IllegalArgumentException("STATIC_JWK_SET 不能同时配置 jwksUrl");
            }
            return new ProviderMaterial(normalizedIssuer, namespace.trim(),
                    normalizedAudiences, normalizedAlgorithms, jwksMode, jwks, null);
        }
        if (jwksMode == JwksMode.REMOTE_JWKS) {
            if (jwks != null) {
                throw new IllegalArgumentException("REMOTE_JWKS 不能同时配置静态 jwks");
            }
            String normalizedUrl = exactHttpsUri(jwksUrl, "jwksUrl", true);
            rejectPrivateLiteralHost(normalizedUrl);
            return new ProviderMaterial(normalizedIssuer, namespace.trim(),
                    normalizedAudiences, normalizedAlgorithms, jwksMode, null, normalizedUrl);
        }
        throw new IllegalArgumentException("SIGNED_JWT 必须配置 STATIC_JWK_SET 或 REMOTE_JWKS");
    }

    /**
     * 校验公开JWKS；不满足约束时阻止后续处理。
     *
     * @param jwks JWKS，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void validatePublicJwks(JsonNode jwks) {
        if (jwks == null || !jwks.isObject() || !jwks.path("keys").isArray()
                || jwks.path("keys").isEmpty() || jwks.path("keys").size() > 20
                || jwks.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > 262_144) {
            throw new IllegalArgumentException("jwks.keys 必须包含 1 到 20 把公钥");
        }
        LinkedHashSet<String> keyIds = new LinkedHashSet<>();
        for (JsonNode key : jwks.path("keys")) {
            String kid = key.path("kid").asText(null);
            String keyType = key.path("kty").asText(null);
            if (!key.isObject() || !StringUtils.hasText(kid) || kid.length() > 128
                    || !keyIds.add(kid)
                    || !EmbedIdentityProviderPolicy.supportsPublicJwkType(keyType)) {
                throw new IllegalArgumentException("每把 JWK 必须是含 kid 的 Object");
            }
            key.fieldNames().forEachRemaining(name -> {
                if (PRIVATE_JWK_FIELDS.contains(name)) {
                    throw new IllegalArgumentException("JWKS 不能包含私钥参数");
                }
            });
            boolean publicMaterialPresent = switch (keyType) {
                case "RSA" -> hasJwkText(key, "n") && hasJwkText(key, "e");
                case "EC" -> hasJwkText(key, "crv") && hasJwkText(key, "x")
                        && hasJwkText(key, "y");
                default -> false;
            };
            if (!publicMaterialPresent
                    || (key.hasNonNull("use") && !"sig".equals(key.path("use").asText()))) {
                throw new IllegalArgumentException("JWK 缺少受支持的公开验签材料");
            }
        }
    }

    /**
     * 判断是否具有{@code jwk}文本；判断结果决定调用方的后续分支。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param field 字段，作为 {@code key.path} 的输入影响后续处理
     * @return {@code jwk}文本条件成立时为 true，否则为 false
     */
    private static boolean hasJwkText(JsonNode key, String field) {
        String value = key.path(field).asText(null);
        return StringUtils.hasText(value) && value.length() <= 16_384;
    }

    /**
     * 生成精确{@code https}{@code uri}文本，供后续匹配或展示。
     *
     * @param value 待处理精确{@code https}{@code uri}的原始输入，结果供调用方继续使用
     * @param field 字段，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param noQuery 无查询，供本方法处理精确{@code https}{@code uri}时使用
     * @return 处理后的精确{@code https}{@code uri}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static String exactHttpsUri(String value, String field, boolean noQuery) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " 为必填 HTTPS URI");
        }
        try {
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (noQuery && uri.getRawQuery() != null)) {
                throw new IllegalArgumentException(field + " 必须是受控 HTTPS URI");
            }
            return uri.normalize().toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(field + " 必须是合法 URI", exception);
        }
    }

    /**
     * 处理驳回{@code private}字面值主机，并将结果传给后续步骤。
     *
     * @param value 待处理驳回{@code private}字面值主机的原始输入，结果供调用方继续使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void rejectPrivateLiteralHost(String value) {
        URI uri = URI.create(value);
        String host = uri.getHost();
        if ("localhost".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("jwksUrl 不允许 localhost 或私网地址");
        }
        String literal = host != null && host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        // 只对真正的 IPv4/IPv6 字面量调用 InetAddress，避免把 dead.beef 这类纯十六进制域名
        // 当成地址后触发管理请求中的 DNS 查询。远程抓取器仍须在每次解析后做 egress 校验。
        if (literal != null
                && (literal.matches("[0-9.]+") || literal.contains(":"))) {
            try {
                InetAddress address = InetAddress.getByName(literal);
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress() || isSpecialPrivateRange(address)) {
                    throw new IllegalArgumentException("jwksUrl 不允许 localhost 或私网地址");
                }
            } catch (java.net.UnknownHostException exception) {
                throw new IllegalArgumentException("jwksUrl IP 地址不合法", exception);
            }
        }
    }

    /**
     * 判断是否{@code special}{@code private}范围；判断结果决定调用方的后续分支。
     *
     * @param address 地址，供本方法判断是否{@code special}{@code private}范围时使用
     * @return {@code special}{@code private}范围条件成立时为 true，否则为 false
     */
    private static boolean isSpecialPrivateRange(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 100 && second >= 64 && second <= 127;
        }
        // Java 的 isSiteLocalAddress 不覆盖 IPv6 Unique Local Address fc00::/7。
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    /**
     * 整理唯一{@code strings}数据，供调用方遍历或继续处理。
     *
     * @param source 待处理唯一{@code strings}的原始输入，结果供调用方继续使用
     * @param field 字段，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param min {@code min}，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param max 最大，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param maxValueLength 最大值长度，供本方法处理唯一{@code strings}时使用
     * @return 嵌入式身份管理集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static List<String> uniqueStrings(
            List<String> source, String field, int min, int max, int maxValueLength) {
        if (source == null || source.size() < min || source.size() > max) {
            throw new IllegalArgumentException(field + " 数量必须为 " + min + " 到 " + max);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : source) {
            if (!StringUtils.hasText(value) || value.length() > maxValueLength) {
                throw new IllegalArgumentException(field + " 包含非法值");
            }
            result.add(value.trim());
        }
        if (result.size() != source.size()) {
            throw new IllegalArgumentException(field + " 不能包含重复值");
        }
        return List.copyOf(result);
    }

    /**
     * 对已持久化 Provider 做启用前全量重校验，并将历史漂移稳定归类为 422。
     *
     * <p>{@code jwksOverride} 仅供静态 key 轮换使用；传入前应先按请求参数规则校验，从而
     * 保持新请求错误为 400、存量资源不可发布为 422。</p>
     *
     * @param provider 提供者，作为 {@code readNullable} 的输入影响后续处理
     * @param jwksOverride JWKS覆盖，供本方法校验并获取已存储提供者有效时使用
     */
    private void requireStoredProviderValid(ProviderState provider, JsonNode jwksOverride) {
        try {
            JsonNode jwks = jwksOverride == null
                    ? readNullable(provider.jwksJson()) : jwksOverride;
            validateProvider(
                    provider.type(),
                    provider.name(),
                    provider.issuer(),
                    provider.subjectNamespace(),
                    readList(provider.audiencesJson()),
                    readList(provider.algorithmsJson()),
                    provider.jwksMode(),
                    jwks,
                    provider.jwksUrl(),
                    provider.clockSkewSeconds(),
                    provider.maxAssertionLifetimeSeconds());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new EmbedManagementException(
                    422,
                    "EMBED_IDENTITY_PROVIDER_INVALID",
                    "Identity Provider 配置不符合 Embed V1 验签策略");
        }
    }

    /**
     * 规范化主体；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化主体的原始输入，结果供调用方继续使用
     * @return 规范化后的主体文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static String normalizeSubject(String value) {
        if (!StringUtils.hasText(value) || value.length() > 128) {
            throw new IllegalArgumentException("Embed V1 externalSubject 长度必须为 1 到 128");
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    /**
     * 生成主体{@code hint}文本，供后续匹配或展示。
     *
     * @param subject 主体，作为 {@code subject.codePointCount} 的输入影响后续处理
     * @return 处理后的主体{@code hint}文本，供调用方比较或展示
     */
    private static String subjectHint(String subject) {
        int codePoints = subject.codePointCount(0, subject.length());
        if (codePoints <= 4) {
            return "****";
        }
        int prefixEnd = subject.offsetByCodePoints(0, 2);
        int suffixStart = subject.offsetByCodePoints(0, codePoints - 2);
        return subject.substring(0, prefixEnd) + "***" + subject.substring(suffixStart);
    }

    /**
     * 校验并获取提供者；不满足约束时阻止后续处理。
     *
     * @param providerId 提供者ID，后续用于校验并获取提供者时定位或关联目标
     * @return 校验并获取后的提供者结果，供调用方继续处理
     */
    private ProviderState requireProvider(String providerId) {
        return requireProviderState(repository.findProvider(providerId));
    }

    /**
     * 校验并获取提供者状态；不满足约束时阻止后续处理。
     *
     * @param provider 提供者，供本方法校验并获取提供者状态时使用
     * @return 校验并获取后的提供者状态结果，供调用方继续处理
     */
    private static ProviderState requireProviderState(ProviderState provider) {
        if (provider == null) {
            throw notFound("Embed Identity Provider 不存在");
        }
        return provider;
    }

    /**
     * 校验并获取绑定；不满足约束时阻止后续处理。
     *
     * @param binding 绑定，供本方法校验并获取绑定时使用
     * @return 校验并获取后的绑定结果，供调用方继续处理
     */
    private static BindingState requireBinding(BindingState binding) {
        if (binding == null) {
            throw notFound("Embed Identity Binding 不存在");
        }
        return binding;
    }

    /**
     * 处理{@code toggle}状态，并将结果传给后续步骤。
     *
     * @param value 待处理{@code toggle}状态的原始输入，结果供调用方继续使用
     * @return 处理后的{@code toggle}状态结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static SecurityStatus toggleStatus(String value) {
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
     * 校验并获取版本；不满足约束时阻止后续处理。
     *
     * @param current 当前，作为 {@code versionConflict} 的输入影响后续处理
     * @param expected 预期，供本方法校验并获取版本时使用
     * @param resource 资源，作为 {@code versionConflict} 的输入影响后续处理
     */
    private static void requireVersion(long current, long expected, String resource) {
        if (current != expected) {
            throw versionConflict(current, resource);
        }
    }

    /**
     * 读取嵌入式身份管理列表；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 嵌入式身份管理集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Provider JSON 数据损坏", exception);
        }
    }

    /**
     * 读取可空；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 读取后的可空结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private JsonNode readNullable(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Provider JWKS JSON 数据损坏", exception);
        }
    }

    /**
     * 写入嵌入式身份管理；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入嵌入式身份管理的原始输入，结果供调用方继续使用
     * @return 写入后的嵌入式身份管理文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Provider JSON 无法序列化", exception);
        }
    }

    /**
     * 审计提供者；供后续追溯或审计使用。
     *
     * @param provider 提供者，供本方法审计提供者时使用
     * @return 提供者键值结果，供调用方继续处理
     */
    private static Map<String, Object> auditProvider(ProviderState provider) {
        return Map.of(
                "id", provider.id(),
                "type", provider.type(),
                "status", provider.status(),
                "subjectNamespace", provider.subjectNamespace(),
                "keyVersion", provider.keyVersion(),
                "securityVersion", provider.securityVersion());
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
     * @param resource 资源，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的版本冲突结果，供调用方继续处理
     */
    private static EmbedManagementException versionConflict(long current, String resource) {
        return new EmbedManagementException(409,
                "EMBED_CONFIGURATION_VERSION_CONFLICT", resource + " 版本冲突",
                Map.of("currentVersion", current));
    }

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @param code 编码，后续用于处理冲突时定位或关联目标
     * @param message 消息，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的冲突结果，供调用方继续处理
     */
    private static EmbedManagementException conflict(String code, String message) {
        return new EmbedManagementException(409, code, message);
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

    /**
     * 封装提供者材料的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param issuer 签发方，保存在对象中供后续校验、查询或展示
     * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
     * @param audiences {@code audiences}，保存在对象中供后续校验、查询或展示
     * @param algorithms {@code algorithms}，保存在对象中供后续校验、查询或展示
     * @param jwksMode JWKS模式标识，决定后续提供者材料采用的处理分支
     * @param jwks JWKS，保存在对象中供后续校验、查询或展示
     * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
     */
    private record ProviderMaterial(
            String issuer,
            String subjectNamespace,
            List<String> audiences,
            List<String> algorithms,
            JwksMode jwksMode,
            JsonNode jwks,
            String jwksUrl) {
    }
}
