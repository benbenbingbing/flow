package com.workflow.entity.ui.application;

import com.workflow.entity.ui.application.model.UiDataSourceExecutionAuthorization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.permission.application.model.DataPermissionResult;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.permission.application.DataPermissionEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UI 数据源执行访问控制服务，负责数据源预览与发布执行链路的来源校验、
 * 权限计算和可信上下文清洗。
 *
 * <p>预览链路校验当前管理员可维护的 FORM/LIST/ENTITY 草稿绑定；
 * 发布链路校验 ACTIVE 发布快照绑定并叠加数据范围权限计划，
 * 同时拦截客户端伪造的服务端可信身份与发布字段。</p>
 */
@Service
@RequiredArgsConstructor
public class UiDataSourceExecutionAccessService {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";
    private static final String ENTITY = "ENTITY";
    private static final Set<String> RESERVED_REQUEST_KEYS = Set.of(
            "idempotencykey",
            "datascopeplan",
            "permissionsummary",
            "trustedruntimecontext",
            "authenticateduser",
            "userid",
            "username",
            "tenantid",
            "orgid",
            "organizationid",
            "deptid",
            "departmentid",
            "entityid",
            "entitycode",
            "formid",
            "formkey",
            "listid",
            "listkey",
            "ownertype",
            "ownerid",
            "configtype",
            "configid",
            "serviceid",
            "operationcode",
            "bindingcode",
            "requestid",
            "sourcerecordid",
            "releaseid",
            "releaseversion",
            "publishedreleaseid");
    /** 仅表单按钮将待办坐标视作服务端身份，其他 UI 事件保留既有业务字段语义。 */
    private static final Set<String> FORM_BUTTON_RESERVED_REQUEST_KEYS =
            Set.of("taskid", "processinstanceid");

    private final UiConfigReleaseMapper releaseMapper;
    private final UiDataSourceBindingMatcher bindingMatcher;
    private final EntityFormMapper formMapper;
    private final EntityFormNodeMapper formNodeMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityListFieldMapper listFieldMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserService userService;
    private final DataPermissionEngine dataPermissionEngine;
    private final UiConfigurationAccessService configurationAccessService;
    private final UiConfigReleaseService releaseService;
    private final ObjectMapper objectMapper;

    /**
     * 授权草稿预览执行，要求来源为当前管理员可维护的 FORM/LIST/ENTITY 草稿绑定。
     *
     * @param definition 数据源定义
     * @param request    执行请求
     * @return 执行授权凭证
     * @throws BusinessForbiddenException 缺少预览权限或来源伪造时抛出
     * @throws BusinessConflictException  来源配置不存在或数据源作用域不匹配时抛出
     */
    public UiDataSourceExecutionAuthorization authorizePreview(
            UiExtensionDefinition definition,
            UiExtensionExecuteRequest request) {
        Origin origin = resolveOrigin(request);
        requirePreviewAccess(origin);
        rejectTrustedMetadata(request);
        ConfigTarget target = requireTarget(origin);
        String bindingPath = findDraftBinding(
                origin,
                target,
                normalize(request.getUsage()),
                request.getTargetType(),
                request.getTargetKey(),
                definition.getId(),
                definition.getOperationCode());
        if (!StringUtils.hasText(bindingPath)
                && StringUtils.hasText(definition.getLegacyServiceId())) {
            // 关联内容等深层草稿可能未由 SQL 重写；旧 pair
            // 只用于读取兼容，下次保存会规范化为 extensionId。
            bindingPath = findDraftBinding(
                    origin,
                    target,
                    normalize(request.getUsage()),
                    request.getTargetType(),
                    request.getTargetKey(),
                    definition.getLegacyServiceId(),
                    definition.getOperationCode());
        }
        if (!StringUtils.hasText(bindingPath)) {
            throw forbidden(
                    "UI_DATA_SOURCE_DRAFT_BINDING_REQUIRED",
                    "数据源预览必须来自当前管理员可维护的 FORM/LIST/ENTITY 草稿绑定");
        }
        requireScopeCompatibility(definition, origin, target);
        return authorization(
                true,
                origin,
                null,
                null,
                bindingPath,
                target,
                request);
    }

    /**
     * 接口服务中心调试入口。管理员必须明确选择一个 FORM/LIST/ENTITY 业务上下文，
     * 但不要求先把待调试操作绑定到该草稿。
     *
     * @param definition 定义，作为 {@code requireScopeCompatibility} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理授权管理预览
     * @return 处理后的授权管理预览结果，供调用方继续处理
     */
    public UiDataSourceExecutionAuthorization authorizeManagementPreview(
            UiExtensionDefinition definition,
            UiExtensionExecuteRequest request) {
        configurationAccessService.requireGlobalConfigurationAccess();
        Origin origin = resolveOrigin(request);
        requirePreviewAccess(origin);
        rejectTrustedMetadata(request);
        ConfigTarget target = requireTarget(origin);
        requireScopeCompatibility(definition, origin, target);
        return authorization(
                true,
                origin,
                null,
                null,
                "INTERFACE_SERVICE_TEST",
                target,
                request);
    }

