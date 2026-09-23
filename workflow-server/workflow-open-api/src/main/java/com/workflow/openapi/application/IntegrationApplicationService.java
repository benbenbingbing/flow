package com.workflow.openapi.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.api.response.IntegrationApplicationView;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.network.IpNetwork;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理接入应用及其 Client Credential 生命周期。
 *
 * <p>接入应用不再承载开放流程、Scope 或 Connector 配置；保留的限流、
 * 并发与来源网络策略仅用于 Embed launch 边界。</p>
 */
@Service
public class IntegrationApplicationService {

    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    private static final int DEFAULT_MAX_CONCURRENCY = 10;
    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationCredentialMapper credentialMapper;
    private final IntegrationSecretGenerator secretGenerator;
    private final IntegrationSecretHasher secretHasher;
    private final CurrentActorPort actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 初始化集成应用服务，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器，保存在对象中供后续校验、查询或展示
     * @param credentialMapper 凭据映射器，保存在对象中供后续校验、查询或展示
     * @param secretGenerator 密钥生成器，保存在对象中供后续校验、查询或展示
     * @param secretHasher 密钥{@code hasher}，保存在对象中供后续校验、查询或展示
     * @param actorProvider 操作人提供者，保存在对象中供后续校验、查询或展示
     * @param auditPort 审计端口，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public IntegrationApplicationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            IntegrationSecretGenerator secretGenerator,
            IntegrationSecretHasher secretHasher,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(
                applicationMapper,
                credentialMapper,
                secretGenerator,
                secretHasher,
                actorProvider,
                auditPort,
                objectMapper,
                Clock.systemUTC());
    }

    /**
     * 初始化集成应用服务，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器依赖，保存到当前对象供后续业务方法调用
     * @param credentialMapper 凭据映射器依赖，保存到当前对象供后续业务方法调用
     * @param secretGenerator 密钥生成器依赖，保存到当前对象供后续业务方法调用
     * @param secretHasher 密钥{@code hasher}依赖，保存到当前对象供后续业务方法调用
     * @param actorProvider 操作人提供者依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    IntegrationApplicationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            IntegrationSecretGenerator secretGenerator,
            IntegrationSecretHasher secretHasher,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.credentialMapper = credentialMapper;
        this.secretGenerator = secretGenerator;
        this.secretHasher = secretHasher;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 返回最近创建的接入应用，并批量附带活动凭据的非敏感摘要。
     *
     * @return 集成应用视图集合，供调用方遍历或展示
     */
    @Transactional(readOnly = true)
    public List<IntegrationApplicationView> list() {
        List<IntegrationApplicationRecord> applications =
                applicationMapper.findRecent();
        if (applications.isEmpty()) {
            return List.of();
        }
        List<String> applicationIds = applications.stream()
                .map(IntegrationApplicationRecord::getId)
                .toList();
        Map<String, IntegrationApplicationCredentialRecord> credentials =
                credentialMapper.findActiveByApplicationIds(applicationIds)
                        .stream()
                        .collect(Collectors.toMap(
                                IntegrationApplicationCredentialRecord
                                        ::getApplicationId,
                                Function.identity()));
        return applications.stream()
                .map(application -> toView(
                        application,
                        credentials.get(application.getId())))
                .toList();
    }

    /**
     * 创建应用并签发首个 Client Secret。
     *
     * <p>明文 Secret 仅在本次返回中出现，持久化层只保存 Argon2 摘要。</p>
     *
     * @param request 本次请求，后续经校验后用于创建集成应用
     * @return 创建后的集成应用结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public IssuedIntegrationCredentialView create(
            CreateIntegrationApplicationRequest request) {
        CurrentActor actor = requireActor();
        List<String> cidrs = validateCidrs(request.allowedSourceCidrs());
        LocalDateTime now = now();

        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId(IdWorker.getIdStr());
        application.setClientId(secretGenerator.newClientId());
        application.setApplicationName(request.applicationName().trim());
        application.setDescription(trimToNull(request.description()));
        application.setOwnerOrganizationId(
                trimToNull(request.ownerOrganizationId()));
        application.setStatus(ApplicationStatus.ACTIVE.name());
        application.setRateLimitPerMinute(request.rateLimitPerMinute() == null
                ? DEFAULT_RATE_LIMIT_PER_MINUTE
                : request.rateLimitPerMinute());
        application.setMaxConcurrency(request.maxConcurrency() == null
                ? DEFAULT_MAX_CONCURRENCY
                : request.maxConcurrency());
        application.setAllowedSourceCidrs(writeCidrs(cidrs));
        application.setExpiresAt(toLocalDateTime(request.expiresAt()));
        application.setVersion(0L);
        application.setCreatedBy(actor.userId());
        application.setUpdatedBy(actor.userId());
        application.setCreateTime(now);
        application.setUpdateTime(now);
        applicationMapper.insert(application);

        IssuedSecret issued = createCredential(
                application.getId(),
                1L,
                request.expiresAt(),
                actor.userId(),
                now);
        recordAudit(
                AuditAction.CREATE,
                "创建接入应用",
                application,
                actor,
                true);
        return new IssuedIntegrationCredentialView(
                toView(application),
                issued.secret(),
                issued.expiresAt());
    }

    /**
     * 按乐观锁版本更新应用状态，并在吊销应用时同步吊销活动凭据。
     *
     * @param applicationId 应用ID，后续用于更新状态时定位或关联目标
     * @param request 本次请求，后续经校验后用于更新状态
     * @return 更新后的状态结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public IntegrationApplicationView updateStatus(
            String applicationId,
            UpdateIntegrationStatusRequest request) {
        CurrentActor actor = requireActor();
        ApplicationStatus target = ApplicationStatus.parse(request.status());
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireExpectedVersion(application, request.expectedVersion());
        ApplicationStatus current = ApplicationStatus.valueOf(
                application.getStatus());
        if (current == ApplicationStatus.REVOKED
                && target != ApplicationStatus.REVOKED) {
            throw new BusinessConflictException(
                    "INTEGRATION_APPLICATION_REVOKED",
                    "已吊销的接入应用不能重新启用");
        }
        if (target == ApplicationStatus.REVOKED) {
            credentialMapper.revokeActive(applicationId, actor.userId(), now);
        }
        int updated = applicationMapper.updateStatus(
                applicationId,
                target.name(),
                request.expectedVersion(),
                actor.userId(),
                now);
        if (updated != 1) {
            throw versionConflict();
        }
        application.setStatus(target.name());
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(actor.userId());
        application.setUpdateTime(now);
        recordAudit(
                target == ApplicationStatus.ACTIVE
                        ? AuditAction.ENABLE
                        : AuditAction.DISABLE,
                target == ApplicationStatus.REVOKED
                        ? "吊销接入应用"
                        : "变更接入应用状态",
                application,
                actor,
                true);
        return toView(application);
    }

    /**
     * 吊销旧凭据并签发新的 Client Secret，避免同时存在多个活动凭据。
     *
     * @param applicationId 应用ID，后续用于处理轮换凭据时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理轮换凭据
     * @return 处理后的轮换凭据结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public IssuedIntegrationCredentialView rotateCredential(
            String applicationId,
            RotateIntegrationCredentialRequest request) {
        CurrentActor actor = requireActor();
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireNotRevoked(application);
        requireExpectedVersion(application, request.expectedVersion());
        credentialMapper.revokeActive(applicationId, actor.userId(), now);
        long version = credentialMapper.findLatestVersion(applicationId) + 1;
        IssuedSecret issued = createCredential(
                applicationId,
                version,
                request.expiresAt(),
                actor.userId(),
                now);
        advanceApplicationVersion(
                application,
                request.expectedVersion(),
                actor.userId(),
                now);
        recordAudit(
                AuditAction.CONFIGURE,
                "轮换接入应用凭据",
                application,
                actor,
                true);
        return new IssuedIntegrationCredentialView(
                toView(application),
                issued.secret(),
                issued.expiresAt());
    }

    /**
     * 显式吊销当前活动凭据，并推进应用版本用于并发控制。
     *
     * @param applicationId 应用ID，后续用于撤销凭据时定位或关联目标
     * @param request 本次请求，后续经校验后用于撤销凭据
     * @return 撤销后的凭据结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public IntegrationApplicationView revokeCredential(
            String applicationId,
            RevokeIntegrationCredentialRequest request) {
        CurrentActor actor = requireActor();
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireNotRevoked(application);
        requireExpectedVersion(application, request.expectedVersion());
        IntegrationApplicationCredentialRecord credential =
                credentialMapper.findActive(applicationId);
        if (credential == null
                || credentialMapper.revokeActive(
                        applicationId,
                        actor.userId(),
                        now) != 1) {
            throw new BusinessConflictException(
                    "INTEGRATION_CREDENTIAL_NOT_ACTIVE",
                    "接入应用没有可吊销的活动凭据");
        }
        advanceApplicationVersion(
                application,
                request.expectedVersion(),
                actor.userId(),
                now);
        recordCredentialAudit(
                "吊销接入应用凭据",
                application,
                credential,
                actor);
        return toView(application);
    }

    /**
     * 创建凭据；结果供后续流程传递或持久化。
     *
     * @param applicationId 应用ID，后续用于创建凭据时定位或关联目标
     * @param version 版本，作为 {@code credential.setCredentialVersion} 的输入影响后续处理
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，作为 {@code credential.setCreateTime} 的输入影响后续处理
     * @return 创建后的凭据结果，供调用方继续处理
     */
    private IssuedSecret createCredential(
            String applicationId,
            long version,
            Instant expiresAt,
            String operatorId,
            LocalDateTime now) {
        String secret = secretGenerator.newClientSecret();
        IntegrationApplicationCredentialRecord credential =
                new IntegrationApplicationCredentialRecord();
        credential.setId(IdWorker.getIdStr());
        credential.setApplicationId(applicationId);
        credential.setSecretHash(secretHasher.hash(secret));
        credential.setCredentialHint(
                secret.substring(Math.max(0, secret.length() - 8)));
        credential.setStatus("ACTIVE");
        credential.setCredentialVersion(version);
        credential.setExpiresAt(toLocalDateTime(expiresAt));
        credential.setCreatedBy(operatorId);
        credential.setCreateTime(now);
        credentialMapper.insert(credential);
        return new IssuedSecret(secret, expiresAt);
    }

    /**
     * 转换为视图；输出作为后续校验或处理的输入。
     *
     * @param application 应用，供本方法转换为视图时使用
     * @return 转换为后的视图结果，供调用方继续处理
     */
    private IntegrationApplicationView toView(
            IntegrationApplicationRecord application) {
        return toView(
                application,
                credentialMapper.findActive(application.getId()));
    }

    /**
     * 转换为视图；输出作为后续校验或处理的输入。
     *
     * @param application 应用，作为 {@code IntegrationApplicationView} 的输入影响后续处理
     * @param credential 凭据，供本方法转换为视图时使用
     * @return 转换为后的视图结果，供调用方继续处理
     */
    private IntegrationApplicationView toView(
            IntegrationApplicationRecord application,
            IntegrationApplicationCredentialRecord credential) {
        return new IntegrationApplicationView(
                application.getId(),
                application.getClientId(),
                application.getApplicationName(),
                application.getDescription(),
                application.getOwnerOrganizationId(),
                application.getStatus(),
                application.getRateLimitPerMinute(),
                application.getMaxConcurrency(),
                readCidrs(application.getAllowedSourceCidrs()),
                toInstant(application.getExpiresAt()),
                application.getVersion(),
                credential == null ? null : credential.getCredentialHint(),
                credential == null ? null : toInstant(
                        credential.getExpiresAt()),
                credential == null ? null : toInstant(
                        credential.getLastUsedAt()),
                toInstant(application.getCreateTime()),
                toInstant(application.getUpdateTime()));
    }

    /**
     * 校验{@code cidrs}；不满足约束时阻止后续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 集成应用集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<String> validateCidrs(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            try {
                IpNetwork.parse(normalized);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "来源 CIDR 格式不正确",
                        exception);
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    /**
     * 写入{@code cidrs}；后续读取或执行将使用更新后的状态。
     *
     * @param cidrs {@code cidrs}，作为 {@code objectMapper.writeValueAsString} 的输入影响后续处理
     * @return 写入后的{@code cidrs}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String writeCidrs(List<String> cidrs) {
        try {
            return objectMapper.writeValueAsString(cidrs);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化来源 CIDR", exception);
        }
    }

    /**
     * 读取{@code cidrs}；查询结果供调用方展示或继续处理。
     *
     * @param value 待读取{@code cidrs}的原始输入，结果供调用方继续使用
     * @return 集成应用集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private List<String> readCidrs(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("来源 CIDR 配置损坏", exception);
        }
    }

    /**
     * 校验并获取已锁定应用；不满足约束时阻止后续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 校验并获取后的已锁定应用结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private IntegrationApplicationRecord requireLockedApplication(String id) {
        IntegrationApplicationRecord application =
                applicationMapper.lockById(id);
        if (application == null) {
            throw new IllegalArgumentException("接入应用不存在");
        }
        return application;
    }

    /**
     * 校验并获取非已撤销；不满足约束时阻止后续处理。
     *
     * @param application 应用，供本方法校验并获取非已撤销时使用
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void requireNotRevoked(
            IntegrationApplicationRecord application) {
        if (ApplicationStatus.REVOKED.name().equals(application.getStatus())) {
            throw new BusinessConflictException(
                    "INTEGRATION_APPLICATION_REVOKED",
                    "接入应用已吊销");
        }
    }

    /**
     * 校验并获取预期版本；不满足约束时阻止后续处理。
     *
     * @param application 应用，供本方法校验并获取预期版本时使用
     * @param expectedVersion 预期版本，供本方法校验并获取预期版本时使用
     */
    private void requireExpectedVersion(
            IntegrationApplicationRecord application,
            Long expectedVersion) {
        if (expectedVersion == null
                || application.getVersion() == null
                || application.getVersion().longValue()
                != expectedVersion.longValue()) {
            throw versionConflict();
        }
    }

    /**
     * 处理{@code advance}应用版本，并将结果传给后续步骤。
     *
     * @param application 应用，供本方法处理{@code advance}应用版本时使用
     * @param expectedVersion 预期版本，作为 {@code application.setVersion} 的输入影响后续处理
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，作为 {@code application.setUpdateTime} 的输入影响后续处理
     */
    private void advanceApplicationVersion(
            IntegrationApplicationRecord application,
            long expectedVersion,
            String operatorId,
            LocalDateTime now) {
        if (applicationMapper.advanceVersion(
                application.getId(),
                expectedVersion,
                operatorId,
                now) != 1) {
            throw versionConflict();
        }
        application.setVersion(expectedVersion + 1);
        application.setUpdatedBy(operatorId);
        application.setUpdateTime(now);
    }

    /**
     * 构造版本冲突异常，供调用方区分失败原因。
     *
     * @return 处理后的版本冲突结果，供调用方继续处理
     */
    private BusinessConflictException versionConflict() {
        return new BusinessConflictException(
                "INTEGRATION_APPLICATION_VERSION_CONFLICT",
                "接入应用已被其他管理员修改");
    }

    /**
     * 校验并获取操作人；不满足约束时阻止后续处理。
     *
     * @return 校验并获取后的操作人结果，供调用方继续处理
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private CurrentActor requireActor() {
        CurrentActor actor = actorProvider.current();
        if (actor == null
                || actor.userId() == null
                || actor.userId().isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return actor;
    }

    /**
     * 记录审计；供后续追溯或审计使用。
     *
     * @param action 动作，写入活动历史供后续审计或展示
     * @param operation 操作标识，决定后续审计采用的处理分支
     * @param application 应用，供本方法记录审计时使用
     * @param actor 操作人，作为 {@code operatorName} 的输入影响后续处理
     * @param required 必填，供本方法记录审计时使用
     */
    private void recordAudit(
            AuditAction action,
            String operation,
            IntegrationApplicationRecord application,
            CurrentActor actor,
            boolean required) {
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(action)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(required)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("INTEGRATION_APPLICATION")
                .targetId(application.getId())
                .targetName(application.getApplicationName())
                .summary(operation)
                .createdAt(now())
                .build());
    }

    /**
     * 记录凭据审计；供后续追溯或审计使用。
     *
     * @param operation 操作标识，决定后续凭据审计采用的处理分支
     * @param application 应用，供本方法记录凭据审计时使用
     * @param credential 凭据，供本方法记录凭据审计时使用
     * @param actor 操作人，供本方法记录凭据审计时使用
     */
    private void recordCredentialAudit(
            String operation,
            IntegrationApplicationRecord application,
            IntegrationApplicationCredentialRecord credential,
            CurrentActor actor) {
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(AuditAction.DELETE)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("INTEGRATION_CREDENTIAL")
                .targetId(credential.getId())
                .targetName(application.getApplicationName())
                .summary(operation)
                .createdAt(now())
                .build());
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
     * 转换为本地日期时间；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为本地日期时间的原始输入，结果供调用方继续使用
     * @return 转换为后的本地日期时间结果，供调用方继续处理
     */
    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null
                ? null
                : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 转换为绝对时间；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为绝对时间的原始输入，结果供调用方继续使用
     * @return 转换为后的绝对时间结果，供调用方继续处理
     */
    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 定义应用状态的可选值；调用方据此选择对应的处理分支。
     */
    private enum ApplicationStatus {
        ACTIVE,
        DISABLED,
        REVOKED;

        /**
         * 解析应用状态；输出作为后续校验或处理的输入。
         *
         * @param value 待解析应用状态的原始输入，结果供调用方继续使用
         * @return 解析后的应用状态结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private static ApplicationStatus parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("接入应用状态不正确");
            }
        }
    }

    /**
     * 封装已签发密钥的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param secret 密钥，保存在对象中供后续校验、查询或展示
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    private record IssuedSecret(String secret, Instant expiresAt) {
    }
}
