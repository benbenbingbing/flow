package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import com.workflow.contracts.ui.UiDataSourceUsages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
 * 统一 UI 事件绑定目录与继承解析服务。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiEventBindingService {

    public static final Set<String> OWNER_TYPES =
            Set.of("ENTITY", "FORM", "LIST");
    public static final Set<String> TARGET_TYPES =
            Set.of("OWNER", "FIELD", "BUTTON");
    public static final Set<String> EVENTS = Set.of(
            UiDataSourceUsages.LIST_LOAD,
            UiDataSourceUsages.LIST_EXPORT,
            UiDataSourceUsages.DETAIL_LOAD,
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.DATA_DELETE,
            UiDataSourceUsages.DATA_BATCH_DELETE,
            UiDataSourceUsages.FORM_OPEN,
            UiDataSourceUsages.FORM_SAVE,
            UiDataSourceUsages.FORM_RESET,
            UiDataSourceUsages.FIELD_CHANGE,
            UiDataSourceUsages.ENTITY_SELECTED,
            UiDataSourceUsages.FIELD_BUTTON_CLICK,
            UiDataSourceUsages.SUBFORM_LOAD,
            UiDataSourceUsages.SUBFORM_SAVE,
            UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
            UiDataSourceUsages.ROW_BUTTON_CLICK,
            UiDataSourceUsages.FORM_BUTTON_CLICK);
    private static final Set<String> FORM_EVENTS = Set.of(
            UiDataSourceUsages.DETAIL_LOAD,
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.FORM_OPEN,
            UiDataSourceUsages.FORM_SAVE,
            UiDataSourceUsages.FORM_RESET,
            UiDataSourceUsages.FIELD_CHANGE,
            UiDataSourceUsages.ENTITY_SELECTED,
            UiDataSourceUsages.FIELD_BUTTON_CLICK,
            UiDataSourceUsages.SUBFORM_LOAD,
            UiDataSourceUsages.SUBFORM_SAVE,
            UiDataSourceUsages.FORM_BUTTON_CLICK);
    private static final Set<String> LIST_EVENTS = Set.of(
            UiDataSourceUsages.LIST_LOAD,
            UiDataSourceUsages.LIST_EXPORT,
            UiDataSourceUsages.DETAIL_LOAD,
            UiDataSourceUsages.DATA_CREATE,
            UiDataSourceUsages.DATA_UPDATE,
            UiDataSourceUsages.DATA_DELETE,
            UiDataSourceUsages.DATA_BATCH_DELETE,
            UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
            UiDataSourceUsages.ROW_BUTTON_CLICK);
    private static final Set<String> FORM_FIELD_EVENTS = Set.of(
            UiDataSourceUsages.FIELD_CHANGE,
            UiDataSourceUsages.ENTITY_SELECTED,
            UiDataSourceUsages.FIELD_BUTTON_CLICK,
            UiDataSourceUsages.SUBFORM_LOAD,
            UiDataSourceUsages.SUBFORM_SAVE);
    private static final Set<String> FORM_BUTTON_EVENTS =
            Set.of(UiDataSourceUsages.FORM_BUTTON_CLICK);
    private static final Set<String> LIST_BUTTON_EVENTS = Set.of(
            UiDataSourceUsages.TOOLBAR_BUTTON_CLICK,
            UiDataSourceUsages.ROW_BUTTON_CLICK);

    /**
     * 事件按配置来源和精确目标分域。OWNER 保留同类目标事件，用于为当前
     * 表单全部字段或按钮、当前列表全部按钮配置一层公共默认绑定。
     */
    private static final Map<String, Map<String, Set<String>>> EVENT_SCOPES =
            Map.of(
                    "ENTITY", Map.of(
                            "OWNER", EVENTS),
                    "FORM", Map.of(
                            "OWNER", FORM_EVENTS,
                            "FIELD", FORM_FIELD_EVENTS,
                            "BUTTON", FORM_BUTTON_EVENTS),
                    "LIST", Map.of(
                            "OWNER", LIST_EVENTS,
                            "BUTTON", LIST_BUTTON_EVENTS));
    private static final Set<String> INHERITANCE_MODES =
            Set.of("INHERIT", "REPLACE", "DISABLE");
    private static final Set<String> STRATEGIES =
            Set.of("BEFORE", "REPLACE", "AFTER");
    private static final Set<String> FAILURE_POLICIES =
            Set.of("STOP", "CONTINUE", "EMPTY");
    private static final Set<String> SYSTEM_READ_ONLY_EVENTS =
            Set.of(
                    UiDataSourceUsages.LIST_LOAD,
                    UiDataSourceUsages.DETAIL_LOAD,
                    UiDataSourceUsages.FORM_OPEN,
                    UiDataSourceUsages.FIELD_CHANGE,
                    UiDataSourceUsages.ENTITY_SELECTED,
                    UiDataSourceUsages.SUBFORM_LOAD);

    private final UiEventBindingMapper mapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final UiConfigurationAccessService configurationAccessService;
    private final UiDataSourceService dataSourceService;
    private final UiEventBindingSnapshotService eventBindingSnapshotService;
    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    public List<UiEventBinding> list(
            String ownerType,
            String ownerId) {
        String normalizedOwner = normalize(ownerType);
        requireOwner(normalizedOwner, ownerId);
        requireOwnerAccess(normalizedOwner, ownerId);
        return mapper.findByOwner(normalizedOwner, ownerId);
    }

    public Map<String, Object> catalog() {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("ownerTypes", OWNER_TYPES);
        catalog.put("targetTypes", TARGET_TYPES);
        catalog.put("events", EVENTS);
        catalog.put("eventScopes", EVENT_SCOPES);
        catalog.put("inheritanceModes", INHERITANCE_MODES);
        catalog.put("strategies", STRATEGIES);
        catalog.put("failurePolicies", FAILURE_POLICIES);
        return catalog;
    }

    public Map<String, Object> resolveDraft(
            String ownerType,
            String ownerId,
            String eventCode) {
        String normalizedOwner = normalize(ownerType);
        String normalizedEvent = normalize(eventCode);
        requireOwner(normalizedOwner, ownerId);
        requireOwnerAccess(normalizedOwner, ownerId);
        if (!Set.of("FORM", "LIST").contains(normalizedOwner)) {
            throw new IllegalArgumentException(
                    "草稿事件解析只支持 FORM 或 LIST");
        }
        if (!EVENTS.contains(normalizedEvent)) {
            throw new IllegalArgumentException(
                    "不支持的事件编码: " + eventCode);
        }
        validateEventScope(normalizedOwner, "OWNER", normalizedEvent);
        ConfigIdentity identity =
                identity(normalizedOwner, ownerId);
        // 草稿预览必须与正式发布使用同一 FORM/LIST 投影，避免共享 ENTITY
        // 事件把另一页面上下文的接口步骤混入当前预览执行链。
        List<Map<String, Object>> bindings =
                eventBindingSnapshotService.snapshot(
                        normalizedOwner,
                        ownerId,
                        identity.entityId());
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType(normalizedOwner);
        request.setConfigId(ownerId);
        request.setEventCode(normalizedEvent);
        request.setTargetType("OWNER");
        ResolvedEventChain chain = resolve(
                bindings,
                identity,
                request,
                null,
                null,
                Map.of());
        Map<String, Object> local = findBinding(
                bindings,
                normalizedOwner,
                ownerId,
                "OWNER",
                null,
                normalizedEvent);
        Map<String, Object> inherited = findBinding(
                bindings,
                "ENTITY",
                identity.entityId(),
                "OWNER",
                null,
                normalizedEvent);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "source",
                local != null
                        ? "LOCAL"
                        : inherited != null
                        ? "INHERITED"
                        : "PLATFORM");
        result.put("steps", chain.steps());
        result.put("localBinding",
                local == null ? Map.of() : local);
        result.put("hasReplace", chain.steps().stream()
                .anyMatch(step -> "REPLACE".equals(
                        normalize(text(step.get("strategy"))))));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public UiEventBinding save(UiEventBindingSaveRequest request) {
        validate(request);
        String ownerType = normalize(request.getOwnerType());
        String targetType = normalize(
                StringUtils.hasText(request.getTargetType())
                        ? request.getTargetType() : "OWNER");
        String eventCode = normalize(request.getEventCode());
        requireOwnerAccess(ownerType, request.getOwnerId());
        validateSystemReadOnlyEvent(
                ownerType,
                request.getOwnerId(),
                eventCode,
                request.getSteps());
        UiEventBinding current = StringUtils.hasText(request.getId())
                ? mapper.selectById(request.getId())
                : findExisting(
                        ownerType,
                        request.getOwnerId(),
                        targetType,
                        normalizedTargetKey(request.getTargetKey()),
                        eventCode);
        if (current != null) {
            requireRevision(request.getExpectedRevision(), current);
        }
        UiEventBinding value =
                current == null ? new UiEventBinding() : current;
        value.setOwnerType(ownerType);
        value.setOwnerId(request.getOwnerId().trim());
        value.setTargetType(targetType);
        value.setTargetKey(normalizedTargetKey(request.getTargetKey()));
        value.setEventCode(eventCode);
        value.setInheritanceMode(normalize(
                StringUtils.hasText(request.getInheritanceMode())
                        ? request.getInheritanceMode() : "INHERIT"));
        value.setStepsDocument(writeSteps(request.getSteps()));
        value.setEnabled(request.getEnabled() == null || request.getEnabled());
        value.setDeleted(0);
        value.setUpdatedAt(LocalDateTime.now());
        if (current == null) {
            value.setRevision(1);
            value.setCreatedAt(LocalDateTime.now());
            mapper.insert(value);
        } else {
            int currentRevision = current.getRevision();
            value.setRevision(currentRevision + 1);
            UpdateWrapper<UiEventBinding> update = new UpdateWrapper<>();
            update.eq("id", current.getId())
                    .eq("revision", currentRevision)
                    .eq("deleted", 0)
                    .set("owner_type", value.getOwnerType())
                    .set("owner_id", value.getOwnerId())
                    .set("target_type", value.getTargetType())
                    .set("target_key", value.getTargetKey())
                    .set("event_code", value.getEventCode())
                    .set("inheritance_mode", value.getInheritanceMode())
                    .set("steps_document", value.getStepsDocument())
                    .set("enabled", value.getEnabled())
                    .set("revision", value.getRevision())
                    .set("update_time", value.getUpdatedAt());
            if (mapper.update(null, update) != 1) {
                throw new RevisionConflictException(
                        "事件绑定已被其他人修改，请刷新后重试",
                        mapper.selectById(current.getId()));
            }
        }
        return mapper.selectById(value.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(
            String id,
            Integer expectedRevision) {
        UiEventBinding current = mapper.selectById(id);
        if (current == null) {
            throw new IllegalArgumentException("事件绑定不存在");
        }
        requireOwnerAccess(current.getOwnerType(), current.getOwnerId());
        requireRevision(expectedRevision, current);
        UpdateWrapper<UiEventBinding> update = new UpdateWrapper<>();
        update.eq("id", id)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("deleted", 1)
                .setSql("revision = revision + 1")
                .set("update_time", LocalDateTime.now());
        if (mapper.update(null, update) != 1) {
            throw new RevisionConflictException(
                    "事件绑定已被其他人修改，请刷新后重试",
                    mapper.selectById(id));
        }
    }

    /**
     * 构建表单或列表发布快照中的事件绑定部分。
     */
    public List<Map<String, Object>> snapshotBindings(
            String configType,
            String configId,
            String entityId) {
        return eventBindingSnapshotService.snapshot(
                normalize(configType), configId, entityId);
    }

    /**
     * 从激活发布快照解析最终事件链。
     */
    public ResolvedEventChain resolvePublished(
            UiEventExecuteRequest request) {
        String configType = normalize(request.getConfigType());
        log.info(
                "开始解析UI事件链: configType={}, configId={}, releaseId={}, releaseVersion={}, tokenPresent={}, eventCode={}, targetType={}, targetKey={}",
                LogValue.safe(configType),
                LogValue.safe(request.getConfigId()),
                LogValue.safe(request.getReleaseId()),
                request.getReleaseVersion(),
                StringUtils.hasText(
                        request.getReleaseResolutionToken()),
                LogValue.safe(request.getEventCode()),
                LogValue.safe(request.getTargetType()),
                LogValue.safe(request.getTargetKey()));
        if (!Set.of("FORM", "LIST").contains(configType)
                || !StringUtils.hasText(request.getConfigId())) {
            throw new IllegalArgumentException(
                    "事件运行时必须声明 FORM/LIST 配置来源");
        }
        String targetType = normalize(
                StringUtils.hasText(request.getTargetType())
                        ? request.getTargetType() : "OWNER");
        String eventCode = normalize(request.getEventCode());
        if (!TARGET_TYPES.contains(targetType)) {
            throw new IllegalArgumentException(
                    "不支持的事件目标类型: "
                            + request.getTargetType());
        }
        if (!EVENTS.contains(eventCode)) {
            throw new IllegalArgumentException(
                    "不支持的 UI 事件: "
                            + request.getEventCode());
        }
        if (!"OWNER".equals(targetType)
                && !StringUtils.hasText(request.getTargetKey())) {
            throw new IllegalArgumentException(
                    "字段或按钮事件必须指定稳定 targetKey");
        }
        validateEventScope(configType, targetType, eventCode);
        if ("FORM".equals(configType)) {
            UiConfigReleaseService.ResolvedUiEventSnapshot resolved =
                    releaseService.resolveRuntimeEventSnapshot(
                            request.getConfigId(),
                            request.getReleaseId(),
                            request.getReleaseVersion(),
                            request.getReleaseResolutionToken());
            Map<String, Object> snapshot = resolved.snapshot();
            ConfigIdentity identity = identity(
                    configType,
                    request.getConfigId());
            ResolvedEventChain chain = resolve(
                    mapList(snapshot.get("eventBindings")),
                    identity,
                    request,
                    resolved.releaseId(),
                    resolved.releaseVersion(),
                    snapshot);
            logResolvedChain(request, chain, "FORM_RELEASE");
            return chain;
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveRuntimeListRelease(
                        request.getConfigId(),
                        request.getReleaseId(),
                        request.getReleaseVersion(),
                        request.getReleaseResolutionToken());
        Map<String, Object> snapshot = resolved.snapshot();
        List<Map<String, Object>> bindings =
                mapList(snapshot.get("eventBindings"));
        ConfigIdentity identity = identity(
                configType,
                request.getConfigId());
        ResolvedEventChain chain = resolve(
                bindings,
                identity,
                request,
                resolved.releaseId(),
                resolved.releaseVersion(),
                snapshot);
        logResolvedChain(
                request,
                chain,
                resolved.pinned()
                        ? "SIGNED_LIST_CONTEXT"
                        : "ACTIVE_RELEASE");
        return chain;
    }

    private void logResolvedChain(
            UiEventExecuteRequest request,
            ResolvedEventChain chain,
            String source) {
        long beforeCount = chain.steps().stream()
                .filter(step -> "BEFORE".equals(
                        normalize(text(step.get("strategy")))))
                .count();
        long replaceCount = chain.steps().stream()
                .filter(step -> "REPLACE".equals(
                        normalize(text(step.get("strategy")))))
                .count();
        long afterCount = chain.steps().stream()
                .filter(step -> "AFTER".equals(
                        normalize(text(step.get("strategy")))))
                .count();
        log.info(
                "UI事件链解析完成: configType={}, configId={}, releaseId={}, releaseVersion={}, eventCode={}, targetType={}, targetKey={}, stepCount={}, beforeCount={}, replaceCount={}, afterCount={}, entityCode={}, listKey={}, source={}",
                LogValue.safe(request.getConfigType()),
                LogValue.safe(request.getConfigId()),
                LogValue.safe(chain.releaseId()),
                chain.releaseVersion(),
                LogValue.safe(request.getEventCode()),
                LogValue.safe(request.getTargetType()),
                LogValue.safe(request.getTargetKey()),
                chain.steps().size(),
                beforeCount,
                replaceCount,
                afterCount,
                LogValue.safe(chain.entityCode()),
                LogValue.safe(chain.listKey()),
                LogValue.safe(source));
    }

    private ResolvedEventChain emptyChain(
            String configType,
            UiEventExecuteRequest request) {
        ConfigIdentity identity = identity(
                configType,
                request.getConfigId());
        return new ResolvedEventChain(
                List.of(),
                null,
                null,
                identity.entityId(),
                identity.entityCode(),
                identity.listKey(),
                Map.of());
    }

    private ResolvedEventChain resolve(
            List<Map<String, Object>> bindings,
            ConfigIdentity identity,
            UiEventExecuteRequest request,
            String releaseId,
            Integer releaseVersion,
            Map<String, Object> snapshot) {
        String eventCode = normalize(request.getEventCode());
        if (!EVENTS.contains(eventCode)) {
            throw new IllegalArgumentException(
                    "不支持的 UI 事件: " + request.getEventCode());
        }
        List<Map<String, Object>> effective = new ArrayList<>();
        applyLevel(
                effective,
                findBinding(
                        bindings,
                        "ENTITY",
                        identity.entityId(),
                        "OWNER",
                        null,
                        eventCode));
        applyLevel(
                effective,
                findBinding(
                        bindings,
                        normalize(request.getConfigType()),
                        request.getConfigId(),
                        "OWNER",
                        null,
                        eventCode));
        if (StringUtils.hasText(request.getTargetType())
                && !"OWNER".equals(normalize(request.getTargetType()))) {
            applyLevel(
                    effective,
                    findBinding(
                            bindings,
                            normalize(request.getConfigType()),
                            request.getConfigId(),
                            normalize(request.getTargetType()),
                        normalizedTargetKey(request.getTargetKey()),
                            eventCode));
        }
        effective.sort(Comparator.comparingInt(
                step -> integer(step.get("order"), 0)));
        long replacements = effective.stream()
                .filter(step -> "REPLACE".equals(
                        normalize(text(step.get("strategy")))))
                .count();
        if (replacements > 1) {
            throw new BusinessConflictException(
                    "UI_EVENT_MULTIPLE_REPLACE",
                    "同一事件的有效执行链最多只能包含一个 REPLACE 步骤");
        }
        return new ResolvedEventChain(
                List.copyOf(effective),
                releaseId,
                releaseVersion,
                identity.entityId(),
                identity.entityCode(),
                identity.listKey(),
                snapshot == null ? Map.of() : Map.copyOf(snapshot));
    }

    private void applyLevel(
            List<Map<String, Object>> effective,
            Map<String, Object> binding) {
        if (binding == null || binding.isEmpty()) {
            return;
        }
        String mode = normalize(text(
                binding.getOrDefault("inheritanceMode", "INHERIT")));
        if ("DISABLE".equals(mode)) {
            effective.clear();
            return;
        }
        if ("REPLACE".equals(mode)) {
            effective.clear();
        }
        effective.addAll(mapList(binding.get("steps")));
    }

    private Map<String, Object> findBinding(
            List<Map<String, Object>> bindings,
            String ownerType,
            String ownerId,
            String targetType,
            String targetKey,
            String eventCode) {
        return bindings.stream()
                .filter(item -> Objects.equals(
                        ownerType, normalize(text(item.get("ownerType")))))
                .filter(item -> Objects.equals(
                        ownerId, text(item.get("ownerId"))))
                .filter(item -> Objects.equals(
                        targetType, normalize(text(item.get("targetType")))))
                .filter(item -> Objects.equals(
                        normalizedTargetKey(targetKey),
                        normalizedTargetKey(text(item.get("targetKey")))))
                .filter(item -> Objects.equals(
                        eventCode, normalize(text(item.get("eventCode")))))
                .findFirst()
                .orElse(null);
    }

    private void validate(UiEventBindingSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("事件绑定不能为空");
        }
        String ownerType = normalize(request.getOwnerType());
        String targetType = normalize(
                StringUtils.hasText(request.getTargetType())
                        ? request.getTargetType() : "OWNER");
        String eventCode = normalize(request.getEventCode());
        String inheritance = normalize(
                StringUtils.hasText(request.getInheritanceMode())
                        ? request.getInheritanceMode() : "INHERIT");
        requireOwner(ownerType, request.getOwnerId());
        if (!TARGET_TYPES.contains(targetType)) {
            throw new IllegalArgumentException(
                    "不支持的事件目标类型: " + request.getTargetType());
        }
        if (!"OWNER".equals(targetType)
                && !StringUtils.hasText(request.getTargetKey())) {
            throw new IllegalArgumentException(
                    "字段或按钮事件必须指定稳定 targetKey");
        }
        if (!EVENTS.contains(eventCode)) {
            throw new IllegalArgumentException(
                    "不支持的事件编码: " + request.getEventCode());
        }
        validateEventScope(ownerType, targetType, eventCode);
        if (!INHERITANCE_MODES.contains(inheritance)) {
            throw new IllegalArgumentException(
                    "不支持的继承模式: " + request.getInheritanceMode());
        }
        List<Map<String, Object>> steps = request.getSteps() == null
                ? List.of() : request.getSteps();
        Set<Integer> orders = new LinkedHashSet<>();
        int replaceCount = 0;
        Map<String, Integer> entityReplaceCounts = new LinkedHashMap<>();
        Set<String> entityEventContexts = "ENTITY".equals(ownerType)
                ? UiEventBindingApplicability.contextsForEvent(eventCode)
                : Set.of();
        for (int index = 0; index < steps.size(); index++) {
            Map<String, Object> step = steps.get(index);
            String strategy = normalize(text(
                    step.getOrDefault("strategy", "BEFORE")));
            String failure = normalize(text(
                    step.getOrDefault("failurePolicy", "STOP")));
            int order = integer(step.get("order"), index * 10);
            if (!STRATEGIES.contains(strategy)) {
                throw new IllegalArgumentException(
                        "不支持的执行位置: " + strategy);
            }
            if (!FAILURE_POLICIES.contains(failure)) {
                throw new IllegalArgumentException(
                        "不支持的失败策略: " + failure);
            }
            if (!orders.add(order)) {
                throw new IllegalArgumentException(
                        "事件步骤顺序重复: " + order);
            }
            String serviceId = firstText(
                    step.get("serviceId"));
            String operationContext = "";
            if (StringUtils.hasText(serviceId)) {
                String operationCode = firstText(
                        step.get("operationCode"));
                if (!StringUtils.hasText(operationCode)) {
                    throw new IllegalArgumentException(
                            "事件接口步骤缺少 operationCode");
                }
                Map<String, Object> operation =
                        dataSourceService.operations(serviceId).stream()
                                .filter(item -> Objects.equals(
                                        operationCode,
                                        text(item.get("code"))))
                                .findFirst()
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "接口服务操作不存在: "
                                                        + serviceId + "/"
                                                        + operationCode));
                operationContext = normalize(text(
                        operation.get("contextType")));
            } else if (!(step.get("outputMapping") instanceof Map<?, ?>)
                    && !(step.get("outputMapping") instanceof List<?>)) {
                throw new IllegalArgumentException(
                        "事件步骤必须选择接口操作或配置纯映射");
            }
            if (!"REPLACE".equals(strategy)) {
                continue;
            }
            if (!"ENTITY".equals(ownerType)) {
                replaceCount++;
                continue;
            }
            // 共享实体事件会按 FORM/LIST 投影为不同运行链：有效上下文明确的
            // 接口步骤只计入对应链；纯映射及旧版未知上下文保守计入两边。
            Set<String> replaceContexts =
                    entityEventContexts.contains(operationContext)
                            ? Set.of(operationContext)
                            : entityEventContexts;
            for (String context : replaceContexts) {
                entityReplaceCounts.merge(context, 1, Integer::sum);
            }
        }
        if (!"ENTITY".equals(ownerType) && replaceCount > 1) {
            throw new IllegalArgumentException(
                    "一个事件绑定链最多只能有一个 REPLACE 步骤");
        }
        entityReplaceCounts.forEach((context, count) -> {
            if (count > 1) {
                throw new IllegalArgumentException(
                        "实体默认事件投影到 " + context
                                + " 后最多只能有一个 REPLACE 步骤");
            }
        });
    }

    /**
     * 校验事件是否属于当前配置来源及目标，避免把列表、表单、字段或按钮
     * 事件保存到不会触发的作用域，也阻止运行时伪造跨作用域事件请求。
     */
    private void validateEventScope(
            String ownerType,
            String targetType,
            String eventCode) {
        Map<String, Set<String>> targetScopes =
                EVENT_SCOPES.get(ownerType);
        Set<String> allowed = targetScopes == null
                ? null : targetScopes.get(targetType);
        if (allowed == null) {
            throw new IllegalArgumentException(
                    ownerType + " 作用域不支持 "
                            + targetType + " 事件目标");
        }
        if (!allowed.contains(eventCode)) {
            throw new IllegalArgumentException(
                    ownerType + " 作用域的 " + targetType
                            + " 目标不支持事件: " + eventCode);
        }
    }

    private void requireOwner(
            String ownerType,
            String ownerId) {
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new IllegalArgumentException(
                    "不支持的绑定作用域: " + ownerType);
        }
        if (!StringUtils.hasText(ownerId)) {
            throw new IllegalArgumentException("事件绑定 ownerId 不能为空");
        }
    }

    private void requireOwnerAccess(
            String ownerType,
            String ownerId) {
        switch (normalize(ownerType)) {
            case "FORM" -> configurationAccessService.requireFormAccess(ownerId);
            case "LIST" -> configurationAccessService.requireListAccess(ownerId);
            case "ENTITY" -> {
                configurationAccessService.requireGlobalConfigurationAccess();
                entityAccessPolicy.requireDynamicById(ownerId);
            }
            default -> throw new IllegalArgumentException(
                    "不支持的绑定作用域: " + ownerType);
        }
    }

    private void validateSystemReadOnlyEvent(
            String ownerType,
            String ownerId,
            String eventCode,
            List<Map<String, Object>> steps) {
        if ("ENTITY".equals(ownerType)) {
            return;
        }
        ConfigIdentity identity = identity(ownerType, ownerId);
        EntityDefinition entity =
                definitionMapper.selectById(identity.entityId());
        if (entity == null
                || entity.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        if (!SYSTEM_READ_ONLY_EVENTS.contains(eventCode)) {
            throw new IllegalArgumentException(
                    "平台系统表只能配置只读 UI 事件: "
                            + eventCode);
        }
        for (Map<String, Object> step :
                steps == null
                        ? List.<Map<String, Object>>of()
                        : steps) {
            if (UiDataSourceUsages.LIST_LOAD.equals(eventCode)
                    && "REPLACE".equals(normalize(text(
                            step.get("strategy"))))) {
                throw new IllegalArgumentException(
                        "平台系统表列表不能替换可信只读查询");
            }
            String serviceId = firstText(
                    step.get("serviceId"));
            if (!StringUtils.hasText(serviceId)) {
                continue;
            }
            String operationCode = firstText(
                    step.get("operationCode"));
            if (!StringUtils.hasText(operationCode)) {
                throw new IllegalArgumentException(
                        "事件接口步骤缺少 operationCode");
            }
            Map<String, Object> operation =
                    dataSourceService.operations(serviceId)
                            .stream()
                            .filter(item -> Objects.equals(
                                    operationCode,
                                    text(item.get("code"))))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "接口服务操作不存在: "
                                                    + serviceId
                                                    + "/"
                                                    + operationCode));
            if (!"READ".equals(normalize(
                    text(operation.get("kind"))))) {
                throw new IllegalArgumentException(
                        "平台系统表只允许调用 READ 类型数据源操作");
            }
        }
    }

    private ConfigIdentity identity(
            String configType,
            String configId) {
        if ("FORM".equals(configType)) {
            EntityForm form = formMapper.selectById(configId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在: " + configId);
            }
            EntityDefinition entity =
                    definitionMapper.selectById(form.getEntityId());
            if (entity == null) {
                throw new IllegalArgumentException("表单关联实体不存在");
            }
            return new ConfigIdentity(
                    entity.getId(), entity.getEntityCode(), null);
        }
        EntityListConfig list = listMapper.selectById(configId);
        if (list == null) {
            throw new IllegalArgumentException("列表不存在: " + configId);
        }
        return new ConfigIdentity(
                list.getEntityId(),
                list.getEntityCode(),
                list.getListKey());
    }

    private UiEventBinding findExisting(
            String ownerType,
            String ownerId,
            String targetType,
            String targetKey,
            String eventCode) {
        LambdaQueryWrapper<UiEventBinding> query = new LambdaQueryWrapper<>();
        query.eq(UiEventBinding::getOwnerType, ownerType)
                .eq(UiEventBinding::getOwnerId, ownerId)
                .eq(UiEventBinding::getTargetType, targetType)
                .eq(UiEventBinding::getEventCode, eventCode)
                .eq(UiEventBinding::getDeleted, 0);
        query.eq(
                UiEventBinding::getTargetKey,
                normalizedTargetKey(targetKey));
        return mapper.selectOne(query);
    }

    private void requireRevision(
            Integer expected,
            UiEventBinding current) {
        if (expected == null
                || !Objects.equals(expected, current.getRevision())) {
            throw new RevisionConflictException(
                    "事件绑定版本冲突，请刷新后重试",
                    current);
        }
    }

    private String writeSteps(List<Map<String, Object>> steps) {
        return steps == null || steps.isEmpty()
                ? null : codec.write(steps, "UI事件步骤");
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> objectMapper.convertValue(
                        item,
                        new TypeReference<Map<String, Object>>() {}))
                .toList();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizedTargetKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null
                    ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record ConfigIdentity(
            String entityId,
            String entityCode,
            String listKey) {
    }

    public record ResolvedEventChain(
            List<Map<String, Object>> steps,
            String releaseId,
            Integer releaseVersion,
            String entityId,
            String entityCode,
            String listKey,
            Map<String, Object> snapshot) {
    }
}