    /**
     * 授权发布版本执行，要求来源为 ACTIVE 发布快照绑定且用户具备列表运行时访问权限。
     *
     * @param definition 数据源定义
     * @param request    执行请求
     * @return 执行授权凭证
     * @throws BusinessForbiddenException 列表访问权限不足或来源伪造时抛出
     * @throws BusinessConflictException  发布版本不存在、过期或数据源作用域不匹配时抛出
     */
    public UiDataSourceExecutionAuthorization authorizePublished(
            UiExtensionDefinition definition,
            UiExtensionExecuteRequest request) {
        Origin origin = resolveOrigin(request);
        rejectTrustedMetadata(request);
        ConfigTarget target = requireTarget(origin);
        requireListRuntimeAccess(origin, target);
        UiConfigRelease release = resolvePublishedRelease(
                origin,
                target,
                request);
        Map<String, Object> snapshot =
                releaseService.verifiedReleaseSnapshot(release);
        String bindingPath = findPublishedBinding(
                origin,
                snapshot,
                normalize(request.getUsage()),
                request.getTargetType(),
                request.getTargetKey(),
                definition.getId(),
                definition.getOperationCode());
        if (!StringUtils.hasText(bindingPath)
                && StringUtils.hasText(definition.getLegacyServiceId())) {
            bindingPath = findPublishedBinding(
                    origin,
                    snapshot,
                    normalize(request.getUsage()),
                    request.getTargetType(),
                    request.getTargetKey(),
                    definition.getLegacyServiceId(),
                    definition.getOperationCode());
        }
        if (!StringUtils.hasText(bindingPath)) {
            throw forbidden(
                    "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
                    "当前 ACTIVE 发布版本未绑定该数据源或使用位置");
        }
        requireScopeCompatibility(definition, origin, target);
        return authorization(
                false,
                origin,
                release.getId(),
                release.getVersion(),
                bindingPath,
                target,
                request);
    }

