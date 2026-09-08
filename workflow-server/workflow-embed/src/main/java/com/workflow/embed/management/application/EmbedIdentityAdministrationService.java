package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.embed.domain.EmbedIdentityProviderPolicy;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.crypto.EmbedSubjectDigester;
import com.workflow.embed.management.crypto.EmbedSubjectDigester.Digest;
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
import com.workflow.embed.management.port.EmbedManagementRepository;
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

    @Transactional(readOnly = true)
    public Page<ProviderState> providers(ProviderFilter filter) {
        return repository.findProviders(filter);
    }

    @Transactional(readOnly = true)
    public ProviderState provider(String providerId) {
        return requireProvider(providerId);
    }

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

    @Transactional(readOnly = true)
    public Page<BindingState> bindings(BindingFilter filter) {
        return repository.findBindings(filter);
    }

    /** Body 中的原始 Subject 只用于计算所有接受版本摘要，响应永不回显原文。 */
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

    private static boolean hasJwkText(JsonNode key, String field) {
        String value = key.path(field).asText(null);
        return StringUtils.hasText(value) && value.length() <= 16_384;
    }

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

    private static String normalizeSubject(String value) {
        if (!StringUtils.hasText(value) || value.length() > 128) {
            throw new IllegalArgumentException("Embed V1 externalSubject 长度必须为 1 到 128");
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    private static String subjectHint(String subject) {
        int codePoints = subject.codePointCount(0, subject.length());
        if (codePoints <= 4) {
            return "****";
        }
        int prefixEnd = subject.offsetByCodePoints(0, 2);
        int suffixStart = subject.offsetByCodePoints(0, codePoints - 2);
        return subject.substring(0, prefixEnd) + "***" + subject.substring(suffixStart);
    }

    private ProviderState requireProvider(String providerId) {
        return requireProviderState(repository.findProvider(providerId));
    }

    private static ProviderState requireProviderState(ProviderState provider) {
        if (provider == null) {
            throw notFound("Embed Identity Provider 不存在");
        }
        return provider;
    }

    private static BindingState requireBinding(BindingState binding) {
        if (binding == null) {
            throw notFound("Embed Identity Binding 不存在");
        }
        return binding;
    }

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

    private static void requireVersion(long current, long expected, String resource) {
        if (current != expected) {
            throw versionConflict(current, resource);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Provider JSON 数据损坏", exception);
        }
    }

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

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Provider JSON 无法序列化", exception);
        }
    }

    private static Map<String, Object> auditProvider(ProviderState provider) {
        return Map.of(
                "id", provider.id(),
                "type", provider.type(),
                "status", provider.status(),
                "subjectNamespace", provider.subjectNamespace(),
                "keyVersion", provider.keyVersion(),
                "securityVersion", provider.securityVersion());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static EmbedManagementException versionConflict(long current, String resource) {
        return new EmbedManagementException(409,
                "EMBED_CONFIGURATION_VERSION_CONFLICT", resource + " 版本冲突",
                Map.of("currentVersion", current));
    }

    private static EmbedManagementException conflict(String code, String message) {
        return new EmbedManagementException(409, code, message);
    }

    private static EmbedManagementException notFound(String message) {
        return new EmbedManagementException(404, "EMBED_RESOURCE_NOT_FOUND", message);
    }

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
