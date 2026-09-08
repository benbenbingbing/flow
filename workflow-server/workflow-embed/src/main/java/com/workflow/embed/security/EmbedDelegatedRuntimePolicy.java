package com.workflow.embed.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.contracts.embed.EmbedDelegatedRuntimeApi;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerifiedTarget;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListRuntimePort.Target;
import com.workflow.contracts.embed.runtime.port.EmbedNativeProcessRuntimePort.RecordTarget;
import com.workflow.contracts.embed.runtime.port.EmbedNativeTraversalRuntimePort.TraversalTarget;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListRuntimePort;
import com.workflow.contracts.embed.runtime.port.EmbedNativeProcessRuntimePort;
import com.workflow.contracts.embed.runtime.port.EmbedNativeTraversalRuntimePort;
import com.workflow.embed.application.runtime.EmbedNativeFormTargetResolver;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Embed opaque Session 委托调用 Flow 原生运行时 API 的中央授权策略。
 *
 * <p>只有显式声明 target binding 的 Handler 才进入本策略；未声明
 * 端点作为原生 Flow 数据面，直接交给平台统一权限与 DataScope。
 * 本策略只理解稳定坐标形状，不维护 endpoint path、组件或 UI eventCode
 * 清单。</p>
 */
@Component
public class EmbedDelegatedRuntimePolicy {

    public static final String VERIFIED_ATTRIBUTE =
            EmbedDelegatedRequestContext.VERIFIED_ATTRIBUTE;
    public static final String TARGET_ATTRIBUTE =
            EmbedDelegatedRequestContext.TARGET_ATTRIBUTE;

    private final EmbedNativeFormTargetResolver targetResolver;
    private final EmbedNativeProcessRuntimePort processRuntimePort;
    private final EmbedNativeTraversalRuntimePort traversalRuntimePort;
    private final EmbedNativeListRuntimePort listRuntimePort;
    private final EmbedNativeFormRuntimePort formRuntimePort;

    @Autowired
    public EmbedDelegatedRuntimePolicy(
            EmbedNativeFormTargetResolver targetResolver,
            ObjectProvider<EmbedNativeProcessRuntimePort> processPortProvider,
            ObjectProvider<EmbedNativeTraversalRuntimePort>
                    traversalPortProvider,
            ObjectProvider<EmbedNativeListRuntimePort> listPortProvider,
            ObjectProvider<EmbedNativeFormRuntimePort> formPortProvider) {
        this.targetResolver = targetResolver;
        this.processRuntimePort = processPortProvider.getIfAvailable();
        this.traversalRuntimePort = traversalPortProvider.getIfAvailable();
        this.listRuntimePort = listPortProvider.getIfAvailable();
        this.formRuntimePort = formPortProvider.getIfAvailable();
    }

    /** 兼容旧单元测试构造；根 LIST 令牌校验会失败关闭。 */
    public EmbedDelegatedRuntimePolicy(
            EmbedNativeFormTargetResolver targetResolver,
            ObjectProvider<EmbedNativeProcessRuntimePort> processPortProvider,
            ObjectProvider<EmbedNativeTraversalRuntimePort>
                    traversalPortProvider) {
        this.targetResolver = targetResolver;
        this.processRuntimePort = processPortProvider.getIfAvailable();
        this.traversalRuntimePort = traversalPortProvider.getIfAvailable();
        this.listRuntimePort = null;
        this.formRuntimePort = null;
    }

    /** 兼容不启动 Process/Traversal 模块的轻量策略测试；相关 binding 会失败关闭。 */
    public EmbedDelegatedRuntimePolicy(
            EmbedNativeFormTargetResolver targetResolver) {
        this.targetResolver = targetResolver;
        this.processRuntimePort = null;
        this.traversalRuntimePort = null;
        this.listRuntimePort = null;
        this.formRuntimePort = null;
    }