    /**
     * 使用表单事件运行时已经验真的同一份有效快照授权钉版按钮 READ 操作。
     *
     * <p>与通用发布授权不同，本入口不会重新读取 ACTIVE 或基础发布记录；这样
     * 标准发布切换和流程热修复都不能让按钮权限、事件链与 Provider 授权来自
     * 不同制品。来源绑定身份由发布步骤携带的服务端字段精确匹配，普通客户端
     * 无法通过 JSON 写入这些字段。用户、作用域和 DataScope 仍按当前服务端状态
     * 重新计算。</p>
     *
     * @param definition 已通过独立哈希校验的钉版操作定义
     * @param request 服务端构造的 FORM_BUTTON_CLICK 操作请求
     * @param resolvedSnapshot 同一次事件解析得到的有效宿主快照
     * @param expectedSnapshotHash 解析时验证过的有效快照哈希
     * @return 精确绑定和当前数据权限组成的执行授权
     */
    public UiDataSourceExecutionAuthorization authorizeResolvedFormButton(
            UiExtensionDefinition definition,
            UiExtensionExecuteRequest request,
            Map<String, Object> resolvedSnapshot,
            String expectedSnapshotHash) {
        if (request == null
                || !FORM.equals(normalize(request.getConfigType()))
                || !UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                        normalize(request.getUsage()))
                || !request.isServerPinnedRelease()
                || !StringUtils.hasText(
                        request.getServerIdempotencyKey())) {
            throw forbidden(
                    "UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED",
                    "表单按钮钉版操作只允许来自可信事件运行时");
        }
        return authorizeResolvedFormEvent(definition, request, resolvedSnapshot, expectedSnapshotHash);
    }

    /**
     * 按本次字段事件已验真的有效快照授权接口调用，包含流程热修复后的新增/修改绑定。
     * 只接受内部事件请求和精确来源绑定；仍验快照哈希、表单归属、作用域及当前数据权限。
     *
     * @param definition 当前启用的接口定义，字段事件沿用现有接口版本契约
     * @param request 已完成事件版本和操作权限校验的内部请求
     * @param resolvedSnapshot 事件解析使用的完整有效表单快照
     * @param expectedSnapshotHash 事件解析时验证过的有效快照哈希
     * @return 当前接口调用的授权凭证
     */
    public UiDataSourceExecutionAuthorization authorizeResolvedFormFieldEvent(
            UiExtensionDefinition definition,
            UiExtensionExecuteRequest request,
            Map<String, Object> resolvedSnapshot,
            String expectedSnapshotHash) {
        if (request == null
                || !isFormFieldEvent(request.getConfigType(), request.getTargetType(), request.getUsage())
                || !request.isServerPinnedRelease()
                || !StringUtils.hasText(request.getServerIdempotencyKey())) {
            throw forbidden("UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED",
                    "表单字段接口只允许来自可信事件运行时");
        }
        return authorizeResolvedFormEvent(definition, request, resolvedSnapshot, expectedSnapshotHash);
    }

    /**
     * 复用同一有效制品验证绑定，禁止重新读取 ACTIVE 或基础版本造成跨版本授权。
     *
     * @param definition 定义，作为 {@code requireScopeCompatibility} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理授权已解析表单事件
     * @param resolvedSnapshot 已解析快照，作为 {@code releaseService.verifyResolvedEventSnapshot} 的输入影响后续处理
     * @param expectedSnapshotHash 预期快照哈希，作为 {@code releaseService.verifyResolvedEventSnapshot} 的输入影响后续处理
     * @return 处理后的授权已解析表单事件结果，供调用方继续处理
     */
    private UiDataSourceExecutionAuthorization authorizeResolvedFormEvent(
            UiExtensionDefinition definition,
            UiExtensionExecuteRequest request,
            Map<String, Object> resolvedSnapshot,
            String expectedSnapshotHash) {
        if (!StringUtils.hasText(request.getReleaseId())
                || request.getReleaseVersion() == null) {
            throw conflict(
                    "UI_DATA_SOURCE_PINNED_RELEASE_REQUIRED",
                    "表单事件接口操作缺少基础发布身份");
        }
        requireResolvedBindingIdentity(request);
        Origin origin = resolveOrigin(request);
        rejectTrustedMetadata(request);
        ConfigTarget target = requireTarget(origin);
        releaseService.verifyResolvedEventSnapshot(
                resolvedSnapshot, expectedSnapshotHash);
        requireResolvedFormIdentity(origin, target, resolvedSnapshot);
        String bindingPath = findPublishedBinding(
                origin,
                resolvedSnapshot,
                normalize(request.getUsage()),
                request.getServerBindingTargetType(),
                request.getServerBindingTargetKey(),
                definition.getId(),
                definition.getOperationCode(),
                request.getServerBindingOwnerType(),
                request.getServerBindingOwnerId());
        if (!StringUtils.hasText(bindingPath)
                && StringUtils.hasText(definition.getLegacyServiceId())) {
            bindingPath = findPublishedBinding(
                    origin,
                    resolvedSnapshot,
                    normalize(request.getUsage()),
                    request.getServerBindingTargetType(),
                    request.getServerBindingTargetKey(),
                    definition.getLegacyServiceId(),
                    definition.getOperationCode(),
                    request.getServerBindingOwnerType(),
                    request.getServerBindingOwnerId());
        }
        if (!StringUtils.hasText(bindingPath)) {
            throw forbidden(
                    "UI_DATA_SOURCE_PUBLISHED_BINDING_REQUIRED",
                    "本次有效表单版本未绑定该事件接口步骤");
        }
        requireScopeCompatibility(definition, origin, target);
        return authorization(
                false,
                origin,
                request.getReleaseId(),
                request.getReleaseVersion(),
                bindingPath,
                target,
                request);
    }

    /**
     * 校验并获取已解析绑定身份；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验并获取已解析绑定身份
     */
    private void requireResolvedBindingIdentity(
            UiExtensionExecuteRequest request) {
        String targetType = normalize(
                request.getServerBindingTargetType());
        if (!StringUtils.hasText(request.getServerBindingOwnerType())
                || !StringUtils.hasText(request.getServerBindingOwnerId())
                || !StringUtils.hasText(targetType)
                || !"OWNER".equals(targetType)
                && !StringUtils.hasText(
                        request.getServerBindingTargetKey())) {
            throw conflict(
                    "UI_EVENT_PINNED_BINDING_INVALID",
                    "表单事件步骤缺少可信来源绑定身份");
        }
    }

    /**
     * 校验并获取已解析表单身份；不满足约束时阻止后续处理。
     *
     * @param origin 来源，供本方法校验并获取已解析表单身份时使用
     * @param target 目标，供本方法校验并获取已解析表单身份时使用
     * @param snapshot 快照，供本方法校验并获取已解析表单身份时使用
     */
    private void requireResolvedFormIdentity(
            Origin origin,
            ConfigTarget target,
            Map<String, Object> snapshot) {
        Map<String, Object> form = snapshot == null
                ? Map.of() : stringMap(snapshot.get("form"));
        if (!FORM.equals(normalize(text(snapshot == null
                ? null : snapshot.get("configType"))))
                || !Objects.equals(origin.configId(), text(form.get("id")))
                || !Objects.equals(target.entityId(), text(
                        form.get("entityId")))) {
            throw conflict(
                    "UI_EVENT_EFFECTIVE_SNAPSHOT_CONFLICT",
                    "表单事件有效快照与请求表单或实体不一致");
        }
    }

    /**
     * 解析已发布发布版本；输出作为后续校验或处理的输入。
     *
     * @param origin 来源，作为 {@code releaseMapper.findActive} 的输入影响后续处理
     * @param target 目标，作为 {@code requireActiveOwnerRelease} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于解析已发布发布版本
     * @return 解析后的已发布发布版本结果，供调用方继续处理
     */
    private UiConfigRelease resolvePublishedRelease(
            Origin origin,
            ConfigTarget target,
            UiExtensionExecuteRequest request) {
        if (request.isServerPinnedRelease()) {
            if (!StringUtils.hasText(request.getServerIdempotencyKey())) {
                throw new BusinessForbiddenException(
                        "UI_DATA_SOURCE_TRUSTED_EXECUTION_REQUIRED",
                        "历史钉版发布只允许携带服务端可信执行种子的内部调用");
            }
            if (!StringUtils.hasText(request.getReleaseId())
                    || request.getReleaseVersion() == null) {
                throw conflict(
                        "UI_DATA_SOURCE_PINNED_RELEASE_REQUIRED",
                        "服务端固定版本执行缺少发布版本标识");
            }
            UiConfigRelease release =
                    releaseMapper.selectById(
                            request.getReleaseId());
            if (release == null
                    || !Objects.equals(
                    origin.configType(),
                    release.getConfigType())
                    || !Objects.equals(
                    origin.configId(),
                    release.getConfigId())
                    || !Objects.equals(
                    request.getReleaseVersion(),
                    release.getVersion())
                    || !StringUtils.hasText(
                    release.getSnapshotDocument())) {
                throw conflict(
                        "UI_DATA_SOURCE_PINNED_RELEASE_CONFLICT",
                        "服务端固定的UI发布版本不存在或与流程快照不一致");
            }
            return release;
        }

        UiConfigRelease release = releaseMapper.findActive(
                origin.configType(),
                origin.configId());
        if (release == null
                || !StringUtils.hasText(
                release.getSnapshotDocument())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_REQUIRED",
                    "运行时数据源只能来自已发布并激活的 FORM/LIST 配置");
        }
        requireActiveOwnerRelease(origin, target, release);
        if (StringUtils.hasText(request.getReleaseId())
                && !Objects.equals(
                request.getReleaseId(),
                release.getId())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    "客户端配置版本已过期，请刷新页面后重试");
        }
        if (request.getReleaseVersion() != null
                && !Objects.equals(
                request.getReleaseVersion(),
                release.getVersion())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    "客户端配置版本已过期，请刷新页面后重试");
        }
        return release;
    }

    /**
     * 处理授权，并将结果传给后续步骤。
     *
     * @param preview 预览，作为 {@code UiDataSourceExecutionAuthorization} 的输入影响后续处理
     * @param origin 来源，作为 {@code requireClaimConsistency} 的输入影响后续处理
     * @param releaseId 发布版本ID，后续用于处理授权时定位或关联目标
     * @param releaseVersion 发布版本，供本方法处理授权时使用
     * @param bindingPath 绑定路径，供本方法处理授权时使用
     * @param target 目标，作为 {@code requireClaimConsistency} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理授权
     * @return 处理后的授权结果，供调用方继续处理
     */
    private UiDataSourceExecutionAuthorization authorization(
            boolean preview,
            Origin origin,
            String releaseId,
            Integer releaseVersion,
            String bindingPath,
            ConfigTarget target,
            UiExtensionExecuteRequest request) {
        requireClaimConsistency(request, origin, target);
        SysUser user = currentUser();
        DataScopePlan plan = permissionPlan(
                target.entityCode(),
                target.listKey(),
                user);
        Map<String, Object> requestContext = sanitizeContext(
                request == null ? null : request.getContext());
        return new UiDataSourceExecutionAuthorization(
                preview,
                origin.configType(),
                origin.configId(),
                releaseId,
                releaseVersion,
                bindingPath,
                normalize(request.getUsage()),
                target.entityId(),
                target.entityCode(),
                target.listKey(),
                user,
                plan,
                requestContext,
                request.getServerIdempotencyKey());
    }

    /**
     * 解析来源；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析来源
     * @return 解析后的来源结果，供调用方继续处理
     */
    private Origin resolveOrigin(UiExtensionExecuteRequest request) {
        if (request == null) {
            throw originRequired();
        }
        Map<String, Object> context = request.getContext() == null
                ? Map.of() : request.getContext();
        String contextFormId = text(context.get("formId"));
        String contextListId = firstText(
                context.get("listId"),
                context.get("listConfigId"));
        if (StringUtils.hasText(contextFormId)
                && StringUtils.hasText(contextListId)) {
            throw spoofed("请求同时声明 FORM 和 LIST 来源");
        }
        String explicitType = normalize(request.getConfigType());
        String inferredType = StringUtils.hasText(contextFormId)
                ? FORM
                : StringUtils.hasText(contextListId) ? LIST : "";
        if (StringUtils.hasText(explicitType)
                && StringUtils.hasText(inferredType)
                && !explicitType.equals(inferredType)) {
            throw spoofed("请求配置类型与运行上下文不一致");
        }
        String configType = StringUtils.hasText(explicitType)
                ? explicitType : inferredType;
        if (!Set.of(FORM, LIST, ENTITY).contains(configType)) {
            throw originRequired();
        }
        String contextId = FORM.equals(configType)
                ? contextFormId
                : LIST.equals(configType)
                        ? contextListId
                        : null;
        if (StringUtils.hasText(request.getConfigId())
                && StringUtils.hasText(contextId)
                && !Objects.equals(request.getConfigId(), contextId)) {
            throw spoofed("请求配置 ID 与运行上下文不一致");
        }
        String configId = firstText(request.getConfigId(), contextId);
        if (!StringUtils.hasText(configId)) {
            throw originRequired();
        }
        return new Origin(configType, configId);
    }

    /**
     * 校验并获取目标；不满足约束时阻止后续处理。
     *
     * @param origin 来源，作为 {@code definitionMapper.selectById} 的输入影响后续处理
     * @return 校验并获取后的目标结果，供调用方继续处理
     */
    private ConfigTarget requireTarget(Origin origin) {
        if (ENTITY.equals(origin.configType())) {
            EntityDefinition entity = definitionMapper.selectById(
                    origin.configId());
            if (entity == null) {
                throw conflict(
                        "UI_DATA_SOURCE_ENTITY_NOT_FOUND",
                        "数据源来源实体不存在或已删除");
            }
            return new ConfigTarget(
                    entity.getId(),
                    entity.getEntityCode(),
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        if (FORM.equals(origin.configType())) {
            EntityForm form = formMapper.selectById(origin.configId());
            if (form == null) {
                throw conflict(
                        "UI_DATA_SOURCE_CONFIG_NOT_FOUND",
                        "数据源来源表单不存在或已删除");
            }
            EntityDefinition entity = definitionMapper.selectById(
                    form.getEntityId());
            if (entity == null) {
                throw conflict(
                        "UI_DATA_SOURCE_ENTITY_NOT_FOUND",
                        "数据源来源表单绑定的实体不存在");
            }
            return new ConfigTarget(
                    form.getEntityId(),
                    entity.getEntityCode(),
                    null,
                    form.getActiveReleaseId(),
                    null,
                    form,
                    null);
        }
        EntityListConfig list = listMapper.selectById(origin.configId());
        if (list == null) {
            throw conflict(
                    "UI_DATA_SOURCE_CONFIG_NOT_FOUND",
                    "数据源来源列表不存在或已删除");
        }
        EntityDefinition entity = StringUtils.hasText(list.getEntityId())
                ? definitionMapper.selectById(list.getEntityId())
                : definitionMapper.findByEntityCode(list.getEntityCode())
                        .orElse(null);
        if (entity == null) {
            throw conflict(
                    "UI_DATA_SOURCE_ENTITY_NOT_FOUND",
                    "数据源来源列表绑定的实体不存在");
        }
        String entityCode = firstText(list.getEntityCode(), entity.getEntityCode());
        return new ConfigTarget(
                entity.getId(),
                entityCode,
                list.getListKey(),
                list.getActiveReleaseId(),
                list.getAccessPermissionCode(),
                null,
                list);
    }

    /**
     * 查询草稿绑定；查询结果供调用方展示或继续处理。
     *
     * @param origin 来源，作为 {@code bindingMatcher.findDraftEvent} 的输入影响后续处理
     * @param target 目标，供本方法查询草稿绑定时使用
     * @param usage 使用场景，作为 {@code bindingMatcher.findForm} 的输入影响后续处理
     * @param targetType 目标类型标识，决定后续草稿绑定采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询草稿绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于查询草稿绑定时定位或关联目标
     * @return 查询后的草稿绑定文本，供调用方比较或展示
     */
    private String findDraftBinding(
            Origin origin,
            ConfigTarget target,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode) {
        String eventBinding = bindingMatcher.findDraftEvent(
                origin.configType(),
                origin.configId(),
                target.entityId(),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode);
        if (StringUtils.hasText(eventBinding)) {
            return eventBinding;
        }
        if (FORM.equals(origin.configType())) {
            EntityForm form = formMapper.selectById(origin.configId());
            List<Map<String, Object>> owners = new ArrayList<>();
            owners.add(objectMapper.convertValue(
                    form,
                    new TypeReference<Map<String, Object>>() {}));
            for (EntityFormNode node : formNodeMapper.findByFormId(origin.configId())) {
                owners.add(objectMapper.convertValue(
                        node,
                        new TypeReference<Map<String, Object>>() {}));
            }
            return bindingMatcher.findForm(
                    owners,
                    usage,
                    targetType,
                    targetKey,
                    sourceId,
                    operationCode,
                    "$.draft.form");
        }
        if (ENTITY.equals(origin.configType())) {
            return null;
        }
        EntityListConfig list = listMapper.selectById(origin.configId());
        List<EntityListField> fields =
                listFieldMapper.findByListConfigId(origin.configId());
        return bindingMatcher.findList(
                objectMapper.convertValue(
                        list,
                        new TypeReference<Map<String, Object>>() {}),
                objectMapper.convertValue(
                        fields,
                        new TypeReference<List<Map<String, Object>>>() {}),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.draft.list");
    }

    /**
     * 查询已发布绑定；查询结果供调用方展示或继续处理。
     *
     * @param origin 来源，供本方法查询已发布绑定时使用
     * @param snapshot 快照，供本方法查询已发布绑定时使用
     * @param usage 使用场景，供本方法查询已发布绑定时使用
     * @param targetType 目标类型标识，决定后续已发布绑定采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询已发布绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于查询已发布绑定时定位或关联目标
     * @return 查询后的已发布绑定文本，供调用方比较或展示
     */
    private String findPublishedBinding(
            Origin origin,
            Map<String, Object> snapshot,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode) {
        return findPublishedBinding(
                origin,
                snapshot,
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                null,
                null);
    }

    /**
     * 查询已发布绑定；查询结果供调用方展示或继续处理。
     *
     * @param origin 来源，作为 {@code bindingMatcher.findPublished} 的输入影响后续处理
     * @param snapshot 快照，作为 {@code normalize} 的输入影响后续处理
     * @param usage 使用场景，供本方法查询已发布绑定时使用
     * @param targetType 目标类型标识，决定后续已发布绑定采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param sourceId 来源ID，后续用于查询已发布绑定时定位或关联目标
     * @param operationCode 操作编码，后续用于查询已发布绑定时定位或关联目标
     * @param bindingOwnerType 绑定归属方类型标识，决定后续已发布绑定采用的处理分支
     * @param bindingOwnerId 绑定归属方ID，后续用于查询已发布绑定时定位或关联目标
     * @return 查询后的已发布绑定文本，供调用方比较或展示
     */
    private String findPublishedBinding(
            Origin origin,
            Map<String, Object> snapshot,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String bindingOwnerType,
            String bindingOwnerId) {
        String snapshotType = normalize(text(snapshot.get("configType")));
        if (!origin.configType().equals(snapshotType)) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    "ACTIVE 发布快照类型与请求来源不一致");
        }
        return bindingMatcher.findPublished(
                origin.configType(),
                snapshot,
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                bindingOwnerType,
                bindingOwnerId);
    }

    /**
     * 校验并获取预览访问；不满足约束时阻止后续处理。
     *
     * @param origin 来源，作为 {@code configurationAccessService.requireEntityAccess} 的输入影响后续处理
     */
    private void requirePreviewAccess(Origin origin) {
        if (ENTITY.equals(origin.configType())) {
            configurationAccessService.requireEntityAccess(
                    origin.configId());
        } else if (FORM.equals(origin.configType())) {
            configurationAccessService.requireFormAccess(origin.configId());
        } else {
            configurationAccessService.requireListAccess(origin.configId());
        }
    }

    /**
     * 校验并获取列表运行时访问；不满足约束时阻止后续处理。
     *
     * @param origin 来源，供本方法校验并获取列表运行时访问时使用
     * @param target 目标，供本方法校验并获取列表运行时访问时使用
     */
    private void requireListRuntimeAccess(
            Origin origin,
            ConfigTarget target) {
        if (!LIST.equals(origin.configType())) {
            return;
        }
        String permission = StringUtils.hasText(target.accessPermissionCode())
                ? target.accessPermissionCode()
                : "entity:"
                        + target.entityCode().toLowerCase(Locale.ROOT)
                        + ":list";
        Set<String> permissions = menuMapper.selectPermsByUserId(
                UserContext.getUserId());
        if (permissions == null || !permissions.contains(permission)) {
            throw forbidden(
                    "UI_DATA_SOURCE_LIST_ACCESS_DENIED",
                    "当前用户没有权限访问该已发布列表的数据源");
        }
    }

    /**
     * 校验并获取活动归属方发布版本；不满足约束时阻止后续处理。
     *
     * @param origin 来源，作为 {@code conflict} 的输入影响后续处理
     * @param target 目标，供本方法校验并获取活动归属方发布版本时使用
     * @param release 发布版本，供本方法校验并获取活动归属方发布版本时使用
     */
    private void requireActiveOwnerRelease(
            Origin origin,
            ConfigTarget target,
            UiConfigRelease release) {
        if (!Objects.equals(target.activeReleaseId(), release.getId())) {
            throw conflict(
                    "UI_DATA_SOURCE_RELEASE_CONFLICT",
                    origin.configType()
                            + " 当前激活版本与发布记录不一致，请重新发布或激活");
        }
    }

    /**
     * 校验并获取作用域兼容性；不满足约束时阻止后续处理。
     *
     * @param definition 定义，作为 {@code normalize} 的输入影响后续处理
     * @param origin 来源，作为 {@code FORM.equals} 的输入影响后续处理
     * @param target 目标，供本方法校验并获取作用域兼容性时使用
     */
    private void requireScopeCompatibility(
            UiExtensionDefinition definition,
            Origin origin,
            ConfigTarget target) {
        String scopeType = normalize(definition.getScopeType());
        boolean compatible = switch (scopeType) {
            case "", "GLOBAL" -> true;
            case "ENTITY" -> Objects.equals(
                    definition.getScopeId(),
                    target.entityId());
            case FORM -> FORM.equals(origin.configType())
                    && Objects.equals(definition.getScopeId(), origin.configId());
            case LIST -> LIST.equals(origin.configType())
                    && Objects.equals(definition.getScopeId(), origin.configId());
            default -> false;
        };
        if (!compatible) {
            throw forbidden(
                    "UI_DATA_SOURCE_SCOPE_MISMATCH",
                    "数据源作用域与请求的 FORM/LIST 发布绑定不一致");
        }
    }

    /**
     * 校验并获取认领{@code consistency}；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验并获取认领{@code consistency}
     * @param origin 来源，供本方法校验并获取认领{@code consistency}时使用
     * @param target 目标，供本方法校验并获取认领{@code consistency}时使用
     */
    private void requireClaimConsistency(
            UiExtensionExecuteRequest request,
            Origin origin,
            ConfigTarget target) {
        if (StringUtils.hasText(request.getEntityCode())
                && !request.getEntityCode().equals(target.entityCode())) {
            throw spoofed("请求 entityCode 与发布配置绑定实体不一致");
        }
        if (LIST.equals(origin.configType())
                && StringUtils.hasText(request.getListKey())
                && !request.getListKey().equals(target.listKey())) {
            throw spoofed("请求 listKey 与发布列表不一致");
        }
    }

    /**
     * 处理当前用户，并将结果传给后续步骤。
     *
     * @return 处理后的当前用户结果，供调用方继续处理
     */
    private SysUser currentUser() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw forbidden(
                    "UI_DATA_SOURCE_USER_CONTEXT_REQUIRED",
                    "数据源执行缺少已认证用户上下文");
        }
        SysUser user = userService.getById(userId);
        if (user == null
                || !SysUser.Status.ENABLED.getValue().equals(user.getStatus())) {
            throw forbidden(
                    "UI_DATA_SOURCE_USER_DISABLED",
                    "当前用户不存在或已停用");
        }
        return user;
    }

    /**
     * 处理权限方案，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 处理后的权限方案结果，供调用方继续处理
     */
    private DataScopePlan permissionPlan(
            String entityCode,
            String listKey,
            SysUser user) {
        DataPermissionResult permission =
                dataPermissionEngine.calculatePermission(
                        entityCode,
                        listKey,
                        user);
        if (permission == null) {
            throw conflict(
                    "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
                    "数据权限引擎未返回可验证的权限计划");
        }
        String sqlFragment = permission.isHasPermission()
                ? (permission.isNeedFilter()
                        ? permission.getSqlCondition()
                        : "1=1")
                : "1=0";
        if (!StringUtils.hasText(sqlFragment)) {
            throw conflict(
                    "UI_DATA_SOURCE_PERMISSION_PLAN_UNAVAILABLE",
                    "数据权限计划缺少有效的范围条件");
        }
        return new DataScopePlan(
                permission.isHasPermission(),
                sqlFragment,
                permission.getSqlParameters(),
                List.of(),
                permission.getMatchedRuleNames() == null
                        ? List.of() : List.copyOf(permission.getMatchedRuleNames()),
                permission.getExplanation(),
                permission.getReleaseVersion());
    }

    /**
     * 处理驳回可信元数据，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理驳回可信元数据
     */
    private void rejectTrustedMetadata(
            UiExtensionExecuteRequest request) {
        if (request == null) {
            return;
        }
        boolean formButton = UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                normalize(request.getUsage()));
        String rejected = formButton
                ? reservedFormButtonKey(
                        request.getContext(),
                        request.getServerIdempotencyKey())
                : reservedKey(
                        request.getContext(),
                        request.getServerIdempotencyKey());
        if (StringUtils.hasText(rejected)) {
            throw spoofed(
                    "接口操作请求不能提交服务端保留的可信字段: "
                            + rejected);
        }
        validateBusinessInput(request.getInput());
    }

    /**
     * 生成保留键文本，供后续匹配或展示。
     *
     * @param value 待处理保留键的原始输入，结果供调用方继续使用
     * @param trustedIdempotencyKey 可信幂等键，后续用于授权校验、关联或幂等去重
     * @return 处理后的保留键文本，供调用方比较或展示
     */
    private static String reservedKey(
            Map<String, Object> value,
            String trustedIdempotencyKey) {
        return reservedKey(value, trustedIdempotencyKey, false);
    }

    /**
     * 生成保留表单按钮键文本，供后续匹配或展示。
     *
     * @param value 待处理保留表单按钮键的原始输入，结果供调用方继续使用
     * @param trustedIdempotencyKey 可信幂等键，后续用于授权校验、关联或幂等去重
     * @return 处理后的保留表单按钮键文本，供调用方比较或展示
     */
    private static String reservedFormButtonKey(
            Map<String, Object> value,
            String trustedIdempotencyKey) {
        return reservedKey(value, trustedIdempotencyKey, true);
    }

    /**
     * 生成保留键文本，供后续匹配或展示。
     *
     * @param value 待处理保留键的原始输入，结果供调用方继续使用
     * @param trustedIdempotencyKey 可信幂等键，后续用于授权校验、关联或幂等去重
     * @param includeFormButtonKeys {@code include}表单按钮键集合，供本方法处理保留键时使用
     * @return 处理后的保留键文本，供调用方比较或展示
     */
    private static String reservedKey(
            Map<String, Object> value,
            String trustedIdempotencyKey,
            boolean includeFormButtonKeys) {
        if (value == null) {
            return null;
        }
        return reservedKey(
                value,
                trustedIdempotencyKey,
                "$",
                0,
                new int[] {0},
                Collections.newSetFromMap(new IdentityHashMap<>()),
                true,
                includeFormButtonKeys);
    }

    /**
     * 所有接口和事件的 input 都是业务数据，字段名不构成认证身份声明。
     * 输入 Schema 在接口执行前校验；用户、部门、权限及幂等身份只能从独立的
     * 服务端授权上下文读取。保留统一的结构预算，防止映射或复制异常输入耗尽资源。
     *
     * @param input 原始或映射后的业务输入
     * @throws BusinessForbiddenException 输入过深、过大或包含循环引用
     */
    static void validateBusinessInput(Map<String, Object> input) {
        String rejected = reservedKey(input, null, "$", 0,
                new int[] {0}, Collections.newSetFromMap(new IdentityHashMap<>()),
                false, false);
        if (StringUtils.hasText(rejected)) {
            throw new BusinessForbiddenException(
                    "UI_DATA_SOURCE_INPUT_STRUCTURE_INVALID",
                    "接口业务输入结构无效: " + rejected);
        }
    }

    /**
     * 字段事件使用已验真的有效表单快照解析其精确绑定。
     *
     * @param configType 配置类型标识，决定后续表单字段事件采用的处理分支
     * @param targetType 目标类型标识，决定后续表单字段事件采用的处理分支
     * @param eventCode 事件编码，后续用于判断是否表单字段事件时定位或关联目标
     * @return 表单字段事件条件成立时为 true，否则为 false
     */
    static boolean isFormFieldEvent(
            String configType, String targetType, String eventCode) {
        return FORM.equals(normalize(configType))
                && "FIELD".equals(normalize(targetType))
                && Set.of(UiDataSourceUsages.FIELD_CHANGE,
                        UiDataSourceUsages.ENTITY_SELECTED,
                        UiDataSourceUsages.FIELD_BUTTON_CLICK)
                .contains(normalize(eventCode));
    }

    /**
     * 检查上下文中的保留元数据，或仅检查业务输入的深度、大小和循环结构。
     * 业务输入与可信身份分别传给 Provider，不能按业务字段名推断认证语义。
     * 上下文仍拒绝嵌套身份声明；服务端幂等种子仅允许出现在根层且必须精确匹配。
     *
     * @param value 待处理保留键的原始输入，结果供调用方继续使用
     * @param trustedIdempotencyKey 可信幂等键，后续用于授权校验、关联或幂等去重
     * @param path 路径，供本方法处理保留键时使用
     * @param depth 深度，供本方法处理保留键时使用
     * @param visitedNodes {@code visited}节点集合，供本方法处理保留键时使用
     * @param visitedContainers {@code visited}{@code containers}，供本方法处理保留键时使用
     * @param inspectReservedKeys 检查保留键集合，供本方法处理保留键时使用
     * @param includeFormButtonKeys {@code include}表单按钮键集合，供本方法处理保留键时使用
     * @return 处理后的保留键文本，供调用方比较或展示
     */
    private static String reservedKey(
            Object value,
            String trustedIdempotencyKey,
            String path,
            int depth,
            int[] visitedNodes,
            Set<Object> visitedContainers,
            boolean inspectReservedKeys,
            boolean includeFormButtonKeys) {
        if (value == null) {
            return null;
        }
        if (depth > 12 || ++visitedNodes[0] > 4096) {
            return path + "（结构过深或过大）";
        }
        if (value instanceof Map<?, ?> map) {
            if (!visitedContainers.add(value)) {
                return path + "（循环结构）";
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey());
                String normalized = key.replace("_", "")
                        .replace("-", "")
                        .toLowerCase(Locale.ROOT);
                boolean trustedRootSeed = depth == 0
                        && "idempotencykey".equals(normalized)
                        && StringUtils.hasText(trustedIdempotencyKey)
                        && Objects.equals(
                                trustedIdempotencyKey,
                                entry.getValue() == null
                                        ? null
                                        : String.valueOf(entry.getValue()));
                if (inspectReservedKeys && !trustedRootSeed
                        && (RESERVED_REQUEST_KEYS.contains(normalized)
                        || includeFormButtonKeys
                        && FORM_BUTTON_RESERVED_REQUEST_KEYS.contains(
                                normalized))) {
                    return path + "." + key;
                }
                String nested = reservedKey(
                        entry.getValue(),
                        trustedIdempotencyKey,
                        path + "." + key,
                        depth + 1,
                        visitedNodes,
                        visitedContainers,
                        inspectReservedKeys,
                        includeFormButtonKeys);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
            visitedContainers.remove(value);
            return null;
        }
        if (value instanceof Collection<?> collection) {
            if (!visitedContainers.add(value)) {
                return path + "（循环结构）";
            }
            int index = 0;
            for (Object item : collection) {
                String nested = reservedKey(
                        item,
                        trustedIdempotencyKey,
                        path + "[" + index++ + "]",
                        depth + 1,
                        visitedNodes,
                        visitedContainers,
                        inspectReservedKeys,
                        includeFormButtonKeys);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
            visitedContainers.remove(value);
        }
        return null;
    }

    /**
     * 清洗上下文；结果供调用方的后续步骤使用。
     *
     * @param context 执行上下文，向后续上下文步骤传递身份、配置或状态
     * @return 上下文键值结果，供调用方继续处理
     */
    private Map<String, Object> sanitizeContext(
            Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (!isReservedRequestKey(key)) {
                result.put(key, value);
            }
        });
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(result));
    }

    /**
     * 判断客户端上下文键是否属于只能由服务端注入的可信元数据。
     * 包内事件运行时复用此规则，在构造 Provider 请求前剥离前端展示上下文中的
     * formId/listKey 等身份声明；上下文中嵌套的伪造值仍由递归校验拒绝。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 保留请求键条件成立时为 true，否则为 false
     */
    static boolean isReservedRequestKey(String key) {
        if (key == null) {
            return false;
        }
        return RESERVED_REQUEST_KEYS.contains(normalizeRequestKey(key));
    }

    /**
     * FORM_BUTTON_CLICK 额外保护服务端核验后的 task/process 坐标。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 保留表单按钮请求键条件成立时为 true，否则为 false
     */
    static boolean isReservedFormButtonRequestKey(String key) {
        String normalized = normalizeRequestKey(key);
        return RESERVED_REQUEST_KEYS.contains(normalized)
                || FORM_BUTTON_RESERVED_REQUEST_KEYS.contains(normalized);
    }

    /**
     * 规范化请求键；输出作为后续校验或处理的输入。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 规范化后的请求键文本，供调用方比较或展示
     */
    private static String normalizeRequestKey(String key) {
        return key == null ? "" : key.replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param value 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面数据来源执行访问的原始输入，结果供调用方继续使用
     * @return 规范化后的界面数据来源执行访问文本，供调用方比较或展示
     */
    private static String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    /**
     * 构造来源必填异常，供调用方区分失败原因。
     *
     * @return 处理后的来源必填结果，供调用方继续处理
     */
    private BusinessForbiddenException originRequired() {
        return forbidden(
                "UI_DATA_SOURCE_EXECUTION_ORIGIN_REQUIRED",
                "数据源执行必须声明可验证的 FORM/LIST/ENTITY 配置来源");
    }

    /**
     * 构造{@code spoofed}异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code forbidden} 的输入影响后续处理
     * @return 处理后的{@code spoofed}结果，供调用方继续处理
     */
    private BusinessForbiddenException spoofed(String message) {
        return forbidden(
                "UI_DATA_SOURCE_EXECUTION_CONTEXT_SPOOFED",
                message);
    }

    /**
     * 构造权限不足异常，供调用方停止当前操作。
     *
     * @param errorCode 错误编码，后续用于处理禁止时定位或关联目标
     * @param message 消息，作为 {@code BusinessForbiddenException} 的输入影响后续处理
     * @return 处理后的禁止结果，供调用方继续处理
     */
    private BusinessForbiddenException forbidden(
            String errorCode,
            String message) {
        return new BusinessForbiddenException(errorCode, message);
    }

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @param errorCode 错误编码，后续用于处理冲突时定位或关联目标
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的冲突结果，供调用方继续处理
     */
    private BusinessConflictException conflict(
            String errorCode,
            String message) {
        return new BusinessConflictException(errorCode, message);
    }

    /**
     * 封装来源的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param configType 配置类型标识，决定后续来源采用的处理分支
     * @param configId 配置ID，后续用于处理来源时定位或关联目标
     */
    private record Origin(
            String configType,
            String configId) {
    }

    /**
     * 封装配置目标的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityId 实体ID，后续用于处理配置目标时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param activeReleaseId 活动发布版本ID，后续用于处理配置目标时定位或关联目标
     * @param accessPermissionCode 访问权限编码，后续用于处理配置目标时定位或关联目标
     * @param form 表单，保存在对象中供后续校验、查询或展示
     * @param list 列表，保存在对象中供后续校验、查询或展示
     */
    private record ConfigTarget(
            String entityId,
            String entityCode,
            String listKey,
            String activeReleaseId,
            String accessPermissionCode,
            EntityForm form,
            EntityListConfig list) {
    }
}
