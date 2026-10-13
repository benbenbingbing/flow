package com.workflow.embed.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.contracts.embed.runtime.context.EmbedDelegatedRequestContext;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;
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

    /**
     * 初始化嵌入式委托运行时策略，保存构造参数供后续方法使用。
     *
     * @param targetResolver 目标解析器依赖，保存到当前对象供后续业务方法调用
     * @param processPortProvider 流程端口提供者，保存在对象中供后续校验、查询或展示
     * @param traversalPortProvider 遍历端口提供者，保存在对象中供后续校验、查询或展示
     * @param listPortProvider 列表端口提供者，保存在对象中供后续校验、查询或展示
     * @param formPortProvider 表单端口提供者，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 兼容旧单元测试构造；根 LIST 上下文校验会失败关闭。
     *
     * @param targetResolver 目标解析器依赖，保存到当前对象供后续业务方法调用
     * @param processPortProvider 流程端口提供者，保存在对象中供后续校验、查询或展示
     * @param traversalPortProvider 遍历端口提供者，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 兼容不启动 Process/Traversal 模块的轻量策略测试；相关 binding 会失败关闭。
     *
     * @param targetResolver 目标解析器依赖，保存到当前对象供后续业务方法调用
     */
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
     *
     * @param request 本次请求，后续经校验后用于处理授权
     * @param session 会话，作为 {@code targetResolver.resolveRoot} 的输入影响后续处理
     * @param body 请求体，后续用于处理授权并传递处理结果
     * @param declaration {@code declaration}，作为 {@code requireDeclarationContract} 的输入影响后续处理
     * @return 处理后的授权结果，供调用方继续处理
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
            case RUNTIME_CONTEXT -> {
                EmbedNativeFormTarget related = authorizeTraversalTarget(request, body, root, session);
                if (related != null) yield related;
                if (declaration.requiredCapability() == EmbedDelegatedRuntimeApi.Capability.LIST_QUERY) {
                    if ("LIST".equals(root.entryMode())) {
                        authorizeRootListRequest(request, body, root, session);
                        yield root;
                    }
                    EmbedNativeFormTarget childList = authorizeReferencedList(request, body, root, session);
                    if (childList != null) yield childList;
                }
                throw denied();
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

    /** 列表目标只能来自认证会话的发布闭包；请求不能选择发布版本。 */
    private void authorizeRootListRequest(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        String entityCode = firstText(uriVariable(request,"entityCode"), text(body,"entityCode"));
        String listKey = firstText(uriVariable(request,"listKey"), text(body,"listKey"));
        authorizeList(root, session, entityCode, listKey);
    }

    private Target authorizeList(EmbedNativeFormTarget root, AuthenticatedEmbedSession session, String entityCode, String listKey) {
        if (listRuntimePort == null || !StringUtils.hasText(root.listKey()) || root.listReleaseVersion() == null) throw denied();
        Target target = listRuntimePort.authorizeTarget(new Target(root.entityCode(), root.listKey(), root.listReleaseId(),root.listReleaseVersion(),
                session.sessionId(),session.viewId(),session.viewReleaseId(),session.absoluteExpiresAt()), entityCode, listKey);
        if (target == null) throw denied();
        EmbedDelegatedRequestContext.authorizeListTarget(target.entityCode(),target.listKey(),target.listReleaseId(),target.listReleaseVersion());
        return target;
    }


    /**
     * 校验稳定的请求形状，不依赖 endpoint path，因此新增原生端点无需修改中央清单。
     *
     * @param request 本次请求，后续经校验后用于校验并获取{@code declaration}契约
     * @param declaration {@code declaration}，供本方法校验并获取{@code declaration}契约时使用
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
            case RUNTIME_CONTEXT ->
                    binding == EmbedDelegatedRuntimeApi.TargetBinding
                            .RUNTIME_CONTEXT && (get || post);
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

    /**
     * 处理授权根实体，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理授权根实体
     * @param body 请求体，后续用于处理授权根实体并传递处理结果
     * @param root 根，作为 {@code authorizeTraversalTarget} 的输入影响后续处理
     * @param session 会话，作为 {@code authorizeTraversalTarget} 的输入影响后续处理
     * @return 处理后的授权根实体结果，供调用方继续处理
     */
    private EmbedNativeFormTarget authorizeRootEntity(
            HttpServletRequest request,
            JsonNode body,
            EmbedNativeFormTarget root,
            AuthenticatedEmbedSession session) {
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(
                request, body, root, session);
        EmbedNativeFormTarget target = traversal == null ? root : traversal;
        String entityCode = uriVariable(request, "entityCode");
        if (traversal == null && !Objects.equals(root.entityCode(), entityCode)) {
            Map<String, Object> context = runtimeContext(request, body);
            if ("LIST".equals(root.entryMode()) && StringUtils.hasText(string(context.get("listKey")))) {
                Target list = authorizeList(root, session, entityCode, string(context.get("listKey")));
                target = listTarget(root, list.entityCode(), list.listKey(), list.listReleaseId(), list.listReleaseVersion());
            } else {
                target = authorizeReferencedList(request, body, root, session);
            }
        }
        if (target == null || !Objects.equals(target.entityCode(), entityCode)) throw denied();
        return target;
    }

    /** 表单内子列表必须在当前根表单的已发布路径上，且根记录仍满足 Embed 固定数据范围。 */
    private EmbedNativeFormTarget authorizeReferencedList(HttpServletRequest request, JsonNode body,
            EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        Map<String, Object> context = runtimeContext(request, body);
        if (!(context.get("componentPath") instanceof java.util.List<?> path) || path.isEmpty()) return null;
        if (listRuntimePort == null || !StringUtils.hasText(root.formId())) throw denied();
        String entityCode = firstText(uriVariable(request, "entityCode"), text(body, "entityCode"));
        String listKey = firstText(firstText(uriVariable(request, "listKey"), text(body, "listKey")), string(context.get("listKey")));
        if (!StringUtils.hasText(entityCode) || !StringUtils.hasText(listKey)) throw denied();
        String rootRecordId = string(context.get("rootRecordId"));
        targetResolver.authorize(session, StringUtils.hasText(rootRecordId) ? "VIEW" : root.entryMode(), rootRecordId);
        var list = listRuntimePort.authorizeFormList(root.formId(), entityCode, listKey, context);
        if (list == null || !Objects.equals(entityCode, list.entityCode()) || !Objects.equals(listKey, list.listKey())) throw denied();
        EmbedDelegatedRequestContext.authorizeListTarget(list.entityCode(), list.listKey(), list.releaseId(), list.releaseVersion());
        return listTarget(root, list.entityCode(), list.listKey(), list.releaseId(), list.releaseVersion());
    }

    /** 子列表审计坐标由授权端口恢复，不能用根实体替代真正读取的目标。 */
    private static EmbedNativeFormTarget listTarget(EmbedNativeFormTarget root, String entityCode, String listKey,
            String releaseId, int releaseVersion) {
        return new EmbedNativeFormTarget(entityCode, root.formId(), root.formReleaseId(), root.formReleaseVersion(),
                listKey, releaseId, releaseVersion, "LIST", null, null, Map.of(), Map.of(), Map.of());
    }

    private EmbedNativeFormTarget authorizeFormTarget(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget root, AuthenticatedEmbedSession session, String mode) {
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(request,body,root,session);
        if (traversal != null) return traversal;
        String formId = firstText(firstText(text(body,"formId"), text(body,"configId")),uriVariable(request,"formId"));
        return authorizeBusinessForm(session,root,formId,runtimeContext(request,body),mode,text(body,"recordId"));
    }


    private EmbedNativeFormTarget authorizeRuntimeRelease(HttpServletRequest request, EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        String formId = uriVariable(request,"formId");
        Map<String,Object> context = runtimeContext(request,null);
        EmbedNativeFormTarget traversal = authorizeTraversalTarget(request,null,root,session);
        if (traversal != null) {
            if (!Objects.equals(formId,traversal.formId())) throw denied();
            return traversal;
        }
        return configurationForm(session,root,formId,context);
    }


    /** 根来自认证会话；子表单由发布组件路径或服务端列表按钮决定。 */
    private EmbedNativeFormTarget authorizeBusinessForm(AuthenticatedEmbedSession session, EmbedNativeFormTarget root, String formId,
            Map<String,Object> runtimeContext, String mode, String recordId) {
        EmbedNativeFormTarget configured = configurationForm(session,root,formId,runtimeContext);
        return targetResolver.authorizePinnedForm(session,configured,mode,recordId);
    }

    private EmbedNativeFormTarget configurationForm(AuthenticatedEmbedSession session, EmbedNativeFormTarget root, String formId, Map<String,Object> context) {
        if (formRuntimePort == null || !StringUtils.hasText(formId)) throw denied();
        String rootFormId = root.formId();
        String listKey = null, listReleaseId = null; Integer listVersion = null;
        if ("LIST".equals(root.entryMode())) {
            String entity = firstText(string(context.get("entityCode")),root.entityCode());
            listKey = firstText(string(context.get("listKey")),root.listKey());
            Target list = authorizeList(root,session,entity,listKey);
            rootFormId = firstText(string(context.get("rootFormId")),formId);
            listRuntimePort.authorizeForm(new Target(root.entityCode(),root.listKey(),root.listReleaseId(),root.listReleaseVersion(),
                    session.sessionId(),session.viewId(),session.viewReleaseId(),session.absoluteExpiresAt()),entity,listKey,rootFormId,context);
            listReleaseId=list.listReleaseId();listVersion=list.listReleaseVersion();
        } else if (!Objects.equals(rootFormId, formId)) {
            // 子表单可跨实体，但不能借组件路径把固定父记录替换为另一个可访问记录。
            // 后续实体边界还会根据这个父记录验证实际父子关系。
            String rootRecordId = firstText(string(context.get("rootRecordId")), root.recordId());
            targetResolver.authorize(session, StringUtils.hasText(rootRecordId) ? "VIEW" : root.entryMode(), rootRecordId);
        }
        VerifiedTarget verified = formRuntimePort.authorizeTarget(rootFormId,formId,context);
        if (verified == null || !Objects.equals(formId,verified.formId())) throw denied();
        return new EmbedNativeFormTarget(verified.entityCode(),formId,verified.formReleaseId(),verified.formReleaseVersion(),
                listKey,listReleaseId,listVersion,root.entryMode(),root.recordId(),root.processInstanceId(),root.initialData(),root.parameters(),root.context());
    }








    private static boolean matchesAction(JsonNode body, EmbedNativeFormTarget target) {
        return isObject(body) && textEquals(body,"formId",target.formId()) && textEquals(body,"entityCode",target.entityCode())
                && Objects.equals(normalizeMode(text(body,"mode")),target.entryMode()) && recordMatches(body,target);
    }


    private EmbedNativeFormTarget authorizeUniquePrecheck(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        if (!isObject(body)) throw denied();
        EmbedNativeFormTarget target=authorizeFormTarget(request,body,root,session,StringUtils.hasText(text(body,"recordId"))?"VIEW":"CREATE");
        if (!Objects.equals(uriVariable(request,"formId"),target.formId()) || !recordMatches(body,target)) throw denied();
        return target;
    }


    private EmbedNativeFormTarget authorizeRecordDetail(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        String recordId=uriVariable(request,"recordId");
        EmbedNativeFormTarget target=authorizeTraversalTarget(request,body,root,session);
        if (target==null) target=authorizeBusinessForm(session,root,request.getParameter("formId"),runtimeContext(request,body),"VIEW",recordId);
        if (!"VIEW".equals(target.entryMode()) || !Objects.equals(target.entityCode(),uriVariable(request,"entityCode"))
                || !Objects.equals(target.recordId(),recordId) || !Objects.equals(target.formId(),request.getParameter("formId"))) throw denied();
        return target;
    }


    private EmbedNativeFormTarget authorizeFormOwner(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        if (!isObject(body)) throw denied();
        String ownerType=text(body,"ownerType"), ownerId=text(body,"ownerId"),recordId=text(body,"recordId");
        EmbedNativeFormTarget parent=authorizeTraversalTarget(request,body,root,session);
        if (parent != null) {
            if ("FORM".equals(ownerType) && Objects.equals(ownerId,parent.formId()) && Objects.equals(recordId,parent.recordId())) return parent;
            if ("LIST".equals(ownerType) && "LIST".equals(parent.entryMode())) return parent;
            throw denied();
        }
        if ("LIST".equals(ownerType) && "LIST".equals(root.entryMode())) {
            Target list=authorizeList(root,session,root.entityCode(),root.listKey());
            listRuntimePort.requireListOwner(list,ownerId);
            // 默认表单的行入口复用 Embed 固定参数过滤，后续关联解析还会验证宿主列表范围。
            targetResolver.authorize(session,"VIEW",recordId);
            return root;
        }
        if (!"FORM".equals(ownerType)) throw denied();
        return authorizeBusinessForm(session,root,ownerId,runtimeContext(request,body),StringUtils.hasText(recordId)?"VIEW":"CREATE",recordId);
    }


    private static boolean matchesEvent(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget target) {
        return safeRuntimeKey(uriVariable(request,"eventCode")) && isObject(body) && textEqualsIgnoreCase(body,"configType","FORM")
                && textEquals(body,"configId",target.formId()) && textEquals(body,"entityCode",target.entityCode()) && recordMatches(body,target);
    }


    /**
     * 判断是否匹配文件写入；判断结果决定调用方的后续分支。
     *
     * @param request 本次请求，后续经校验后用于判断是否匹配文件写入
     * @param root 根，供本方法判断是否匹配文件写入时使用
     * @return 文件写入条件成立时为 true，否则为 false
     */
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

    /**
     * 处理授权流程记录，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理授权流程记录
     * @param root 根，供本方法处理授权流程记录时使用
     * @param session 会话，作为 {@code targetResolver.authorize} 的输入影响后续处理
     * @return 处理后的授权流程记录结果，供调用方继续处理
     */
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

    /** 每跳权限和关系由实体边界重算，第一跳必须属于当前 Embed 根及固定记录。 */
    private EmbedNativeFormTarget authorizeTraversalTarget(HttpServletRequest request, JsonNode body, EmbedNativeFormTarget root, AuthenticatedEmbedSession session) {
        Map<String,Object> context=compositionContext(request,body);
        if (context.isEmpty()) return null;
        if (traversalRuntimePort==null) throw denied();
        // 先检查根，避免把其他业务页面的合法路径当作当前嵌入页的授权来源。
        boolean formRoot="FORM".equals(context.get("ownerType")) && Objects.equals(root.formId(),context.get("ownerId"));
        boolean listRoot="LIST".equals(context.get("ownerType")) && "LIST".equals(root.entryMode());
        if(listRoot) {
            Target list=authorizeList(root,session,root.entityCode(),root.listKey());
            listRuntimePort.requireListOwner(list,string(context.get("ownerId")));
        } else if (!formRoot) throw denied();
        String rootRecordId=string(context.get("recordId"));
        targetResolver.authorize(session,"VIEW",rootRecordId);
        TraversalTarget fixed=traversalRuntimePort.resolve(context);
        if (fixed==null || !Objects.equals(context.get("ownerId"),fixed.rootOwnerId()) || !Objects.equals(rootRecordId,fixed.rootRecordId())) throw denied();
        boolean form="FORM".equals(fixed.targetOwnerType()), list="LIST".equals(fixed.targetOwnerType());
        if ((!form&&!list)||!StringUtils.hasText(fixed.targetEntityCode())) throw denied();
        return new EmbedNativeFormTarget(fixed.targetEntityCode(),form?fixed.targetOwnerId():root.formId(),
                form?fixed.targetReleaseId():root.formReleaseId(),form?fixed.targetReleaseVersion():root.formReleaseVersion(),
                null,null,null,form?(StringUtils.hasText(fixed.targetRecordId())?"VIEW":"CREATE"):"LIST",form?fixed.targetRecordId():null,null,Map.of(),Map.of(),Map.of());
    }

    private static String string(Object value) {return value==null?null:String.valueOf(value);}

    private static Map<String,Object> runtimeContext(HttpServletRequest request,JsonNode body) {
        return contextMap(request,body,"runtimeContext");
    }

    private static Map<String,Object> compositionContext(HttpServletRequest request,JsonNode body) {
        Map<String,Object> direct=contextMap(request,body,"compositionContext");
        if (!direct.isEmpty()) return direct;
        Object nested=runtimeContext(request,body).get("compositionContext");
        return nested instanceof Map<?,?> ? new com.fasterxml.jackson.databind.ObjectMapper().convertValue(nested,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}):Map.of();
    }

    /** JSON 仅携带定位值，任何访问结论均在后续服务端路径校验后生成。 */
    private static Map<String,Object> contextMap(HttpServletRequest request,JsonNode body,String key) {
        try {
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            if (body!=null && body.path(key).isObject()) return mapper.convertValue(body.get(key),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            String raw=request.getParameter(key);
            return StringUtils.hasText(raw)?mapper.readValue(raw,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}):Map.of();
        } catch (Exception exception) {throw denied();}
    }





    /**
     * 记录匹配；供后续追溯或审计使用。
     *
     * @param body 请求体，后续用于记录匹配并传递处理结果
     * @param target 目标，作为 {@code equals} 的输入影响后续处理
     * @return 匹配条件成立时为 true，否则为 false
     */
    private static boolean recordMatches(
            JsonNode body,
            EmbedNativeFormTarget target) {
        String value = text(body, "recordId");
        return "CREATE".equals(target.entryMode())
                ? !StringUtils.hasText(value)
                : Objects.equals(target.recordId(), value);
    }

    /**
     * 判断坐标相等条件是否成立，供调用方选择后续分支。
     *
     * @param request 本次请求，后续经校验后用于处理坐标相等
     * @param name 名称，后续用于处理坐标相等时匹配或展示
     * @param expected 预期，供本方法处理坐标相等时使用
     * @return 坐标相等条件成立时为 true，否则为 false
     */
    private static boolean coordinateEquals(
            HttpServletRequest request,
            String name,
            String expected) {
        String actual = request.getParameter(name);
        return StringUtils.hasText(expected)
                ? Objects.equals(expected, actual)
                : !StringUtils.hasText(actual);
    }

    /**
     * 判断坐标整数相等条件是否成立，供调用方选择后续分支。
     *
     * @param request 本次请求，后续经校验后用于处理坐标整数相等
     * @param name 名称，后续用于处理坐标整数相等时匹配或展示
     * @param expected 预期，供本方法处理坐标整数相等时使用
     * @return 坐标整数相等条件成立时为 true，否则为 false
     */
    private static boolean coordinateIntEquals(
            HttpServletRequest request,
            String name,
            Integer expected) {
        String actual = request.getParameter(name);
        return expected == null
                ? !StringUtils.hasText(actual)
                : Objects.equals(expected, positiveInt(actual));
    }

    /**
     * 处理正数整数，并将结果传给后续步骤。
     *
     * @param value 待处理正数整数的原始输入，结果供调用方继续使用
     * @return 处理后的正数整数结果，供调用方继续处理
     */
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

    /**
     * 处理正数整数，并将结果传给后续步骤。
     *
     * @param value 待处理正数整数的原始输入，结果供调用方继续使用
     * @return 处理后的正数整数结果，供调用方继续处理
     */
    private static Integer positiveInt(JsonNode value) {
        return value != null && value.canConvertToInt()
                && value.asInt() > 0 ? value.asInt() : null;
    }

    /**
     * 原生页面使用 edit，平台记录授权模型使用 VIEW；APPROVE 保留原值，
     * 由目标解析器先执行 VIEW/DataScope，再交给流程任务权限做最终审批授权。
     *
     * @param value 待规范化模式的原始输入，结果供调用方继续使用
     * @return 规范化后的模式文本，供调用方比较或展示
     */
    private static String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String mode = value.trim().toUpperCase(java.util.Locale.ROOT);
        return "EDIT".equals(mode) ? "VIEW" : mode;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param first 首个，供本方法处理首个文本时使用
     * @param second {@code second}，供本方法处理首个文本时使用
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    /**
     * 处理首个正数整数，并将结果传给后续步骤。
     *
     * @param first 首个，作为 {@code positiveInt} 的输入影响后续处理
     * @param second {@code second}，供本方法处理首个正数整数时使用
     * @return 处理后的首个正数整数结果，供调用方继续处理
     */
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

    /**
     * 整理{@code uri}流程变量数据，供调用方遍历或继续处理。
     *
     * @param request 本次请求，后续经校验后用于处理{@code uri}流程变量
     * @return {@code uri}流程变量键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> uriVariables(
            HttpServletRequest request) {
        Object value = request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map<?, ?> map
                ? (Map<String, String>) map : Collections.emptyMap();
    }

    /**
     * 生成{@code uri}变量文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理{@code uri}变量
     * @param name 名称，后续用于处理{@code uri}变量时匹配或展示
     * @return 处理后的{@code uri}变量文本，供调用方比较或展示
     */
    private static String uriVariable(
            HttpServletRequest request,
            String name) {
        return uriVariables(request).get(name);
    }

    /**
     * 接受常见稳定代码字符，不赋予任何特定事件码额外权限。
     *
     * @param value 待处理安全运行时键的原始输入，结果供调用方继续使用
     * @return 安全运行时键条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否对象；判断结果决定调用方的后续分支。
     *
     * @param body 请求体，后续用于判断是否对象并传递处理结果
     * @return 对象条件成立时为 true，否则为 false
     */
    private static boolean isObject(JsonNode body) {
        return body != null && body.isObject();
    }

    /**
     * 判断文本相等条件是否成立，供调用方选择后续分支。
     *
     * @param body 请求体，后续用于处理文本相等并传递处理结果
     * @param field 字段，供本方法处理文本相等时使用
     * @param expected 预期，供本方法处理文本相等时使用
     * @return 文本相等条件成立时为 true，否则为 false
     */
    private static boolean textEquals(
            JsonNode body,
            String field,
            String expected) {
        return Objects.equals(expected, text(body, field));
    }

    /**
     * 判断文本相等{@code ignore}分支条件是否成立，供调用方选择后续分支。
     *
     * @param body 请求体，后续用于处理文本相等{@code ignore}分支并传递处理结果
     * @param field 字段，作为 {@code text} 的输入影响后续处理
     * @param expected 预期，供本方法处理文本相等{@code ignore}分支时使用
     * @return 文本相等{@code ignore}分支条件成立时为 true，否则为 false
     */
    private static boolean textEqualsIgnoreCase(
            JsonNode body,
            String field,
            String expected) {
        String actual = text(body, field);
        return actual != null && expected.equalsIgnoreCase(actual);
    }

    /**
     * 判断整数相等条件是否成立，供调用方选择后续分支。
     *
     * @param body 请求体，后续用于处理整数相等并传递处理结果
     * @param field 字段，作为 {@code body.get} 的输入影响后续处理
     * @param expected 预期，供本方法处理整数相等时使用
     * @return 整数相等条件成立时为 true，否则为 false
     */
    private static boolean intEquals(
            JsonNode body,
            String field,
            int expected) {
        JsonNode value = body == null ? null : body.get(field);
        return value != null && value.canConvertToInt()
                && value.asInt() == expected;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param body 请求体，后续用于处理文本并传递处理结果
     * @param field 字段，作为 {@code body.get} 的输入影响后续处理
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() || !value.isTextual()
                ? null : value.asText();
    }

    /**
     * 构造已拒绝异常，供调用方区分失败原因并终止后续处理。
     *
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private static EmbedException denied() {
        return new EmbedException(
                403,
                EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed delegated runtime operation is not allowed");
    }

    /**
     * 封装固定列表的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     */
    private record PinnedList(
            String listKey,
            String releaseId,
            Integer releaseVersion) {

        private static final PinnedList NONE =
                new PinnedList(null, null, null);
    }
}
