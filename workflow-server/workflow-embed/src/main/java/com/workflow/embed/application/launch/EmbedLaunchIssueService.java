package com.workflow.embed.application.launch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.launch.model.EmbedApplicationActor;
import com.workflow.contracts.embed.launch.model.EmbedLaunchCommand;
import com.workflow.contracts.embed.launch.model.EmbedLaunchEntry;
import com.workflow.contracts.embed.launch.port.EmbedLaunchIssuePort;
import com.workflow.contracts.embed.launch.model.EmbedLaunchIssued;
import com.workflow.contracts.embed.launch.model.EmbedLaunchSubject;
import com.workflow.contracts.embed.launch.model.EmbedLaunchUi;
import com.workflow.contracts.embed.launch.model.EmbedLaunchView;
import com.workflow.embed.application.port.EmbedAssertionReplayPort;
import com.workflow.embed.application.port.EmbedContextProtectionPort;
import com.workflow.embed.application.port.EmbedDigestPort;
import com.workflow.embed.application.port.EmbedExternalIdentityBindingPort;
import com.workflow.embed.application.port.EmbedFlowUserPort;
import com.workflow.embed.application.port.EmbedIdGeneratorPort;
import com.workflow.embed.application.port.EmbedLaunchConfigurationPort;
import com.workflow.embed.application.port.EmbedLaunchStorePort;
import com.workflow.embed.application.port.EmbedRuntimeSnapshotMaterializationPort;
import com.workflow.embed.application.port.EmbedSecretGeneratorPort;
import com.workflow.embed.application.port.EmbedSignedAssertionVerifierPort;
import com.workflow.embed.application.port.EmbedSubjectDigestPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.validation.EmbedOriginNormalizer;
import com.workflow.embed.application.validation.EmbedPublishedContextValidator;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.EmbedEntryMode;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import com.workflow.embed.domain.EmbedFlowUser;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedLaunchConfiguration;
import com.workflow.embed.domain.EmbedReleaseSnapshot;
import com.workflow.embed.domain.EmbedSubjectType;
import com.workflow.embed.domain.PersistedEmbedLaunch;
import com.workflow.embed.domain.ProtectedContext;
import com.workflow.embed.domain.SubjectDigest;
import com.workflow.embed.domain.VerifiedExternalSubject;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.text.Normalizer;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将可信外部身份精确映射到 Flow 用户，并签发短时、一次性的 Embed Launch。 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedLaunchIssueService implements EmbedLaunchIssuePort {

    private static final String PROTOCOL_VERSION = "flow-embed/1";
    private static final Pattern SAFE_CHANNEL = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Pattern SAFE_VIEW_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final Pattern SAFE_LOCALE = Pattern.compile("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*");
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() { };
    private static final Set<String> V1_ENTRY_MODES = Set.of(
            "LIST", "CREATE", "VIEW");
    private static final Set<String> V1_CAPABILITIES = Set.of(
            "LIST_QUERY", "SELECTION_RETURN", "RECORD_VIEW", "RECORD_CREATE",
            "ACTION_EXECUTE");

    private final EmbedLaunchConfigurationPort configurationPort;
    private final EmbedExternalIdentityBindingPort bindingPort;
    private final EmbedFlowUserPort flowUserPort;
    private final EmbedSignedAssertionVerifierPort signedAssertionVerifier;
    private final EmbedAssertionReplayPort assertionReplayPort;
    private final EmbedSubjectDigestPort subjectDigestPort;
    private final EmbedContextProtectionPort contextProtectionPort;
    private final EmbedSecretGeneratorPort secretGenerator;
    private final EmbedDigestPort digestPort;
    private final EmbedIdGeneratorPort idGenerator;
    private final EmbedLaunchStorePort launchStore;
    private final EmbedRuntimeSnapshotMaterializationPort snapshotMaterializationPort;
    private final EmbedTrafficControlPort trafficControlPort;
    private final EmbedPublishedContextValidator contextValidator;
    private final EmbedProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EmbedLifecycleAudit lifecycleAudit;

    /**
     * 初始化嵌入式启动记录签发服务，保存构造参数供后续方法使用。
     *
     * @param configurationPort 配置端口依赖，保存到当前对象供后续业务方法调用
     * @param bindingPort 绑定端口依赖，保存到当前对象供后续业务方法调用
     * @param flowUserPort 流程用户端口依赖，保存到当前对象供后续业务方法调用
     * @param signedAssertionVerifier 已签名断言验证器依赖，保存到当前对象供后续业务方法调用
     * @param assertionReplayPort 断言重放端口依赖，保存到当前对象供后续业务方法调用
     * @param subjectDigestPort 主体摘要端口依赖，保存到当前对象供后续业务方法调用
     * @param contextProtectionPort 上下文{@code protection}端口依赖，保存到当前对象供后续业务方法调用
     * @param secretGenerator 密钥生成器依赖，保存到当前对象供后续业务方法调用
     * @param digestPort 摘要端口依赖，保存到当前对象供后续业务方法调用
     * @param idGenerator ID生成器依赖，保存到当前对象供后续业务方法调用
     * @param launchStore 启动记录{@code store}依赖，保存到当前对象供后续业务方法调用
     * @param snapshotMaterializationPort 快照{@code materialization}端口依赖，保存到当前对象供后续业务方法调用
     * @param trafficControlPort {@code traffic}{@code control}端口依赖，保存到当前对象供后续业务方法调用
     * @param contextValidator 上下文校验器依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     * @param lifecycleAudit 生命周期审计依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedLaunchIssueService(
            EmbedLaunchConfigurationPort configurationPort,
            EmbedExternalIdentityBindingPort bindingPort,
            EmbedFlowUserPort flowUserPort,
            EmbedSignedAssertionVerifierPort signedAssertionVerifier,
            EmbedAssertionReplayPort assertionReplayPort,
            EmbedSubjectDigestPort subjectDigestPort,
            EmbedContextProtectionPort contextProtectionPort,
            EmbedSecretGeneratorPort secretGenerator,
            EmbedDigestPort digestPort,
            EmbedIdGeneratorPort idGenerator,
            EmbedLaunchStorePort launchStore,
            EmbedRuntimeSnapshotMaterializationPort snapshotMaterializationPort,
            EmbedTrafficControlPort trafficControlPort,
            EmbedPublishedContextValidator contextValidator,
            EmbedProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("embedClock") Clock clock,
            EmbedLifecycleAudit lifecycleAudit) {
        this.configurationPort = configurationPort;
        this.bindingPort = bindingPort;
        this.flowUserPort = flowUserPort;
        this.signedAssertionVerifier = signedAssertionVerifier;
        this.assertionReplayPort = assertionReplayPort;
        this.subjectDigestPort = subjectDigestPort;
        this.contextProtectionPort = contextProtectionPort;
        this.secretGenerator = secretGenerator;
        this.digestPort = digestPort;
        this.idGenerator = idGenerator;
        this.launchStore = launchStore;
        this.snapshotMaterializationPort = snapshotMaterializationPort;
        this.trafficControlPort = trafficControlPort;
        this.contextValidator = contextValidator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.lifecycleAudit = lifecycleAudit;
    }

    /**
     * 按 fail-closed 顺序完成 Launch 校验与签发。
     *
     * <p>外部 subject、上下文、身份断言及明文 launch code 均不会落库；任何配置缺失、版本失效或
     * 映射不唯一的情况都会拒绝请求。
     *
     * @param actor 操作人，作为 {@code issueInternal} 的输入影响后续处理
     * @param command 本次命令，后续经校验后用于处理签发
     * @return 处理后的签发结果，供调用方继续处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbedLaunchIssued issue(
            EmbedApplicationActor actor,
            EmbedLaunchCommand command) {
        EmbedAuditCorrelation correlation = actor == null
                ? EmbedAuditCorrelation.none()
                : EmbedAuditCorrelation.of(actor.traceId(), actor.requestId());
        try {
            EmbedLaunchIssued issued = issueInternal(actor, command, correlation);
            lifecycleAudit.launchIssued(issued.launchId(), correlation);
            return issued;
        } catch (EmbedException error) {
            lifecycleAudit.launchRejected(error.getErrorCode(), correlation);
            throw error;
        } catch (RuntimeException error) {
            lifecycleAudit.launchRejected(null, correlation);
            throw error;
        }
    }

    /**
     * 处理签发内部，并将结果传给后续步骤。
     *
     * @param actor 操作人，作为 {@code configurationPort.find} 的输入影响后续处理
     * @param command 本次命令，后续经校验后用于处理签发内部
     * @param correlation 关联，供本方法处理签发内部时使用
     * @return 处理后的签发内部结果，供调用方继续处理
     */
    private EmbedLaunchIssued issueInternal(
            EmbedApplicationActor actor,
            EmbedLaunchCommand command,
            EmbedAuditCorrelation correlation) {
        if (actor == null || command == null) {
            throw invalid("Launch request is required");
        }
        Instant now = clock.instant();
        validateTopLevel(command);
        String parentOrigin = EmbedOriginNormalizer.normalize(command.parentOrigin());
        EmbedLaunchConfiguration preflight = configurationPort.find(
                        actor.applicationId(), command.viewKey(), now)
                .orElseThrow(() -> new EmbedException(
                        403,
                        EmbedErrorCode.EMBED_VIEW_NOT_GRANTED,
                        "Embed view is not granted to this application"));
        validateConfiguration(preflight, actor, parentOrigin, now);
        // 任何内部 Runtime Snapshot 写入都必须先受 Grant Launch 配额约束，避免合法
        // Application/Origin 使用无效身份请求放大快照解析和数据库写入成本。预读不加锁，
        // 因此 REQUIRES_NEW 限流事务不会等待被挂起的外层事务自己持有的行锁。
        trafficControlPort.consumeLaunch(actor.applicationId(), preflight.grant().id());

        // 扣减配额后在签发事务内重新加锁读取，所有安全状态和 Origin 都以最新值为准。
        EmbedLaunchConfiguration configuration = configurationPort.lockForUpdate(
                        actor.applicationId(), command.viewKey(), now)
                .orElseThrow(() -> new EmbedException(
                        403,
                        EmbedErrorCode.EMBED_VIEW_NOT_GRANTED,
                        "Embed view is not granted to this application"));
        validateConfiguration(configuration, actor, parentOrigin, now);
        if (!preflight.grant().id().equals(configuration.grant().id())) {
            // 两次读取之间如果 Grant 被替换，不能把旧 Grant 配额充当新 Grant 配额。
            throw new EmbedException(
                    403,
                    EmbedErrorCode.EMBED_VIEW_DISABLED,
                    "Embed configuration changed; retry the request");
        }
        // 每个新 Launch 都在 View 行锁内解析 Flow 当前 ACTIVE 资源并物化内部快照。
        // 该快照不会更新 published_release_id；后续 Session 只固定本次结果。
        EmbedReleaseSnapshot runtimeSnapshot = snapshotMaterializationPort.materialize(
                configuration.view().id(),
                configuration.view().surfaceType(),
                configuration.currentConfigJson(),
                actor.applicationId(),
                now);
        validateRuntimeSnapshot(configuration, runtimeSnapshot);
        // 入口模式只依赖本次 Launch 的不可变 Runtime Snapshot；物化后立即校验，避免无效入口
        // 继续触发外部身份、JWKS 或用户映射工作。Launch 配额已在物化之前消费。
        EntrySelection entry = validateEntry(runtimeSnapshot, command.entry());

        ResolvedSubject externalSubject = resolveSubject(
                configuration, command.subject(), now);
        java.util.List<SubjectDigest> acceptedDigests = subjectDigestPort.accepted(
                actor.applicationId(), configuration.grant().identityProvider().id(),
                externalSubject.namespace(), externalSubject.externalSubject());
        ResolvedBinding resolvedBinding = acceptedDigests.stream()
                .map(digest -> bindingPort.find(
                                actor.applicationId(),
                                configuration.grant().identityProvider().id(),
                                digest.value(), digest.keyVersion(), now)
                        // Port 实现也属于安全边界：即使适配器误返回了其他租户或其他 Provider 的行，
                        // 应用层仍必须按四元组进行恒等校验，不能只相信查询条件。
                        .filter(value -> value.applicationId().equals(actor.applicationId())
                                && value.identityProviderId().equals(
                                        configuration.grant().identityProvider().id())
                                && value.subjectDigest().equals(digest.value())
                                && value.subjectDigestKeyVersion().equals(digest.keyVersion())
                                && value.isActiveAt(now))
                        .map(binding -> new ResolvedBinding(binding, digest))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new EmbedException(
                        403,
                        EmbedErrorCode.EXTERNAL_IDENTITY_NOT_MAPPED,
                        "External identity is not mapped"));
        EmbedExternalIdentityBinding binding = resolvedBinding.binding();
        SubjectDigest subjectDigest = resolvedBinding.digest();
        EmbedFlowUser user = flowUserPort.findById(binding.flowUserId())
                .filter(value -> value.id().equals(binding.flowUserId())
                        && value.mayUseEmbed())
                .orElseThrow(() -> new EmbedException(
                        403,
                        EmbedErrorCode.FLOW_USER_DISABLED,
                        "Mapped Flow user is unavailable"));

        Map<String, Object> context = immutableContext(command.context());
        contextValidator.validate(context, runtimeSnapshot.contextSchemaJson());
        UiSelection ui = validateUi(runtimeSnapshot.uiConfigJson(), command.ui());

        String launchId = idGenerator.nextLaunchId();
        String launchCode = secretGenerator.generate(properties.getSecretBytes());
        ProtectedContext protectedContext = contextProtectionPort.protectLaunch(
                actor.applicationId(), launchId, context);
        Instant expiresAt = now.plusSeconds(properties.getLaunchTtlSeconds());
        PersistedEmbedLaunch persisted = new PersistedEmbedLaunch(
                launchId,
                actor.applicationId(),
                configuration.grant().id(),
                configuration.view().id(),
                runtimeSnapshot.id(),
                configuration.grant().identityProvider().id(),
                configuration.grant().identityProvider().securityVersion(),
                configuration.application().version(),
                configuration.grant().securityVersion(),
                configuration.view().securityVersion(),
                user.id(),
                binding.id(),
                binding.bindingVersion(),
                subjectDigest.value(),
                subjectDigest.keyVersion(),
                parentOrigin,
                command.channelId(),
                entry.mode().name(),
                entry.recordId(),
                protectedContext,
                ui.locale(),
                ui.theme(),
                ui.formPresentation(),
                digestPort.sha256(launchCode),
                expiresAt,
                correlation.traceId(),
                correlation.requestId(),
                now);
        launchStore.insert(persisted);

        return new EmbedLaunchIssued(
                launchId,
                publicBaseUrl() + "/embed/v1/launches/" + launchId,
                launchCode,
                expiresAt,
                new EmbedLaunchView(
                        configuration.view().viewKey(),
                        configuration.view().surfaceType()),
                PROTOCOL_VERSION);
    }

    /**
     * 校验{@code top}层级；不满足约束时阻止后续处理。
     *
     * @param command 本次命令，后续经校验后用于校验{@code top}层级
     */
    private void validateTopLevel(EmbedLaunchCommand command) {
        if (!SAFE_VIEW_KEY.matcher(command.viewKey()).matches()) {
            throw invalid("viewKey is invalid");
        }
        if (!SAFE_CHANNEL.matcher(command.channelId()).matches()) {
            throw invalid("channelId is invalid");
        }
        if (command.subject() == null || command.entry() == null) {
            throw invalid("subject and entry are required");
        }
    }

    /**
     * 校验配置；不满足约束时阻止后续处理。
     *
     * @param configuration 配置内容，决定后续配置的处理规则
     * @param actor 操作人，供本方法校验配置时使用
     * @param parentOrigin 父级来源，供本方法校验配置时使用
     * @param now 当前时间，供本方法校验配置时使用
     */
    private void validateConfiguration(
            EmbedLaunchConfiguration configuration,
            EmbedApplicationActor actor,
            String parentOrigin,
            Instant now) {
        if (!configuration.application().id().equals(actor.applicationId())
                || !configuration.grant().applicationId().equals(actor.applicationId())
                || !configuration.grant().viewId().equals(configuration.view().id())) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_VIEW_NOT_GRANTED,
                    "Embed view is not granted to this application");
        }
        if (!configuration.application().isActiveAt(now)
                || !configuration.view().isActive()
                || !configuration.grant().isActiveAt(now)
                || !configuration.grant().identityProvider().isActive()) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_VIEW_DISABLED,
                    "Embed configuration is unavailable");
        }
        boolean originAllowed = configuration.grant().allowedOrigins().stream()
                .map(EmbedOriginNormalizer::normalize)
                .anyMatch(parentOrigin::equals);
        if (!originAllowed) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_ORIGIN_NOT_ALLOWED,
                    "Parent origin is not allowed");
        }
    }

    /**
     * 复验内部物化器输出，防止异常适配器把未开放能力带入 Session。
     *
     * @param configuration 配置内容，决定后续运行时快照的处理规则
     * @param runtimeSnapshot 运行时快照，作为 {@code parseStringSet} 的输入影响后续处理
     */
    private void validateRuntimeSnapshot(
            EmbedLaunchConfiguration configuration,
            EmbedReleaseSnapshot runtimeSnapshot) {
        if (runtimeSnapshot == null
                || !configuration.view().surfaceType().equals(runtimeSnapshot.surfaceType())) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_VIEW_DISABLED,
                    "Embed configuration is unavailable");
        }
        Set<String> entryModes = parseStringSet(
                runtimeSnapshot.entryModesJson());
        Set<String> capabilities = parseStringSet(
                runtimeSnapshot.capabilitiesJson());
        if (entryModes.isEmpty()
                || !V1_ENTRY_MODES.containsAll(entryModes)
                || !V1_CAPABILITIES.containsAll(capabilities)) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_VIEW_DISABLED,
                    "Embed configuration is unavailable");
        }
    }

    /**
     * 解析主体；输出作为后续校验或处理的输入。
     *
     * @param configuration 配置内容，决定后续主体的处理规则
     * @param request 本次请求，后续经校验后用于解析主体
     * @param now 当前时间，作为 {@code signedAssertionVerifier.verify} 的输入影响后续处理
     * @return 解析后的主体结果，供调用方继续处理
     */
    private ResolvedSubject resolveSubject(
            EmbedLaunchConfiguration configuration,
            EmbedLaunchSubject request,
            Instant now) {
        EmbedSubjectType requestedType;
        try {
            requestedType = EmbedSubjectType.valueOf(request.type().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("subject.type is invalid");
        }
        EmbedIdentityProviderSnapshot provider = configuration.grant().identityProvider();
        if (!provider.type().equals(requestedType.name())) {
            throw assertionInvalid();
        }
        if (requestedType == EmbedSubjectType.TRUSTED_EXTERNAL_ID) {
            if (!configuration.grant().trustedSubjectAssertion()) {
                throw assertionInvalid();
            }
            String namespace = requiredTrimmed(request.namespace(), 128, "subject.namespace");
            String subject = normalizedExternalSubject(
                    request.externalUserId(), "subject.externalUserId");
            if (!namespace.equals(provider.subjectNamespace())
                    || (request.assertion() != null && !request.assertion().isBlank())) {
                throw assertionInvalid();
            }
            return new ResolvedSubject(namespace, subject);
        }

        String assertion = requiredTrimmed(request.assertion(), 16 * 1024, "subject.assertion");
        if (request.namespace() != null || request.externalUserId() != null) {
            throw assertionInvalid();
        }
        VerifiedExternalSubject verified = signedAssertionVerifier.verify(provider, assertion, now);
        if (verified == null
                || !provider.subjectNamespace().equals(verified.namespace())
                || (provider.issuer() != null && !provider.issuer().equals(verified.issuer()))
                || verified.jti() == null || verified.jti().isBlank()
                || verified.replayExpiresAt() == null || !verified.replayExpiresAt().isAfter(now)) {
            throw assertionInvalid();
        }
        String replayDigest = digestPort.sha256(
                "embed-assertion-v1|" + provider.id() + "|" + verified.issuer()
                        + "|" + verified.jti());
        if (!assertionReplayPort.claim(
                provider.id(), replayDigest, verified.replayExpiresAt(), now)) {
            throw new EmbedException(
                    403,
                    EmbedErrorCode.EMBED_IDENTITY_ASSERTION_REPLAYED,
                    "Identity assertion has already been used");
        }
        return new ResolvedSubject(
                requiredTrimmed(verified.namespace(), 128, "verified namespace"),
                normalizedExternalSubject(verified.externalSubject(), "verified subject"));
    }

    /**
     * 校验入口；不满足约束时阻止后续处理。
     *
     * @param runtimeSnapshot 运行时快照，供本方法校验入口时使用
     * @param requested 请求，作为 {@code EmbedEntryMode.valueOf} 的输入影响后续处理
     * @return 校验后的入口结果，供调用方继续处理
     */
    private EntrySelection validateEntry(
            EmbedReleaseSnapshot runtimeSnapshot,
            EmbedLaunchEntry requested) {
        EmbedEntryMode mode;
        try {
            mode = EmbedEntryMode.valueOf(requested.mode().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("entry.mode is invalid");
        }
        if (!V1_ENTRY_MODES.contains(mode.name())
                || !parseStringSet(runtimeSnapshot.entryModesJson())
                        .contains(mode.name())) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                    "Entry mode is not published");
        }
        String recordId = requested.recordId() == null ? null : requested.recordId().trim();
        if (mode == EmbedEntryMode.VIEW
                && (recordId == null || recordId.isBlank() || recordId.length() > 64)) {
            throw invalid("entry.recordId is required for VIEW");
        }
        if ((mode == EmbedEntryMode.LIST || mode == EmbedEntryMode.CREATE)
                && recordId != null && !recordId.isBlank()) {
            throw invalid("entry.recordId is not allowed for LIST or CREATE");
        }
        return new EntrySelection(mode, recordId == null || recordId.isBlank() ? null : recordId);
    }

    /**
     * 校验界面；不满足约束时阻止后续处理。
     *
     * @param uiConfigJson 界面配置JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param requested 请求，供本方法校验界面时使用
     * @return 校验后的界面结果，供调用方继续处理
     */
    private UiSelection validateUi(String uiConfigJson, EmbedLaunchUi requested) {
        String locale = requested == null || requested.locale() == null
                || requested.locale().isBlank() ? "zh-CN" : requested.locale().trim();
        String theme = requested == null || requested.theme() == null
                || requested.theme().isBlank() ? "light" : requested.theme().trim().toLowerCase(Locale.ROOT);
        String formPresentation = requested == null || requested.formPresentation() == null
                ? "seamless" : requested.formPresentation();
        if (locale.length() > 35 || !SAFE_LOCALE.matcher(locale).matches()
                || !Set.of("light", "dark", "system").contains(theme)
                || !Set.of("seamless", "dialog").contains(formPresentation)) {
            throw invalid("ui locale, theme or formPresentation is invalid");
        }
        try {
            JsonNode uiConfig = objectMapper.readTree(uiConfigJson == null ? "{}" : uiConfigJson);
            JsonNode locales = uiConfig.path("allowedLocales");
            JsonNode themes = uiConfig.path("allowedThemes");
            if (locales.isArray() && !containsText(locales, locale)) {
                throw invalid("ui.locale is not published");
            }
            if (themes.isArray() && !containsText(themes, theme)) {
                throw invalid("ui.theme is not published");
            }
        } catch (JsonProcessingException error) {
            throw new EmbedException(503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Published Embed UI configuration is invalid");
        }
        return new UiSelection(locale, theme, formPresentation);
    }

    /**
     * 解析字符串设置；输出作为后续校验或处理的输入。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 嵌入式启动记录签发集合，供调用方遍历或展示
     */
    private Set<String> parseStringSet(String json) {
        try {
            Set<String> parsed = objectMapper.readValue(json, STRING_SET);
            return parsed == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(parsed));
        } catch (JsonProcessingException error) {
            throw new EmbedException(503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Published Embed configuration is invalid");
        }
    }

    /**
     * 判断是否包含文本；判断结果决定调用方的后续分支。
     *
     * @param array 数组，供本方法判断是否包含文本时使用
     * @param expected 预期，供本方法判断是否包含文本时使用
     * @return 文本条件成立时为 true，否则为 false
     */
    private static boolean containsText(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 整理不可变上下文数据，供调用方遍历或继续处理。
     *
     * @param context 执行上下文，向后续不可变上下文步骤传递身份、配置或状态
     * @return 不可变上下文键值结果，供调用方继续处理
     */
    private static Map<String, Object> immutableContext(Map<String, Object> context) {
        return context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /**
     * 生成公开基础URL文本，供后续匹配或展示。
     *
     * @return 处理后的公开基础URL文本，供调用方比较或展示
     */
    private String publicBaseUrl() {
        String value = properties.getPublicBaseUrl().trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 生成必填{@code trimmed}文本，供后续匹配或展示。
     *
     * @param value 待处理必填{@code trimmed}的原始输入，结果供调用方继续使用
     * @param maxLength 最大长度，供本方法处理必填{@code trimmed}时使用
     * @param field 字段，作为 {@code invalid} 的输入影响后续处理
     * @return 处理后的必填{@code trimmed}文本，供调用方比较或展示
     */
    private static String requiredTrimmed(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        return value.trim();
    }

    /**
     * 生成规范化外部主体文本，供后续匹配或展示。
     *
     * @param value 待处理规范化外部主体的原始输入，结果供调用方继续使用
     * @param field 字段，作为 {@code requiredTrimmed} 的输入影响后续处理
     * @return 处理后的规范化外部主体文本，供调用方比较或展示
     */
    private static String normalizedExternalSubject(String value, String field) {
        String trimmed = requiredTrimmed(value, 128, field);
        return Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，作为 {@code EmbedException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static EmbedException invalid(String message) {
        return new EmbedException(400, EmbedErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 构造断言无效异常，供调用方区分失败原因。
     *
     * @return 处理后的断言无效结果，供调用方继续处理
     */
    private static EmbedException assertionInvalid() {
        return new EmbedException(403, EmbedErrorCode.EMBED_IDENTITY_ASSERTION_INVALID,
                "Identity assertion is invalid");
    }

    /**
     * 封装已解析主体的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param namespace 命名空间，保存在对象中供后续校验、查询或展示
     * @param externalSubject 外部主体，保存在对象中供后续校验、查询或展示
     */
    private record ResolvedSubject(String namespace, String externalSubject) {
    }

    /**
     * 封装已解析绑定的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param binding 绑定，保存在对象中供后续校验、查询或展示
     * @param digest 摘要，保存在对象中供后续校验、查询或展示
     */
    private record ResolvedBinding(
            EmbedExternalIdentityBinding binding,
            SubjectDigest digest) {
    }

    /**
     * 封装入口选择的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param mode 模式标识，决定后续入口选择采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    private record EntrySelection(EmbedEntryMode mode, String recordId) {
    }

    /**
     * 封装界面选择的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param locale {@code locale}，保存在对象中供后续校验、查询或展示
     * @param theme {@code theme}，保存在对象中供后续校验、查询或展示
     * @param formPresentation 表单展示，保存在对象中供后续校验、查询或展示
     */
    private record UiSelection(String locale, String theme, String formPresentation) {
    }
}
