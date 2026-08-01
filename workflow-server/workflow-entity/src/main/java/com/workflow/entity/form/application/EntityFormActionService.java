package com.workflow.entity.form.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.ProcessCatalogItem;
import com.workflow.contracts.process.ProcessCatalogPort;
import com.workflow.core.error.ForbiddenException;
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
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class EntityFormActionService {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST =
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
        Map<String, Object> actionBar =
                configPolicy.actionBar(source.viewConfig());
        Map<String, Object> overrides =
                mapOrEmpty(actionBar.get("builtInOverrides"));

        List<FormActionRuntimeDTO> result = new ArrayList<>();
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            result.add(toRuntime(
                    source.form().getId(),
                    applyOverride(
                            defaultBuiltIn("close", mode),
                            mapOrEmpty(overrides.get("close")),
                            mode),
                    EntityActionCapabilityDTO.allowed(),
                    false));
            return result;
        }

        for (String key : List.of(
                "close", "reset", "save",
                "saveAndStart", "submitApproval")) {
            if (!BUILT_IN_MODES.get(key).contains(mode)) {
                continue;
            }
            Map<String, Object> button = applyOverride(
                    defaultBuiltIn(key, mode),
                    mapOrEmpty(overrides.get(key)),
                    mode);
            if (!enabledForMode(button, mode)) {
                continue;
            }
            EntityActionCapabilityDTO capability =
                    builtInCapability(
                            key,
                            definition,
                            mode,
                            row,
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
                    || !modes(button).contains(mode)) {
                continue;
            }
            String permission = text(button.get("perm"));
            EntityActionCapabilityDTO capability =
                    capabilityService.evaluateConfiguredAction(
                            definition.getEntityCode(),
                            permission,
                            readRule(button),
                            row);
            result.add(toRuntime(
                    source.form().getId(),
                    normalizeCustom(button),
                    capability,
                    true));
        }
        result.sort(Comparator.comparingInt(
                item -> item.getSort() == null
                        ? 0 : item.getSort()));
        return result;
    }

    /**
     * FORM_BUTTON_CLICK 服务端执行前的最终鉴权。
     */
    public void requireCustomButton(UiEventExecuteRequest request) {
        RuntimeSource source = loadSource(
                request.getConfigId(),
                request.getReleaseId(),
                request.getReleaseVersion(),
                request.getReleaseResolutionToken());
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
        String mode = requestMode(request);
        if (!modes(button).contains(mode)) {
            throw new ForbiddenException(
                    "当前表单模式不能执行该按钮");
        }
        EntityDataDTO row = loadRow(
                definition,
                request.getRecordId(),
                request.getListKey());
        capabilityService.requireCustomAction(
                definition.getEntityCode(),
                buttonKey,
                text(button.get("perm")),
                readRule(button),
                row);
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
            return new RuntimeSource(
                    runtimeForm,
                    readViewConfig(runtimeForm.getViewConfig()),
                    snapshotNodes(snapshot),
                    snapshotBindings(snapshot));
        }
        return new RuntimeSource(
                form,
                readViewConfig(form.getViewConfig()),
                formNodeMapper.findByFormId(formId),
                List.of());
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
        EntityPermissionAction action = switch (key) {
            case "save", "saveAndStart" ->
                    "create".equals(mode)
                            ? EntityPermissionAction.CREATE
                            : EntityPermissionAction.UPDATE;
            case "submitApproval" ->
                    EntityPermissionAction.APPROVE;
            default -> null;
        };
        EntityActionCapabilityDTO standard =
                capabilityService.evaluateConfiguredAction(
                        definition.getEntityCode(),
                        action == null
                                ? null
                                : action.permissionCode(
                                        definition.getEntityCode()),
                        "submitApproval".equals(key)
                                ? approvalRule()
                                : null,
                        row);
        if (!standard.isVisible() || !standard.isEnabled()) {
            return standard;
        }
        return evaluateOverrideRule(
                definition.getEntityCode(),
                button,
                row);
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
        rule.setUnavailableBehavior("HIDE");
        rule.setMessage("仅当前任务办理人可以提交审批");
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
        rule.setRoot(root);
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

    private String requestMode(UiEventExecuteRequest request) {
        Object mode = request.getContext() == null
                ? null : request.getContext().get("mode");
        if (mode == null && request.getInput() != null) {
            mode = request.getInput().get("mode");
        }
        return requireMode(text(mode));
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
        return raw == null
                ? null
                : objectMapper.convertValue(
                        raw,
                        EntityActionRuleDTO.class);
    }

    private Set<String> modes(Map<String, Object> button) {
        Object raw = button.containsKey("modes")
                ? button.get("modes")
                : button.get("enabledModes");
        if (!(raw instanceof List<?> values)) {
            String key = text(button.get("key"));
            return BUILT_IN_MODES.getOrDefault(key, Set.of());
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
            List<Map<String, Object>> bindings) {
    }
}