    /**
     * 校验所有浏览器可见的固定坐标，成功时返回服务端 target
     * 供下游审计使用。
     *
     * <p>View/Grant capability 只约束 Embed 入口和宿主 Bridge；进入原生
     * Flow 数据面后，操作权限继续由 mapped user 的平台权限、对象授权和
     * DataScope 决定，不在此处再做 capability ceiling。</p>
     */
    public EmbedNativeFormTarget authorize(
            HttpServletRequest request,
            AuthenticatedEmbedSession session,
            JsonNode body,
            EmbedDelegatedRuntimeApi declaration) {
        if (declaration == null
                || declaration.targetBinding()
                == EmbedDelegatedRuntimeApi.TargetBinding.SCOPE_DEFAULT) {
            throw denied();
        }
        // Scope、binding 和 HTTP method 共同组成端点的声明式安全形状。
        // 即使开发者误把无坐标的只读 binding 标到写接口，也必须失败关闭。
        requireDeclarationContract(request, declaration);
        EmbedNativeFormTarget root = targetResolver.resolveRoot(session);

        return switch (declaration.targetBinding()) {
            case NONE -> root;
            case ROOT_ENTITY_PATH -> authorizeRootEntity(
                    request, body, root, session);
            case FORM_RELEASE_PATH_QUERY -> authorizeRuntimeRelease(
                    request, root, session);
            case FORM_ACTION_BODY -> {
                EmbedNativeFormTarget target = authorizeFormTarget(
                        request, body, root, session, text(body, "mode"));
                if (!matchesAction(body, target)) {
                    throw denied();
                }
                yield target;
            }
            case FORM_EVENT_BODY -> {
                String mode = StringUtils.hasText(text(body, "recordId"))
                        ? "VIEW" : "CREATE";
                EmbedNativeFormTarget target = authorizeFormTarget(
                        request, body, root, session, mode);
                if (!matchesEvent(request, body, target)) {
                    throw denied();
                }
                yield target;
            }
            case FORM_UNIQUE_PRECHECK -> authorizeUniquePrecheck(
                    request, body, root, session);
            case RECORD_DETAIL_PATH_QUERY -> authorizeRecordDetail(
                    request, body, root, session);
            case FORM_OWNER_BODY -> authorizeFormOwner(
                    request, body, root, session);
            case SIGNED_RUNTIME_CONTEXT -> {
                if (declaration.requiredCapability()
                        == EmbedDelegatedRuntimeApi.Capability.LIST_QUERY
                        && "LIST".equals(root.entryMode())) {
                    authorizeRootListRequest(request, body, root, session);
                } else if (!hasSignedRuntimeContext(request, body)) {
                    throw denied();
                }
                // 具体服务再次验证签名、当前用户、发布归属、目标记录及 DataScope。
                yield root;
            }
            case FILE_READ -> {
                if (!StringUtils.hasText(request.getParameter("url"))) {
                    throw denied();
                }
                yield root;
            }
            case FILE_WRITE -> {
                if (!matchesFileWrite(request, root)) {
                    throw denied();
                }
                yield root;
            }
            case PROCESS_INSTANCE_PATH -> authorizeProcessRecord(
                    request, root, session);
            case SCOPE_DEFAULT -> throw denied();
        };
    }

