package com.workflow.entity.form.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.ProcessCatalogItem;
import com.workflow.contracts.process.port.ProcessCatalogPort;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.ActionableTaskContext;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityActionRuleStructurePolicy;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 表单按钮的约定默认值、发布快照解析与运行时鉴权。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityFormActionService {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_OBJECT =
            new TypeReference<>() {};
    private static final Map<String, Set<String>> BUILT_IN_MODES = Map.of(
            "close", Set.of("create", "edit", "approve", "view"),
            "reset", Set.of("create", "edit"),
            "save", Set.of("create", "edit"),
            "saveAndStart", Set.of("create", "edit"),
            "submitApproval", Set.of("approve"));

    private final EntityFormMapper formMapper;
    private final EntityFormNodeMapper formNodeMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityDataDynamicService dataService;
    private final EntityActionCapabilityService capabilityService;
    private final EntityFormActionConfigPolicy configPolicy;
    private final UiConfigReleaseService releaseService;
    private final ProcessCatalogPort processCatalogPort;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 解析当前用户在指定表单上下文中的按钮。
     */
    public List<FormActionRuntimeDTO> resolve(
            FormActionResolveRequest request) {
        log.info(
                "开始解析表单操作栏: formId={}, releaseId={}, releaseVersion={}, tokenPresent={}, mode={}, entityCode={}, recordId={}, listKey={}",
                LogValue.safe(request.getFormId()),
                LogValue.safe(request.getReleaseId()),
                request.getReleaseVersion(),
                StringUtils.hasText(
                        request.getReleaseResolutionToken()),
                LogValue.safe(request.getMode()),
                LogValue.safe(request.getEntityCode()),
                LogValue.safe(request.getRecordId()),
                LogValue.safe(request.getListKey()));
        RuntimeSource source = loadSource(
                request.getFormId(),
                request.getReleaseId(),
                request.getReleaseVersion(),
                request.getReleaseResolutionToken());
        EntityDefinition definition =
                requireDefinition(source.form());
        requireEntityCode(request.getEntityCode(), definition);
        String mode = requireMode(request.getMode());
        EntityDataDTO row = loadRow(
                definition,
                request.getRecordId(),
                request.getListKey());
        if ("approve".equals(mode)) {
            EntityActionCapabilityDTO approval =
                    capabilityService.evaluateApprovalAction(
                            definition.getEntityCode(), row,
                            approvalRule(), null);
            requireApprovalTaskBinding(
                    request.getTaskId(),
                    request.getReleaseResolutionToken(),
                    source, definition, row, approval);
        }
        return resolveTrustedPublishedSnapshot(
                source.form(), definition, mode, row);
    }

    /**
     * 在内置表单动作产生业务副作用前，使用客户端会话携带的固定发布令牌
     * 重新解析同一快照并校验按钮能力。
     *
     * <p>该入口只接受会触发服务端变更的 save/saveAndStart/submitApproval。
     * 发布 ID、版本和签名令牌必须完整，防止调用方省略表单坐标后退回草稿或
     * 最新 ACTIVE，从而绕过用户实际看到的按钮条件。</p>
     *
     * @param request 由服务端组装的表单、实体、记录及任务上下文
     * @param actionKey 待执行的内置按钮 key
     * @throws BusinessForbiddenException 发布上下文不完整或按钮不可执行时抛出
     */
    public void requireBuiltInMutationAction(
            FormActionResolveRequest request,
            String actionKey) {
        if (request == null
                || !Set.of("save", "saveAndStart", "submitApproval")
                .contains(actionKey)) {
            throw new IllegalArgumentException("不支持的表单内置变更动作");
        }
        if (!StringUtils.hasText(request.getFormId())
                || !StringUtils.hasText(request.getReleaseId())
                || request.getReleaseVersion() == null
                || !StringUtils.hasText(
                request.getReleaseResolutionToken())) {
            throw new BusinessForbiddenException(
                    "FORM_ACTION_RELEASE_CONTEXT_REQUIRED",
                    "表单操作缺少完整的固定发布上下文，请刷新后重试");
        }
        FormActionRuntimeDTO action = resolve(request).stream()
                .filter(item -> actionKey.equals(item.getKey()))
                .filter(item -> "built-in".equals(item.getType()))
                .findFirst()
                .orElseThrow(() -> new BusinessForbiddenException(
                        "FORM_BUILT_IN_ACTION_NOT_AVAILABLE",
                        "当前发布表单未开放该操作"));
        if (!action.isVisible() || !action.isEnabled()) {
            throw new BusinessForbiddenException(
                    "FORM_BUILT_IN_ACTION_DENIED",
                    StringUtils.hasText(action.getReason())
                            ? action.getReason()
                            : "当前表单操作不可执行");
        }
    }

    /**
     * 从调用方已经固定并校验过的 Published Form 快照解析标准操作栏。
     *
     * <p>该入口供同一服务进程内的受信任运行时适配器使用，避免再次按 ACTIVE 指针
     * 解析表单而让历史会话漂移。{@code authorizedRow} 必须已经过当前 Flow 用户的
     * 对象权限、DataScope 以及调用方固定上下文校验；本方法只复用平台统一的按钮
     * 配置、权限和可用性规则求值。</p>
     *
     * @param publishedForm 当前会话固定的已发布表单快照
     * @param definition 表单所属实体定义
     * @param mode 标准表单模式
     * @param authorizedRow 已鉴权记录；CREATE 可为空
     * @return 与普通 Flow 表单相同的运行时按钮描述
     */
    public List<FormActionRuntimeDTO> resolveTrustedPublishedSnapshot(
            EntityForm publishedForm,
            EntityDefinition definition,
            String mode,
            EntityDataDTO authorizedRow) {
        if (publishedForm == null || definition == null
                || !Objects.equals(
                        publishedForm.getEntityId(), definition.getId())) {
            throw new IllegalArgumentException("发布表单与实体上下文不一致");
        }
        String normalizedMode = requireMode(mode);
        RuntimeSource source = new RuntimeSource(
                publishedForm,
                readViewConfig(publishedForm.getViewConfig()),
                publishedForm.getNodes() == null
                        ? List.of() : publishedForm.getNodes(),
                List.of(),
                null,
                null);
        Map<String, Object> actionBar =
                configPolicy.actionBar(source.viewConfig());
        Map<String, Object> overrides =
                mapOrEmpty(actionBar.get("builtInOverrides"));

        List<FormActionRuntimeDTO> result = new ArrayList<>();
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            Map<String, Object> close = applyOverride(
                    defaultBuiltIn("close", normalizedMode),
                    mapOrEmpty(overrides.get("close")),
                    normalizedMode);
            if (!enabledForMode(close, normalizedMode)) {
                return result;
            }
            result.add(toRuntime(
                    source.form().getId(),
                    close,
                    evaluateOverrideRule(
                            definition.getEntityCode(),
                            close,
                            authorizedRow),
                    false));
            log.info(
                    "表单操作栏解析完成: formId={}, entityCode={}, mode={}, buttonCount={}, visibleCount={}, enabledCount={}, systemEntity=true",
                    LogValue.safe(source.form().getId()),
                    LogValue.safe(definition.getEntityCode()),
                    LogValue.safe(normalizedMode),
                    result.size(),
                    visibleCount(result),
                    enabledCount(result));
            return result;
        }

        for (String key : List.of(
                "close", "reset", "save",
                "saveAndStart", "submitApproval")) {
            if (!BUILT_IN_MODES.get(key).contains(normalizedMode)) {
                continue;
            }
            Map<String, Object> button = applyOverride(
                    defaultBuiltIn(key, normalizedMode),
                    mapOrEmpty(overrides.get(key)),
                    normalizedMode);
            if (!enabledForMode(button, normalizedMode)) {
                continue;
            }
            EntityActionCapabilityDTO capability =
                    builtInCapability(
                            key,
                            definition,
                            normalizedMode,
                            authorizedRow,
                            button);
            result.add(toRuntime(
                    source.form().getId(),
                    button,
                    capability,
                    false));
        }

        for (Map<String, Object> button :
                mapList(actionBar.get("customButtons"))) {
            if (Boolean.FALSE.equals(button.get("enabled"))
                    || !modes(button).contains(normalizedMode)) {
                continue;
            }
            String permission = text(button.get("perm"));
            EntityActionCapabilityDTO modeCapability =
                    customModeCapability(
                            definition,
                            normalizedMode,
                            authorizedRow);
            EntityActionCapabilityDTO configuredCapability =
                    capabilityService.evaluateConfiguredAction(
                            definition.getEntityCode(),
                            permission,
                            readRule(button),
                            authorizedRow);
            result.add(toRuntime(
                    source.form().getId(),
                    normalizeCustom(button),
                    intersectCapabilities(
                            modeCapability,
                            configuredCapability),
                    true));
        }
        result.sort(Comparator.comparingInt(
                item -> item.getSort() == null
                        ? 0 : item.getSort()));
        log.info(
                "表单操作栏解析完成: formId={}, entityCode={}, mode={}, buttonCount={}, visibleCount={}, enabledCount={}, systemEntity=false",
                LogValue.safe(source.form().getId()),
                LogValue.safe(definition.getEntityCode()),
                LogValue.safe(normalizedMode),
                result.size(),
                visibleCount(result),
                enabledCount(result));
        return result;
    }

    /**
     * FORM_BUTTON_CLICK 服务端执行前的最终鉴权。
     */
    public String requireCustomButton(UiEventExecuteRequest request) {
        RuntimeSource source = loadSource(
                request.getConfigId(),
                request.getReleaseId(),
                request.getReleaseVersion(),
                request.getReleaseResolutionToken());
        return requireCustomButton(request, source);
    }

    /**
     * 基于事件链已经解析并验真的同一份有效快照执行表单按钮最终鉴权。
     *
     * <p>调用方必须传入 {@code resolveRuntimeEventSnapshot} 的原始结果；本方法
     * 不再读取 ACTIVE 或热修复目标，避免权限按钮与随后执行的事件链来自不同制品。
     * 记录读取仍走 {@link EntityDataDynamicService}，继续执行对象权限和 DataScope
     * 校验。</p>
     *
     * @param request 表单按钮执行请求
     * @param verifiedSnapshot 已校验的完整有效发布快照
     * @return 已由记录上下文、标准权限和审批任务共同确认的运行模式
     */
    public String requireCustomButton(
            UiEventExecuteRequest request,
            Map<String, Object> verifiedSnapshot) {
        return requireCustomButton(
                request,
                verifiedSnapshot,
                request == null ? null : request.getReleaseId(),
                request == null ? null : request.getReleaseVersion());
    }

    /**
     * 使用事件链已经固定的基准 Release 身份鉴权按钮；热修复快照仍绑定原流程
     * 基准 Release，不能从客户端请求字段推断。
     */
    public String requireCustomButton(
            UiEventExecuteRequest request,
            Map<String, Object> verifiedSnapshot,
            String resolvedReleaseId,
            Integer resolvedReleaseVersion) {
        if (verifiedSnapshot == null
                || !(verifiedSnapshot.get("form") instanceof Map<?, ?>)) {
            throw new ForbiddenException(
                    "表单按钮缺少可信发布快照");
        }
        EntityForm form = objectMapper.convertValue(
                verifiedSnapshot.get("form"),
                EntityForm.class);
        if (form == null
                || !Objects.equals(request.getConfigId(), form.getId())) {
            throw new ForbiddenException(
                    "表单按钮发布快照与请求不一致");
        }
        return requireCustomButton(
                request,
                new RuntimeSource(
                        form,
                        readViewConfig(form.getViewConfig()),
                        snapshotNodes(verifiedSnapshot),
                        snapshotBindings(verifiedSnapshot),
                        resolvedReleaseId,
                        resolvedReleaseVersion));
    }

    private String requireCustomButton(
            UiEventExecuteRequest request,
            RuntimeSource source) {
        EntityDefinition definition =
                requireDefinition(source.form());
        requireEntityCode(request.getEntityCode(), definition);
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            throw new ForbiddenException(
                    "平台系统表表单不能执行自定义按钮");
        }
        String buttonKey = text(request.getTargetKey());
        Map<String, Object> button = mapList(
                configPolicy.actionBar(source.viewConfig())
                        .get("customButtons"))
                .stream()
                .filter(item -> buttonKey.equals(text(item.get("key"))))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException(
                        "表单按钮不存在或未发布"));
        if (Boolean.FALSE.equals(button.get("enabled"))) {
            throw new ForbiddenException("表单按钮未启用");
        }
        EntityDataDTO row = loadRow(
                definition,
                request.getRecordId(),
                request.getListKey());
        String mode = authorizedRequestMode(
                request, source, definition, row);
        if (!modes(button).contains(mode)) {
            throw new ForbiddenException(
                    "当前表单模式不能执行该按钮");
        }
        capabilityService.requireCustomAction(
                definition.getEntityCode(),
                buttonKey,
                text(button.get("perm")),
                readRule(button),
                row);
        // 条件和 inputMapping 不能读取客户端伪造的 input.button；把同一份
        // 已鉴权发布快照按钮复制到 server-only 字段，供事件运行时重建输入。
        request.setServerPublishedButton(objectMapper.convertValue(
                button, MAP_OBJECT));
        log.info(
                "自定义表单按钮鉴权通过: formId={}, entityCode={}, buttonKey={}, mode={}, recordId={}, listKey={}",
                LogValue.safe(source.form().getId()),
                LogValue.safe(definition.getEntityCode()),
                LogValue.safe(buttonKey),
                LogValue.safe(mode),
                LogValue.safe(request.getRecordId()),
                LogValue.safe(request.getListKey()));
        return mode;
    }

    private RuntimeSource loadSource(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String resolutionToken) {
        if (!StringUtils.hasText(formId)) {
            throw new IllegalArgumentException("表单ID不能为空");
        }
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            throw new IllegalArgumentException("表单不存在");
        }
        boolean published = StringUtils.hasText(releaseId)
                || releaseVersion != null
                || StringUtils.hasText(resolutionToken)
                || StringUtils.hasText(form.getActiveReleaseId());
        if (published) {
            UiConfigReleaseService.ResolvedUiEventSnapshot resolved =
                    releaseService.resolveRuntimeEventSnapshot(
                            formId,
                            releaseId,
                            releaseVersion,
                            resolutionToken);
            Map<String, Object> snapshot = resolved.snapshot();
            EntityForm runtimeForm = objectMapper.convertValue(
                    snapshot.get("form"),
                    EntityForm.class);
            log.info(
                    "表单操作栏使用发布快照: formId={}, requestedReleaseId={}, requestedVersion={}, resolvedReleaseId={}, resolvedVersion={}, effectiveReleaseId={}, hotfixApplied={}",
                    LogValue.safe(formId),
                    LogValue.safe(releaseId),
                    releaseVersion,
                    LogValue.safe(resolved.releaseId()),
                    resolved.releaseVersion(),
                    LogValue.safe(resolved.effectiveReleaseId()),
                    resolved.hotfixApplied());
            return new RuntimeSource(
                    runtimeForm,
                    readViewConfig(runtimeForm.getViewConfig()),
                    snapshotNodes(snapshot),
                    snapshotBindings(snapshot),
                    resolved.releaseId(),
                    resolved.releaseVersion());
        }
        log.info(
                "表单操作栏使用草稿配置: formId={}, reason=NO_PUBLISHED_CONTEXT",
                LogValue.safe(formId));
        return new RuntimeSource(
                form,
                readViewConfig(form.getViewConfig()),
                formNodeMapper.findByFormId(formId),
                List.of(),
                null,
                null);
    }

    private long visibleCount(List<FormActionRuntimeDTO> actions) {
        return actions.stream()
                .filter(FormActionRuntimeDTO::isVisible)
                .count();
    }

    private long enabledCount(List<FormActionRuntimeDTO> actions) {
        return actions.stream()
                .filter(FormActionRuntimeDTO::isEnabled)
                .count();
    }

    private EntityDefinition requireDefinition(EntityForm form) {
        EntityDefinition definition =
                definitionMapper.selectById(form.getEntityId());
        if (definition == null) {
            throw new IllegalArgumentException("表单关联实体不存在");
        }
        return definition;
    }

    private void requireEntityCode(
            String requested,
            EntityDefinition definition) {
        if (StringUtils.hasText(requested)
                && !Objects.equals(
                        requested.trim().toLowerCase(Locale.ROOT),
                        definition.getEntityCode()
                                .toLowerCase(Locale.ROOT))) {
            throw new ForbiddenException(
                    "表单与实体上下文不一致");
        }
    }

    private EntityDataDTO loadRow(
            EntityDefinition definition,
            String recordId,
            String listKey) {
        if (!StringUtils.hasText(recordId)
                || definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            return null;
        }
        return dataService.findAccessibleById(
                definition.getEntityCode(),
                recordId,
                listKey);
    }

    private EntityActionCapabilityDTO builtInCapability(
            String key,
            EntityDefinition definition,
            String mode,
            EntityDataDTO row,
            Map<String, Object> button) {
        if ("close".equals(key) || "reset".equals(key)) {
            return evaluateOverrideRule(
                    definition.getEntityCode(),
                    button,
                    row);
        }
        if ("saveAndStart".equals(key)
                && (!workflowReady(definition)
                || (row != null && StringUtils.hasText(
                        row.getProcessInstanceId())))) {
            return EntityActionCapabilityDTO.hidden(
                    "当前数据不能发起流程");
        }
        if ("submitApproval".equals(key)) {
            // 打开审批表单不会抢占候选任务；提交按钮需使用可审批身份，并继续叠加发布覆盖条件。
            return capabilityService.evaluateApprovalAction(
                    definition.getEntityCode(), row, approvalRule(), readRule(button));
        }
        EntityPermissionAction action = switch (key) {
            case "save", "saveAndStart" ->
                    "create".equals(mode)
                            ? EntityPermissionAction.CREATE
                            : EntityPermissionAction.UPDATE;
            default -> null;
        };
        EntityActionCapabilityDTO standard =
                capabilityService.evaluateConfiguredAction(
                        definition.getEntityCode(),
                        action == null
                                ? null
                                : action.permissionCode(
                                        definition.getEntityCode()),
                        null,
                        row);
        if (!standard.isVisible() || !standard.isEnabled()) {
            return standard;
        }
        return evaluateOverrideRule(
                definition.getEntityCode(),
                button,
                row);
    }

    /**
     * 计算自定义按钮所属表单模式的服务端能力。
     *
     * <p>custom perm/availabilityRule 是附加约束，不能替代 CREATE/UPDATE/VIEW
     * 标准权限或审批待办校验。缺少编辑、查看、审批记录上下文时按禁用处理，避免
     * 解析接口先乐观返回 enabled，随后执行接口才拒绝。</p>
     */
    private EntityActionCapabilityDTO customModeCapability(
            EntityDefinition definition,
            String mode,
            EntityDataDTO row) {
        if ("create".equals(mode) && row != null) {
            return EntityActionCapabilityDTO.disabled(
                    "新增模式不能绑定已有记录");
        }
        if (!"create".equals(mode) && row == null) {
            return EntityActionCapabilityDTO.disabled(
                    "缺少可访问的记录上下文");
        }
        if ("approve".equals(mode)) {
            return capabilityService.evaluateApprovalAction(
                    definition.getEntityCode(),
                    row,
                    approvalRule(),
                    null);
        }
        EntityPermissionAction action = switch (mode) {
            case "create" -> EntityPermissionAction.CREATE;
            case "edit" -> EntityPermissionAction.UPDATE;
            case "view" -> EntityPermissionAction.VIEW;
            default -> null;
        };
        if (action == null) {
            return EntityActionCapabilityDTO.disabled(
                    "不支持的表单运行模式");
        }
        return capabilityService.evaluateConfiguredAction(
                definition.getEntityCode(),
                action.permissionCode(definition.getEntityCode()),
                null,
                row);
    }

    /** 取模式能力与按钮自定义能力的最严格交集。 */
    private EntityActionCapabilityDTO intersectCapabilities(
            EntityActionCapabilityDTO modeCapability,
            EntityActionCapabilityDTO configuredCapability) {
        if (modeCapability == null || configuredCapability == null) {
            return EntityActionCapabilityDTO.disabled(
                    modeCapability == null
                            ? "表单模式能力不可用"
                            : "按钮能力不可用");
        }
        // visible 的安全优先级高于 enabled：任一来源判定隐藏时，不能被
        // 另一来源较早产生的“可见但禁用”结果覆盖。
        if (!modeCapability.isVisible()) {
            return modeCapability;
        }
        if (!configuredCapability.isVisible()) {
            return configuredCapability;
        }
        if (!modeCapability.isEnabled()) {
            return modeCapability;
        }
        return configuredCapability;
    }

    private EntityActionCapabilityDTO evaluateOverrideRule(
            String entityCode,
            Map<String, Object> button,
            EntityDataDTO row) {
        EntityActionRuleDTO rule = readRule(button);
        if (rule == null) {
            return EntityActionCapabilityDTO.allowed();
        }
        return capabilityService.evaluateConfiguredAction(
                entityCode,
                null,
                rule,
                row);
    }

    private boolean workflowReady(EntityDefinition definition) {
        if (definition.getLifecycleMode()
                != EntityDefinition.LifecycleMode.WORKFLOW
                || !StringUtils.hasText(
                        definition.getProcessDefinitionId())) {
            return false;
        }
        ProcessCatalogItem process =
                processCatalogPort.findItemsByIds(
                                List.of(
                                        definition.getProcessDefinitionId()))
                        .get(definition.getProcessDefinitionId());
        return process != null
                && "PUBLISHED".equalsIgnoreCase(process.status());
    }

    private EntityActionRuleDTO approvalRule() {
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        EntityActionRuleDTO.RuleNode relation =
                new EntityActionRuleDTO.RuleNode();
        relation.setType("RELATION");
        relation.setRelation("CURRENT_USER_IS_ASSIGNEE");
        EntityActionRuleDTO.RuleNode process =
                new EntityActionRuleDTO.RuleNode();
        process.setType("PROCESS_STATE");
        process.setOperator("EQ");
        process.setValue("RUNNING");
        EntityActionRuleDTO.RuleNode root =
                new EntityActionRuleDTO.RuleNode();
        root.setType("GROUP");
        root.setLogic("AND");
        root.setChildren(List.of(relation, process));
        rule.setVisibleWhen(root);
        return rule;
    }

    private Map<String, Object> defaultBuiltIn(
            String key,
            String mode) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("key", key);
        button.put("type", "built-in");
        button.put("label", switch (key) {
            case "close" ->
                    Set.of("create", "edit").contains(mode)
                            ? "取消" : "关闭";
            case "reset" -> "重置";
            case "save" ->
                    "create".equals(mode)
                            ? "保存" : "保存修改";
            case "saveAndStart" -> "保存并发起流程";
            case "submitApproval" -> "提交审批";
            default -> key;
        });
        button.put("icon", switch (key) {
            case "reset" -> "RefreshLeft";
            case "save", "saveAndStart" -> "Check";
            case "submitApproval" -> "Select";
            default -> "";
        });
        button.put("buttonType",
                Set.of("save", "saveAndStart", "submitApproval")
                        .contains(key)
                        ? "primary" : "default");
        button.put("sort", switch (key) {
            case "close" -> 10;
            case "reset" -> 20;
            case "save" -> 30;
            default -> 40;
        });
        button.put("enabled", true);
        button.put("enabledModes",
                new ArrayList<>(BUILT_IN_MODES.get(key)));
        button.put("placement", "FOOTER");
        button.put("validateBeforeExecute",
                Set.of("save", "saveAndStart", "submitApproval")
                        .contains(key));
        return button;
    }

    private Map<String, Object> applyOverride(
            Map<String, Object> base,
            Map<String, Object> override,
            String mode) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (String key : List.of(
                "enabled", "icon", "buttonType", "sort",
                "enabledModes", "availabilityRule")) {
            if (override.containsKey(key)) {
                result.put(key, override.get(key));
            }
        }
        Map<String, Object> labels =
                mapOrEmpty(override.get("labelByMode"));
        if (labels.containsKey(mode)
                && StringUtils.hasText(text(labels.get(mode)))) {
            result.put("label", text(labels.get(mode)));
        }
        return result;
    }

    private boolean enabledForMode(
            Map<String, Object> button,
            String mode) {
        return !Boolean.FALSE.equals(button.get("enabled"))
                && modes(button).contains(mode);
    }

    private Map<String, Object> normalizeCustom(
            Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("type", "custom");
        result.putIfAbsent("buttonType", "default");
        result.putIfAbsent("buttonAppearance", "DEFAULT");
        result.putIfAbsent("sort", 50);
        result.putIfAbsent("placement", "FOOTER");
        result.putIfAbsent("validateBeforeExecute", false);
        return result;
    }

    private FormActionRuntimeDTO toRuntime(
            String formId,
            Map<String, Object> button,
            EntityActionCapabilityDTO capability,
            boolean custom) {
        FormActionRuntimeDTO dto = new FormActionRuntimeDTO();
        String key = text(button.get("key"));
        dto.setOwnerFormId(formId);
        dto.setRuntimeKey(custom ? formId + ":" + key : key);
        dto.setKey(key);
        dto.setType(custom ? "custom" : "built-in");
        dto.setLabel(text(button.get("label")));
        dto.setIcon(text(button.get("icon")));
        dto.setButtonType(firstText(
                button.get("buttonType"), "default"));
        dto.setButtonAppearance(runtimeButtonAppearance(button, custom));
        dto.setSort(number(button.get("sort"), 0));
        dto.setPlacement(firstText(
                button.get("placement"), "FOOTER")
                .toUpperCase(Locale.ROOT));
        dto.setSlotKey(text(button.get("slotKey")));
        dto.setVisible(capability.isVisible());
        dto.setEnabled(capability.isEnabled());
        dto.setReason(capability.getReason());
        dto.setConfirm(mapOrNull(button.get("confirm")));
        dto.setValidateBeforeExecute(
                Boolean.TRUE.equals(
                        button.get("validateBeforeExecute")));
        return dto;
    }

    /**
     * 只向自定义按钮暴露外观配置，并为旧发布快照或异常值提供安全默认值。
     */
    private String runtimeButtonAppearance(
            Map<String, Object> button,
            boolean custom) {
        if (!custom) {
            return "DEFAULT";
        }
        String normalized = firstText(
                button.get("buttonAppearance"), "DEFAULT")
                .toUpperCase(Locale.ROOT);
        if (!Set.of("DEFAULT", "PLAIN", "ROUND", "CIRCLE")
                .contains(normalized)) {
            return "DEFAULT";
        }
        // 历史快照可能在该字段受校验前写入过不完整配置，
        // 运行态不能输出不可渲染的圆按钮。
        return "CIRCLE".equals(normalized)
                && !EntityFormActionConfigPolicy.isSupportedButtonIcon(
                        button.get("icon"))
                ? "DEFAULT"
                : normalized;
    }

    /**
     * 将客户端模式声明约束到服务端可验证的记录和标准动作权限。
     *
     * <p>新增态严格由 recordId 缺失推导；编辑/查看必须具备对应标准权限；审批
     * 还必须命中当前用户真实可办理任务。这样 modes 只负责展示适用性，不能单独
     * 成为提权依据。</p>
     */
    private String authorizedRequestMode(
            UiEventExecuteRequest request,
            RuntimeSource source,
            EntityDefinition definition,
            EntityDataDTO row) {
        Object contextMode = request.getContext() == null
                ? null : request.getContext().get("mode");
        Object inputMode = request.getInput() == null
                ? null : request.getInput().get("mode");
        if (contextMode != null && inputMode != null
                && !Objects.equals(
                        normalizeMode(text(contextMode)),
                        normalizeMode(text(inputMode)))) {
            throw new ForbiddenException(
                    "表单运行模式声明不一致");
        }
        String mode = requireMode(text(
                contextMode != null ? contextMode : inputMode));
        boolean create = !StringUtils.hasText(request.getRecordId());
        if (create != "create".equals(mode)) {
            throw new ForbiddenException(
                    "表单运行模式与记录上下文不一致");
        }
        switch (mode) {
            case "create" -> capabilityService.requireStandardPermission(
                    definition.getEntityCode(),
                    EntityPermissionAction.CREATE);
            case "edit" -> capabilityService.requireStandardPermission(
                    definition.getEntityCode(),
                    EntityPermissionAction.UPDATE);
            case "view" -> capabilityService.requireStandardPermission(
                    definition.getEntityCode(),
                    EntityPermissionAction.VIEW);
            case "approve" -> {
                EntityActionCapabilityDTO approval =
                        capabilityService.evaluateApprovalAction(
                                definition.getEntityCode(),
                                row,
                                approvalRule(),
                                null);
                if (approval == null
                        || !approval.isVisible()
                        || !approval.isEnabled()) {
                    throw new ForbiddenException(
                            approval == null
                                    || !StringUtils.hasText(
                                    approval.getReason())
                                    ? "当前用户没有可办理的审批任务"
                                    : approval.getReason());
                }
                ActionableTaskContext task = requireApprovalTaskBinding(
                        request.getTaskId(),
                        request.getReleaseResolutionToken(),
                        source, definition, row, approval);
                // 只有服务端已联合校验的任务/实例身份可以继续传入事件链。
                request.setServerTaskId(task.taskId());
                request.setServerProcessInstanceId(
                        task.processInstanceId());
            }
            default -> throw new ForbiddenException(
                    "不支持的表单运行模式");
        }
        return mode;
    }

    /**
     * 将审批模式绑定到当前用户真实待办、业务记录和流程发布版。
     *
     * <p>taskId 是公开请求坐标，只有与能力查询结果一致并经流程端口联合回查后
     * 才可信；发布令牌必须为该任务的 ACTIVE_TASK 历史/节点，不能省略后回退
     * 当前 ACTIVE，也不能用 NEW_INSTANCE 或另一流程版本的令牌替代。</p>
     */
    private ActionableTaskContext requireApprovalTaskBinding(
            String requestedTaskId,
            String releaseResolutionToken,
            RuntimeSource source,
            EntityDefinition definition,
            EntityDataDTO row,
            EntityActionCapabilityDTO approval) {
        if (row == null
                || !StringUtils.hasText(requestedTaskId)
                || approval == null
                || !approval.isVisible()
                || !approval.isEnabled()) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_APPROVAL_TASK_CONTEXT_MISMATCH",
                    "审批表单按钮与当前可办理任务不一致");
        }
        ActionableTaskContext task = capabilityService
                .findActionableApprovalTaskContext(
                        row, requestedTaskId)
                .orElseThrow(() -> new BusinessForbiddenException(
                        "UI_EVENT_APPROVAL_TASK_CONTEXT_MISMATCH",
                        "审批表单按钮与当前可办理任务不一致"));
        if (!Objects.equals(row.getId(), task.entityDataId())
                || !Objects.equals(
                        row.getProcessInstanceId(),
                        task.processInstanceId())
                || !Objects.equals(
                        definition.getEntityCode(),
                        task.entityCode())) {
            throw new BusinessForbiddenException(
                    "UI_EVENT_APPROVAL_TASK_CONTEXT_MISMATCH",
                    "审批表单按钮与当前业务记录不一致");
        }
        releaseService.requireActiveTaskReleaseToken(
                releaseResolutionToken,
                source.form().getId(),
                source.releaseId(),
                source.releaseVersion(),
                task.processVersionHistoryId(),
                task.nodeId(),
                task.taskId(),
                task.processInstanceId(),
                task.entityCode(),
                task.entityDataId());
        return task;
    }

    private String normalizeMode(String mode) {
        return StringUtils.hasText(mode)
                ? mode.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String requireMode(String mode) {
        String normalized = text(mode)
                .toLowerCase(Locale.ROOT);
        if (!EntityFormActionConfigPolicy.MODES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "不支持的表单运行模式: " + mode);
        }
        return normalized;
    }

    private EntityActionRuleDTO readRule(
            Map<String, Object> button) {
        Object raw = button.get("availabilityRule");
        if (raw == null) {
            return null;
        }
        // 运行时仍对发布制品做失败关闭校验，避免绕过写入入口的旧版或畸形规则。
        configPolicy.validateAvailabilityRule(raw);
        return objectMapper.convertValue(
                EntityActionRuleStructurePolicy.normalizeAndValidate(raw),
                EntityActionRuleDTO.class);
    }

    private Set<String> modes(Map<String, Object> button) {
        Object raw = button.containsKey("modes")
                ? button.get("modes")
                : button.get("enabledModes");
        if (!(raw instanceof List<?> values)) {
            String key = text(button.get("key"));
            Set<String> builtInModes = BUILT_IN_MODES.get(key);
            if (builtInModes != null) {
                return builtInModes;
            }
            // 历史自定义按钮可能早于 modes 字段；与前端兼容规则一致，
            // 缺失时仅开放 edit，不能扩大到 create/view/approve。
            return Set.of("edit");
        }
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(
                text(value).toLowerCase(Locale.ROOT)));
        return result;
    }

    private Map<String, Object> readViewConfig(String document) {
        return StringUtils.hasText(document)
                ? codec.readObject(document, "表单视图配置")
                : Map.of();
    }

    private List<EntityFormNode> snapshotNodes(
            Map<String, Object> snapshot) {
        return objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {});
    }

    private List<Map<String, Object>> snapshotBindings(
            Map<String, Object> snapshot) {
        return objectMapper.convertValue(
                snapshot.getOrDefault("eventBindings", List.of()),
                MAP_LIST);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrEmpty(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> mapOrNull(Object value) {
        Map<String, Object> result = mapOrEmpty(value);
        return result.isEmpty() ? null : result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            result.add(mapOrEmpty(item));
        }
        return result;
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null
                    ? fallback
                    : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String firstText(Object value, String fallback) {
        String result = text(value);
        return StringUtils.hasText(result) ? result : fallback;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record RuntimeSource(
            EntityForm form,
            Map<String, Object> viewConfig,
            List<EntityFormNode> nodes,
            List<Map<String, Object>> bindings,
            String releaseId,
            Integer releaseVersion) {
    }
}