    /**
     * 根 LIST schema/query 必须同时匹配 Session 固定坐标和专用签名令牌。
     */
    private void authorizeRootListRequest(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        String entityCode = uriVariable(request, "entityCode");
        String listKey = uriVariable(request, "listKey");
        String releaseId = firstText(
                request.getParameter("releaseId"), text(body, "releaseId"));
        Integer releaseVersion = firstPositiveInt(
                request.getParameter("releaseVersion"),
                body == null ? null : body.get("releaseVersion"));
        String token = firstText(
                request.getParameter("releaseResolutionToken"),
                text(body, "releaseResolutionToken"));
        if (listRuntimePort == null
                || !StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listKey)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || !StringUtils.hasText(token)) {
            throw denied();
        }
        try {
            // 根列表与 open-list 派生列表使用同一验证链；
            // 后者的 token 只能由固定 List Release 中的按钮坐标
            // 派生，并继续绑定当前 Session/View Release。
            listRuntimePort.verifyReleaseResolutionToken(
                    token,
                    new Target(
                            entityCode, listKey,
                            releaseId, releaseVersion,
                            session.sessionId(), session.viewId(),
                            session.viewReleaseId(),
                            session.absoluteExpiresAt()));
        } catch (RuntimeException error) {
            throw denied();
        }
    }

    /**
     * 校验稳定的请求形状，不依赖 endpoint path，因此新增原生端点无需修改中央清单。
     */
    private static void requireDeclarationContract(
            HttpServletRequest request,
            EmbedDelegatedRuntimeApi declaration) {
        EmbedDelegatedRuntimeApi.TargetBinding binding =
                declaration.targetBinding();
        boolean get = "GET".equalsIgnoreCase(request.getMethod());
        boolean post = "POST".equalsIgnoreCase(request.getMethod());
        boolean allowed = switch (declaration.value()) {
            case ROOT_ENTITY_METADATA ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .ROOT_ENTITY_PATH && get;
            case FORM_RELEASE ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FORM_RELEASE_PATH_QUERY && get;
            case FORM_CONTEXT ->
                    (binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FORM_ACTION_BODY
                    || binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FORM_EVENT_BODY
                    || binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FORM_UNIQUE_PRECHECK) && post;
            case RECORD_DETAIL ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .RECORD_DETAIL_PATH_QUERY && post;
            case REFERENCE_READ ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding.NONE
                            && get;
            case FORM_OWNER_RUNTIME ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FORM_OWNER_BODY && post;
            case SIGNED_RUNTIME_CONTEXT ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .SIGNED_RUNTIME_CONTEXT && (get || post);
            case FILE_RUNTIME ->
                    (binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FILE_READ && get)
                    || (binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .FILE_WRITE && post);
            case PROCESS_RECORD_RUNTIME ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .PROCESS_INSTANCE_PATH && get;
        };
        if (!allowed) {
            throw denied();
        }
    }

    private EmbedNativeFormTarget authorizeRootEntity(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(
                request, body, root, session);
        EmbedNativeFormTarget target = traversal == null ? root : traversal;
        if (!Objects.equals(
                target.entityCode(), uriVariable(request, "entityCode"))) {
            throw denied();
        }
        return target;
    }

    private EmbedNativeFormTarget authorizeFormTarget(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session,
            String mode) {
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(
                request, body, root, session);
        if (traversal != null) {
            return traversal;
        }
        String formId = firstText(
                text(body, "formId"), text(body, "configId"));
        if (!StringUtils.hasText(formId)) {
            formId = uriVariable(request, "formId");
        }
        return authorizeSignedForm(
                session,
                root,
                text(body, "entityCode"),
                formId,
                text(body, "releaseId"),
                positiveInt(body == null ? null
                        : body.get("releaseVersion")),
                text(body, "releaseResolutionToken"),
                text(body, "listKey"),
                null, null, null,
                mode,
                text(body, "recordId"));
    }

    private EmbedNativeFormTarget authorizeRuntimeRelease(
            HttpServletRequest request,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        String formId = uriVariable(request, "formId");
        String releaseId = request.getParameter("releaseId");
        Integer releaseVersion = positiveInt(
                request.getParameter("version"));
        String token = request.getParameter("releaseResolutionToken");
        if (formRuntimePort == null
                || !StringUtils.hasText(formId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || !StringUtils.hasText(token)) {
            throw denied();
        }
        try {
            VerifiedTarget verified =
                    formRuntimePort.verifyRuntimeReleaseRequest(
                            token,
                            new VerificationTarget(
                                    null, formId, releaseId, releaseVersion,
                                    session.sessionId(),
                                    session.viewReleaseId(),
                                    session.absoluteExpiresAt()));
            if (verified == null
                    || !Objects.equals(formId, verified.formId())
                    || !Objects.equals(
                            releaseId, verified.formReleaseId())
                    || releaseVersion != verified.formReleaseVersion()) {
                throw denied();
            }
            return new EmbedNativeFormTarget(
                    verified.entityCode(), verified.formId(),
                    verified.formReleaseId(), verified.formReleaseVersion(),
                    null, null, null, root.entryMode(), null, null,
                    Map.of(), Map.of(), Map.of());
        } catch (EmbedException error) {
            throw error;
        } catch (RuntimeException error) {
            throw denied();
        }
    }

    /**
     * 将签名 Form Release 目标恢复为原生表单访问，并继续执行
     * mapped user 的对象权限与 DataScope。
     */
    private EmbedNativeFormTarget authorizeSignedForm(
            AuthenticatedEmbedSession session,
            EmbedNativeFormTarget root,
            String entityCode,
            String formId,
            String formReleaseId,
            Integer formReleaseVersion,
            String formReleaseToken,
            String listKey,
            String listReleaseId,
            Integer listReleaseVersion,
            String listReleaseToken,
            String mode,
            String recordId) {
        EmbedNativeFormTarget pinned = verifySignedForm(
                session, root, entityCode, formId,
                formReleaseId, formReleaseVersion, formReleaseToken,
                listKey, listReleaseId, listReleaseVersion, listReleaseToken);
        return targetResolver.authorizePinnedForm(
                session, pinned, mode, recordId);
    }

    /**
     * 仅相信 Entity 边界对签名 token 恢复的坐标；请求中的
     * entity/form/release 必须与签名值逐项一致。
     */
    private EmbedNativeFormTarget verifySignedForm(
            AuthenticatedEmbedSession session,
            EmbedNativeFormTarget root,
            String entityCode,
            String formId,
            String formReleaseId,
            Integer formReleaseVersion,
            String formReleaseToken,
            String listKey,
            String listReleaseId,
            Integer listReleaseVersion,
            String listReleaseToken) {
        if (formRuntimePort == null
                || !StringUtils.hasText(formId)
                || !StringUtils.hasText(formReleaseId)
                || formReleaseVersion == null
                || !StringUtils.hasText(formReleaseToken)) {
            throw denied();
        }
        try {
            VerificationTarget requested =
                    new VerificationTarget(
                            entityCode, formId, formReleaseId,
                            formReleaseVersion,
                            session.sessionId(),
                            session.viewReleaseId(),
                            session.absoluteExpiresAt());
            VerifiedTarget verified =
                    formRuntimePort.verifyReleaseResolutionToken(
                            formReleaseToken, requested);
            if (verified == null
                    || !Objects.equals(formId, verified.formId())
                    || !Objects.equals(
                            formReleaseId, verified.formReleaseId())
                    || formReleaseVersion
                            != verified.formReleaseVersion()
                    || StringUtils.hasText(entityCode)
                            && !Objects.equals(
                                    entityCode, verified.entityCode())) {
                throw denied();
            }
            PinnedList pinnedList = verifyPinnedList(
                    session, root, verified.entityCode(),
                    listKey, listReleaseId, listReleaseVersion,
                    listReleaseToken);
            return new EmbedNativeFormTarget(
                    verified.entityCode(), verified.formId(),
                    verified.formReleaseId(), verified.formReleaseVersion(),
                    pinnedList.listKey(), pinnedList.releaseId(),
                    pinnedList.releaseVersion(), root.entryMode(), null,
                    null, Map.of(), Map.of(), Map.of());
        } catch (EmbedException error) {
            throw error;
        } catch (RuntimeException error) {
            throw denied();
        }
    }

    /**
     * 详情请求可携带根列表或 open-list 的固定发布坐标。
     * 有 release 坐标时必须用同 Session 签名的 List token 验证；
     * 表单按钮只携带根 listKey 时可直接复用 Session 根坐标。
     */
    private PinnedList verifyPinnedList(
            AuthenticatedEmbedSession session,
            EmbedNativeFormTarget root,
            String entityCode,
            String listKey,
            String releaseId,
            Integer releaseVersion,
            String releaseToken) {
        boolean hasReleaseCoordinate = StringUtils.hasText(releaseId)
                || releaseVersion != null
                || StringUtils.hasText(releaseToken);
        if (!hasReleaseCoordinate) {
            if (Objects.equals(entityCode, root.entityCode())
                    && StringUtils.hasText(listKey)
                    && Objects.equals(listKey, root.listKey())
                    && StringUtils.hasText(root.listReleaseId())
                    && root.listReleaseVersion() != null) {
                return new PinnedList(
                        root.listKey(), root.listReleaseId(),
                        root.listReleaseVersion());
            }
            return PinnedList.NONE;
        }
        if (listRuntimePort == null
                || !StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listKey)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || !StringUtils.hasText(releaseToken)) {
            throw denied();
        }
        listRuntimePort.verifyReleaseResolutionToken(
                releaseToken,
                new Target(
                        entityCode, listKey, releaseId, releaseVersion,
                        session.sessionId(), session.viewId(),
                        session.viewReleaseId(),
                        session.absoluteExpiresAt()));
        return new PinnedList(listKey, releaseId, releaseVersion);
    }

    private static boolean matchesAction(
            JsonNode body,
            EmbedNativeFormTarget target) {
        return isObject(body)
                && textEquals(body, "formId", target.formId())
                && textEquals(body, "releaseId", target.formReleaseId())
                && intEquals(body, "releaseVersion", target.formReleaseVersion())
                && textEquals(body, "entityCode", target.entityCode())
                && Objects.equals(
                        normalizeMode(text(body, "mode")),
                        target.entryMode())
                && recordMatches(body, target)
                && StringUtils.hasText(text(body, "releaseResolutionToken"));
    }

    private EmbedNativeFormTarget authorizeUniquePrecheck(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        if (!isObject(body)
                || !StringUtils.hasText(text(body, "releaseResolutionToken"))) {
            throw denied();
        }
        String mode = StringUtils.hasText(text(body, "recordId"))
                ? "VIEW" : "CREATE";
        EmbedNativeFormTarget target = authorizeFormTarget(
                request, body, root, session, mode);
        if (!Objects.equals(uriVariable(request, "formId"), target.formId())
                || !textEquals(body, "releaseId", target.formReleaseId())
                || !intEquals(body, "releaseVersion",
                        target.formReleaseVersion())
                || !recordMatches(body, target)) {
            throw denied();
        }
        return target;
    }

    private EmbedNativeFormTarget authorizeRecordDetail(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(
                request, body, root, session);
        String recordId = uriVariable(request, "recordId");
        EmbedNativeFormTarget target = traversal == null
                ? authorizeSignedForm(
                        session,
                        root,
                        uriVariable(request, "entityCode"),
                        request.getParameter("formId"),
                        request.getParameter("formReleaseId"),
                        positiveInt(request.getParameter(
                                "formReleaseVersion")),
                        request.getParameter(
                                "formReleaseResolutionToken"),
                        request.getParameter("listKey"),
                        request.getParameter("releaseId"),
                        positiveInt(request.getParameter("releaseVersion")),
                        request.getParameter("releaseResolutionToken"),
                        "VIEW",
                        recordId)
                : traversal;
        if (!"VIEW".equals(target.entryMode())
                || !Objects.equals(target.entityCode(),
                        uriVariable(request, "entityCode"))
                || !Objects.equals(target.recordId(), recordId)
                || !coordinateEquals(request, "formId", target.formId())
                || !coordinateEquals(
                        request, "formReleaseId", target.formReleaseId())
                || !coordinateIntEquals(
                        request, "formReleaseVersion",
                        target.formReleaseVersion())
                || !coordinateEquals(request, "listKey", target.listKey())
                || !coordinateEquals(
                        request, "releaseId", target.listReleaseId())
                || !coordinateIntEquals(
                        request, "releaseVersion",
                        target.listReleaseVersion())) {
            throw denied();
        }
        return target;
    }

    private EmbedNativeFormTarget authorizeFormOwner(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        if (!isObject(body)) {
            throw denied();
        }
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(
                request, body, root, session);
        String recordId = text(body, "recordId");
        EmbedNativeFormTarget target = traversal;
        if (target == null) {
            String ownerId = text(body, "ownerId");
            String releaseId = text(body, "releaseId");
            Integer releaseVersion = positiveInt(
                    body.get("releaseVersion"));
            String token = text(body, "releaseResolutionToken");
            boolean signedTarget = StringUtils.hasText(token)
                    || StringUtils.hasText(releaseId)
                    || releaseVersion != null
                    || !Objects.equals(ownerId, root.formId());
            if (signedTarget) {
                target = verifySignedForm(
                        session, root, text(body, "entityCode"),
                        ownerId, releaseId, releaseVersion, token,
                        text(body, "listKey"), null, null, null);
                if (StringUtils.hasText(recordId)) {
                    target = targetResolver.authorizePinnedForm(
                            session, target, "VIEW", recordId);
                }
            } else {
                target = StringUtils.hasText(recordId)
                        ? targetResolver.authorize(
                                session, "VIEW", recordId)
                        : root;
            }
        }
        if (!textEqualsIgnoreCase(body, "ownerType", "FORM")
                || !textEquals(body, "ownerId", target.formId())
                || StringUtils.hasText(recordId)
                        && !Objects.equals(target.recordId(), recordId)) {
            throw denied();
        }
        String releaseId = text(body, "releaseId");
        if (StringUtils.hasText(releaseId)
                && (!Objects.equals(target.formReleaseId(), releaseId)
                || !intEquals(body, "releaseVersion",
                        target.formReleaseVersion()))) {
            throw denied();
        }
        return target;
    }

    private static boolean matchesEvent(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget target) {
        String eventCode = uriVariable(request, "eventCode");
        return safeRuntimeKey(eventCode)
                && isObject(body)
                && textEqualsIgnoreCase(body, "configType", "FORM")
                && textEquals(body, "configId", target.formId())
                && textEquals(body, "releaseId", target.formReleaseId())
                && intEquals(body, "releaseVersion", target.formReleaseVersion())
                && textEquals(body, "entityCode", target.entityCode())
                && recordMatches(body, target)
                && StringUtils.hasText(text(body, "releaseResolutionToken"));
    }

    private static boolean matchesFileWrite(
            HttpServletRequest request,
            EmbedNativeFormTarget root) {
        String contentType = request.getContentType();
        String entityCode = uriVariable(request, "entityCode");
        return contentType != null
                && contentType.toLowerCase(java.util.Locale.ROOT)
                        .startsWith("multipart/form-data")
                && StringUtils.hasText(request.getHeader("Idempotency-Key"))
                // 实体字段上传必须与 Embed Session 固定的根实体一致；
                // 通用文件入口没有 entityCode 路径变量，继续由存储权限控制。
                && (!StringUtils.hasText(entityCode)
                    || (StringUtils.hasText(root.entityCode())
                        && root.entityCode().equalsIgnoreCase(
                                entityCode.trim())));
    }

    private EmbedNativeFormTarget authorizeProcessRecord(
            HttpServletRequest request,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        String processInstanceId = uriVariable(request, "processInstanceId");
        if (processRuntimePort == null
                || !StringUtils.hasText(processInstanceId)) {
            throw denied();
        }
        RecordTarget mapped = processRuntimePort
                .findRecordTarget(processInstanceId)
                .orElseThrow(EmbedDelegatedRuntimePolicy::denied);
        if (!Objects.equals(root.entityCode(), mapped.entityCode())) {
            throw denied();
        }
        // 固定记录再次执行实体 DataScope；流程端点随后还会执行流程实例访问校验。
        return targetResolver.authorize(session, "VIEW", mapped.recordId());
    }

    /** 仅沿 Flow 服务端签发的关联内容遍历链进入非根实体。 */
    private EmbedNativeFormTarget authorizeTraversalTarget(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        String token = request.getParameter("viewCompositionTraversalToken");
        if (!StringUtils.hasText(token)) {
            token = text(body, "viewCompositionTraversalToken");
        }
        if (!StringUtils.hasText(token) && isObject(body)) {
            token = text(body.get("context"),
                    "viewCompositionTraversalToken");
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (traversalRuntimePort == null) {
            throw denied();
        }
        TraversalTarget fixed =
                traversalRuntimePort.resolve(token);
        if (!"FORM".equals(fixed.rootOwnerType())
                || !Objects.equals(root.formId(), fixed.rootOwnerId())
                || !Objects.equals(root.formReleaseId(), fixed.rootReleaseId())
                || !Objects.equals(root.formReleaseVersion(),
                        fixed.rootReleaseVersion())
                || !StringUtils.hasText(fixed.rootRecordId())) {
            throw denied();
        }
        EmbedNativeFormTarget authorizedRoot = targetResolver.authorize(
                session, "VIEW", fixed.rootRecordId());
        if (!Objects.equals(root.formId(), authorizedRoot.formId())
                || !Objects.equals(root.formReleaseId(),
                        authorizedRoot.formReleaseId())
                || !Objects.equals(root.formReleaseVersion(),
                        authorizedRoot.formReleaseVersion())) {
            throw denied();
        }
        boolean formTarget = "FORM".equals(fixed.targetOwnerType());
        boolean listTarget = "LIST".equals(fixed.targetOwnerType());
        if ((!formTarget && !listTarget)
                || !StringUtils.hasText(fixed.targetOwnerId())
                || !StringUtils.hasText(fixed.targetReleaseId())
                || fixed.targetReleaseVersion() == null
                || fixed.targetReleaseVersion() < 1
                || !StringUtils.hasText(fixed.targetEntityCode())) {
            throw denied();
        }
        return new EmbedNativeFormTarget(
                fixed.targetEntityCode(),
                formTarget ? fixed.targetOwnerId() : root.formId(),
                formTarget ? fixed.targetReleaseId() : root.formReleaseId(),
                formTarget ? fixed.targetReleaseVersion()
                        : root.formReleaseVersion(),
                null, null, null,
                formTarget ? "VIEW" : "LIST",
                formTarget ? fixed.targetRecordId() : null,
                null, Map.of(), Map.of(), Map.of());
    }

    private static boolean hasSignedRuntimeContext(
            HttpServletRequest request,
            JsonNode body) {
        return StringUtils.hasText(request.getParameter(
                        "releaseResolutionToken"))
                || StringUtils.hasText(request.getParameter(
                        "viewCompositionContextToken"))
                || StringUtils.hasText(request.getParameter(
                        "viewCompositionTraversalToken"))
                || StringUtils.hasText(text(body, "releaseResolutionToken"))
                || StringUtils.hasText(text(body,
                        "viewCompositionContextToken"))
                || StringUtils.hasText(text(body,
                        "viewCompositionTraversalToken"))
                || StringUtils.hasText(text(body, "actionContextToken"));
    }

    private static boolean recordMatches(
            JsonNode body,
            EmbedNativeFormTarget target) {
        String value = text(body, "recordId");
        return "CREATE".equals(target.entryMode())
                ? !StringUtils.hasText(value)
                : Objects.equals(target.recordId(), value);
    }

    private static boolean coordinateEquals(
            HttpServletRequest request,
            String name,
            String expected) {
        String actual = request.getParameter(name);
        return StringUtils.hasText(expected)
                ? Objects.equals(expected, actual)
                : !StringUtils.hasText(actual);
    }

    private static boolean coordinateIntEquals(
            HttpServletRequest request,
            String name,
            Integer expected) {
        String actual = request.getParameter(name);
        return expected == null
                ? !StringUtils.hasText(actual)
                : Objects.equals(expected, positiveInt(actual));
    }

    private static Integer positiveInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static Integer positiveInt(JsonNode value) {
        return value != null && value.canConvertToInt()
                && value.asInt() > 0 ? value.asInt() : null;
    }

    /**
     * 原生页面使用 edit，平台记录授权模型使用 VIEW；APPROVE 保留原值，
     * 由目标解析器先执行 VIEW/DataScope，再交给流程任务权限做最终审批授权。
     */
    private static String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String mode = value.trim().toUpperCase(java.util.Locale.ROOT);
        return "EDIT".equals(mode) ? "VIEW" : mode;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static Integer firstPositiveInt(
            String first,
            JsonNode second) {
        Integer queryValue = positiveInt(first);
        if (queryValue != null) {
            return queryValue;
        }
        return second != null && second.canConvertToInt()
                && second.asInt() > 0 ? second.asInt() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> uriVariables(
            HttpServletRequest request) {
        Object value = request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map<?, ?> map
                ? (Map<String, String>) map : Collections.emptyMap();
    }

    private static String uriVariable(
            HttpServletRequest request,
            String name) {
        return uriVariables(request).get(name);
    }

    /** 接受常见稳定代码字符，不赋予任何特定事件码额外权限。 */
    private static boolean safeRuntimeKey(String value) {
        if (!StringUtils.hasText(value) || value.length() > 100) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!(Character.isLetterOrDigit(current)
                    || current == '_' || current == '-' || current == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isObject(JsonNode body) {
        return body != null && body.isObject();
    }

    private static boolean textEquals(
            JsonNode body,
            String field,
            String expected) {
        return Objects.equals(expected, text(body, field));
    }

    private static boolean textEqualsIgnoreCase(
            JsonNode body,
            String field,
            String expected) {
        String actual = text(body, field);
        return actual != null && expected.equalsIgnoreCase(actual);
    }

    private static boolean intEquals(
            JsonNode body,
            String field,
            int expected) {
        JsonNode value = body == null ? null : body.get(field);
        return value != null && value.canConvertToInt()
                && value.asInt() == expected;
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() || !value.isTextual()
                ? null : value.asText();
    }

    private static EmbedException denied() {
        return new EmbedException(
                403,
                EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed delegated runtime operation is not allowed");
    }

    private record PinnedList(
            String listKey,
            String releaseId,
            Integer releaseVersion) {

        private static final PinnedList NONE =
                new PinnedList(null, null, null);
    }
}
