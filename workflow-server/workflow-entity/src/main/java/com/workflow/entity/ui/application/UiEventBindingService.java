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
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
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

    /** 发布制品字段只能由发布流程生成，草稿保存时一律剥离。 */
    private static final Set<String> PINNED_INTERFACE_FIELDS = Set.of(
            "serviceId",
            "operationCode",
            "serviceName",
            "operationName",
            "interfaceName",
            "providerOperationCode",
            "legacyServiceId",
            "operationSnapshotVersion",
            "sourceCode",
            "serviceRevision",
            "extensionKey",
            "extensionRevision",
            "executableSnapshot",
            "definitionHash",
            "bindingOwnerType",
            "bindingOwnerId",
            "bindingTargetType",
            "bindingTargetKey");

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
    private static final List<String> PINNED_BINDING_IDENTITY_FIELDS =
            List.of(
                    "bindingOwnerType",
                    "bindingOwnerId",
                    "bindingTargetType",
                    "bindingTargetKey");
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
    private final UiInterfaceExtensionService dataSourceService;
    private final UiEventBindingSnapshotService eventBindingSnapshotService;
    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 列出界面事件绑定；查询结果供调用方展示或继续处理。
     *
     * @param ownerType 归属方类型标识，决定后续界面事件绑定采用的处理分支
     * @param ownerId 归属方ID，后续用于列出界面事件绑定时定位或关联目标
     * @return 界面事件绑定集合，供调用方遍历或展示
     */
    public List<UiEventBinding> list(
            String ownerType,
            String ownerId) {
        String normalizedOwner = normalize(ownerType);
        requireOwner(normalizedOwner, ownerId);
        requireOwnerAccess(normalizedOwner, ownerId);
        return mapper.findByOwner(normalizedOwner, ownerId);
    }

    /**
     * 整理目录数据，供调用方遍历或继续处理。
     *
     * @return 目录键值结果，供调用方继续处理
     */
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

    /**
     * 解析草稿；输出作为后续校验或处理的输入。
     *
     * @param ownerType 归属方类型标识，决定后续草稿采用的处理分支
     * @param ownerId 归属方ID，后续用于解析草稿时定位或关联目标
     * @param eventCode 事件编码，后续用于解析草稿时定位或关联目标
     * @return 草稿键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
                Map.of(),
                null,
                null);
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

    /**
     * 保存界面事件绑定；后续读取或执行将使用更新后的状态。
     *
     * @param request 本次请求，后续经校验后用于保存界面事件绑定
     * @return 保存后的界面事件绑定结果，供调用方继续处理
     */
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

    /**
     * 删除界面事件绑定；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param expectedRevision 预期修订版本，作为 {@code requireRevision} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
     *
     * @param configType 配置类型标识，决定后续快照绑定集合采用的处理分支
     * @param configId 配置ID，后续用于处理快照绑定集合时定位或关联目标
     * @param entityId 实体ID，后续用于处理快照绑定集合时定位或关联目标
     * @return 界面事件绑定集合，供调用方遍历或展示
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
     *
     * @param request 本次请求，后续经校验后用于解析已发布
     * @return 解析后的已发布结果，供调用方继续处理
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
                    snapshot,
                    resolved.effectiveReleaseId(),
                    resolved.effectiveContentHash());
            logResolvedChain(request, chain, "FORM_RELEASE");
            return chain;
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                request.isServerPinnedRelease()
                        ? releaseService.resolveServerPinnedRuntimeListRelease(
                                request.getConfigId(), request.getReleaseId(), request.getReleaseVersion())
                        : releaseService.resolveRuntimeListRelease(
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
                snapshot,
                resolved.releaseId(),
                null);
        logResolvedChain(
                request,
                chain,
                resolved.pinned()
                        ? "SIGNED_LIST_CONTEXT"
                        : "ACTIVE_RELEASE");
        return chain;
    }

    /**
     * 处理日志已解析链，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理日志已解析链
     * @param chain 链，作为 {@code safe} 的输入影响后续处理
     * @param source 待处理日志已解析链的原始输入，结果供调用方继续使用
     */
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

    /**
     * 处理空链，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续空链采用的处理分支
     * @param request 本次请求，后续经校验后用于处理空链
     * @return 处理后的空链结果，供调用方继续处理
     */
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

    /**
     * 解析界面事件绑定；输出作为后续校验或处理的输入。
     *
     * @param bindings 绑定集合，作为 {@code applyLevel} 的输入影响后续处理
     * @param identity 身份，作为 {@code applyLevel} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于解析界面事件绑定
     * @param releaseId 发布版本ID，后续用于解析界面事件绑定时定位或关联目标
     * @param releaseVersion 发布版本，供本方法解析界面事件绑定时使用
     * @param snapshot 快照，供本方法解析界面事件绑定时使用
     * @param effectiveReleaseId 有效发布版本ID，后续用于解析界面事件绑定时定位或关联目标
     * @param effectiveContentHash 有效内容哈希，供本方法解析界面事件绑定时使用
     * @return 解析后的界面事件绑定结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private ResolvedEventChain resolve(
            List<Map<String, Object>> bindings,
            ConfigIdentity identity,
            UiEventExecuteRequest request,
            String releaseId,
            Integer releaseVersion,
            Map<String, Object> snapshot,
            String effectiveReleaseId,
            String effectiveContentHash) {
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
        // 历史发布快照不可改写：仅在没有有效替代步骤时，把旧查询槽位投影到统一链。
        // 新草稿由 V094 迁移为可编辑的 LIST_LOAD 步骤，旧 Provider 保留 LIST_QUERY 契约。
        if (replacements == 0 && "LIST".equals(normalize(request.getConfigType()))
                && UiDataSourceUsages.LIST_LOAD.equals(eventCode)
                && snapshot != null && snapshot.get("list") instanceof Map<?, ?> list
                && StringUtils.hasText(text(list.get("queryInterfaceExtensionId")))) {
            effective.add(new LinkedHashMap<>(Map.of(
                    "name", "列表查询接口（已迁移）",
                    "strategy", "REPLACE",
                    "extensionId", text(list.get("queryInterfaceExtensionId")),
                    "legacyListQuery", true,
                    "failurePolicy", "STOP")));
        }
        if (replacements > 1) {
            if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)) {
                // 损坏或历史发布快照也可能绕过当前保存/发布校验；表单按钮
                // 在解析阶段仍使用面向业务的“主处理”概念返回一致错误。
                throw new BusinessConflictException(
                        "UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                        "启用的表单自定义按钮最终有效链必须且只能包含一个主处理步骤，当前为 "
                                + replacements + " 个");
            }
            throw new BusinessConflictException(
                    "UI_EVENT_MULTIPLE_REPLACE",
                    "同一事件的有效执行链最多只能包含一个 REPLACE 步骤");
        }
        if (replacements == 1
                && "FORM".equals(normalize(request.getConfigType()))
                && "BUTTON".equals(normalize(request.getTargetType()))
                && UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)) {
            Map<String, Object> mainStep = effective.stream()
                    .filter(step -> "REPLACE".equals(normalize(text(
                            step.get("strategy")))))
                    .findFirst()
                    .orElseThrow();
            if (hasExecutionCondition(mainStep)) {
                // 即使旧发布快照绕过保存与发布校验，精确按钮主处理也不能
                // 因条件不满足而静默跳过。
                throw new BusinessConflictException(
                        "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                        "表单自定义按钮的主处理步骤必须无条件执行，请移除主处理的执行条件");
            }
        }
        return new ResolvedEventChain(
                List.copyOf(effective),
                releaseId,
                releaseVersion,
                identity.entityId(),
                identity.entityCode(),
                identity.listKey(),
                snapshot == null ? Map.of() : Map.copyOf(snapshot),
                effectiveReleaseId,
                effectiveContentHash);
    }

    /**
     * 应用层级，并将结果传给后续步骤。
     *
     * @param effective 有效，供本方法应用层级时使用
     * @param binding 绑定，作为 {@code normalize} 的输入影响后续处理
     */
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
        for (Map<String, Object> step : mapList(binding.get("steps"))) {
            Map<String, Object> executable = new LinkedHashMap<>(step);
            String eventCode = normalize(text(binding.get("eventCode")));
            if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                    || FORM_FIELD_EVENTS.contains(eventCode)) {
                attachTrustedBindingIdentity(executable, binding);
            }
            effective.add(executable);
        }
    }

    /**
     * 把步骤来源绑定保留到可信运行链，保证 OWNER 默认链仍按原绑定授权。
     * 新版钉版步骤必须携带且匹配发布时身份；无版本标记的历史步骤在已验证
     * 发布快照解析后补齐身份，以兼容旧发布。
     *
     * @param step 步骤，作为 {@code identity.forEach} 的输入影响后续处理
     * @param binding 绑定，作为 {@code identity.put} 的输入影响后续处理
     */
    private void attachTrustedBindingIdentity(
            Map<String, Object> step,
            Map<String, Object> binding) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("bindingOwnerType", normalize(text(
                binding.get("ownerType"))));
        identity.put("bindingOwnerId", text(binding.get("ownerId")));
        identity.put("bindingTargetType", normalize(text(
                binding.getOrDefault("targetType", "OWNER"))));
        identity.put("bindingTargetKey", normalizedTargetKey(text(
                binding.get("targetKey"))));
        if (step.containsKey("operationSnapshotVersion")) {
            boolean matches = PINNED_BINDING_IDENTITY_FIELDS.stream()
                    .allMatch(step::containsKey)
                    && identity.entrySet().stream().allMatch(entry ->
                            Objects.equals(
                                    entry.getValue(),
                                    "bindingOwnerType".equals(entry.getKey())
                                            || "bindingTargetType".equals(
                                            entry.getKey())
                                            ? normalize(text(step.get(
                                            entry.getKey())))
                                            : "bindingTargetKey".equals(
                                            entry.getKey())
                                            ? normalizedTargetKey(text(
                                            step.get(entry.getKey())))
                                            : text(step.get(entry.getKey()))));
            if (!matches) {
                throw new BusinessConflictException(
                        "UI_EVENT_PINNED_BINDING_INVALID",
                        "表单事件发布步骤的来源绑定身份不完整或不匹配");
            }
            return;
        }
        identity.forEach(step::put);
    }

    /**
     * 查询绑定；查询结果供调用方展示或继续处理。
     *
     * @param bindings 绑定集合，供本方法查询绑定时使用
     * @param ownerType 归属方类型标识，决定后续绑定采用的处理分支
     * @param ownerId 归属方ID，后续用于查询绑定时定位或关联目标
     * @param targetType 目标类型标识，决定后续绑定采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param eventCode 事件编码，后续用于查询绑定时定位或关联目标
     * @return 绑定键值结果，供调用方继续处理
     */
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

    /**
     * 校验界面事件绑定；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验界面事件绑定
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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
        if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                && "BUTTON".equals(targetType)
                && "DISABLE".equals(inheritance)) {
            // 按钮级 DISABLE 会把最终链清空，却仍保留一个可点击按钮配置；停用
            // 必须使用按钮自身的 enabled 开关。OWNER 层仍允许清空上级公共链，
            // 由更具体的 BUTTON 层重新提供主处理。
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_DISABLE_UNSUPPORTED",
                    "表单自定义按钮不支持“禁用自定义”；如需停用按钮，请关闭按钮的启用开关");
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
            if (Boolean.TRUE.equals(step.get("legacyListQuery"))
                    && (!UiDataSourceUsages.LIST_LOAD.equals(eventCode) || !"REPLACE".equals(strategy))) {
                throw new IllegalArgumentException("历史查询接口标记只能用于 LIST_LOAD 的替代平台处理步骤");
            }
            if (!FAILURE_POLICIES.contains(failure)) {
                throw new IllegalArgumentException(
                        "不支持的失败策略: " + failure);
            }
            if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                    && "REPLACE".equals(strategy)
                    && hasExecutionCondition(step)) {
                // FORM_BUTTON_CLICK 的每个层级都把 REPLACE 定义为主处理；带
                // 条件会造成按钮已执行但主动作被静默跳过，保存时统一拒绝。
                throw new BusinessConflictException(
                        "UI_EVENT_FORM_BUTTON_MAIN_STEP_CONDITION_UNSUPPORTED",
                        "表单自定义按钮的主处理步骤必须无条件执行，请移除主处理的执行条件");
            }
            if (!orders.add(order)) {
                throw new IllegalArgumentException(
                        "事件步骤顺序重复: " + order);
            }
            String extensionId = firstText(
                    step.get("extensionId"), step.get("serviceId"));
            String operationContext = "";
            if (StringUtils.hasText(extensionId)) {
                String operationCode = firstText(
                        step.get("operationCode"));
                UiExtensionDefinition definition = dataSourceService
                        .requireExecutableDefinition(
                                extensionId, operationCode);
                operationContext = normalize(
                        definition.getInterfaceContextType());
                if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                        && !"READ".equals(normalize(
                                definition.getInterfaceKind()))) {
                    throw new IllegalArgumentException(
                            "表单自定义按钮接口步骤只允许 READ 接口；实体写入必须走平台默认处理或受控命令计划，外部副作用必须由业务事务投递 Outbox");
                }
            } else if (!(step.get("outputMapping") instanceof Map<?, ?>)
                    && !(step.get("outputMapping") instanceof List<?>)) {
                throw new IllegalArgumentException(
                    "事件步骤必须选择接口扩展或配置纯映射");
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
        if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                && "BUTTON".equals(targetType)
                && "REPLACE".equals(inheritance)
                && replaceCount != 1) {
            // BUTTON 层选择“仅使用当前层”后不再可能继承主处理，因此草稿保存时
            // 就必须保证本层主处理唯一，避免只能到发布阶段才发现空链。
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                    "表单自定义按钮使用“仅使用当前层”时，本层必须且只能包含一个主处理步骤，当前为 "
                            + replaceCount + " 个");
        }
        if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(eventCode)
                && "BUTTON".equals(targetType)
                && replaceCount > 1) {
            // 即使继续继承上级，BUTTON 已是最后一层，本层多个主处理也不可能
            // 在后续合并中恢复为合法链，应在保存入口直接返回统一业务错误。
            throw new BusinessConflictException(
                    "UI_EVENT_FORM_BUTTON_MAIN_STEP_REQUIRED",
                    "表单自定义按钮本层最多只能包含一个主处理步骤，当前为 "
                            + replaceCount + " 个");
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
     * 空条件对象等同于未配置；只有会参与运行时判断的非空对象才算执行条件。
     *
     * @param step 步骤，供本方法判断是否具有执行条件时使用
     * @return 执行条件条件成立时为 true，否则为 false
     */
    private boolean hasExecutionCondition(Map<String, Object> step) {
        return step != null
                && step.get("condition") instanceof Map<?, ?> condition
                && !condition.isEmpty();
    }

    /**
     * 校验事件是否属于当前配置来源及目标，避免把列表、表单、字段或按钮
     * 事件保存到不会触发的作用域，也阻止运行时伪造跨作用域事件请求。
     *
     * @param ownerType 归属方类型标识，决定后续事件作用域采用的处理分支
     * @param targetType 目标类型标识，决定后续事件作用域采用的处理分支
     * @param eventCode 事件编码，后续用于校验事件作用域时定位或关联目标
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

    /**
     * 校验并获取归属方；不满足约束时阻止后续处理。
     *
     * @param ownerType 归属方类型标识，决定后续归属方采用的处理分支
     * @param ownerId 归属方ID，后续用于校验并获取归属方时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验并获取归属方访问；不满足约束时阻止后续处理。
     *
     * @param ownerType 归属方类型标识，决定后续归属方访问采用的处理分支
     * @param ownerId 归属方ID，后续用于校验并获取归属方访问时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验系统读取仅事件；不满足约束时阻止后续处理。
     *
     * @param ownerType 归属方类型标识，决定后续系统读取仅事件采用的处理分支
     * @param ownerId 归属方ID，后续用于校验系统读取仅事件时定位或关联目标
     * @param eventCode 事件编码，后续用于校验系统读取仅事件时定位或关联目标
     * @param steps 步骤集合，供本方法校验系统读取仅事件时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
            String extensionId = firstText(
                    step.get("extensionId"), step.get("serviceId"));
            if (!StringUtils.hasText(extensionId)) {
                continue;
            }
            String operationCode = firstText(
                    step.get("operationCode"));
            UiExtensionDefinition definition = dataSourceService
                    .requireExecutableDefinition(
                            extensionId, operationCode);
            if (!"READ".equals(normalize(
                    definition.getInterfaceKind()))) {
                throw new IllegalArgumentException(
                        "平台系统表只允许调用 READ 类型接口扩展");
            }
        }
    }

    /**
     * 处理身份，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续身份采用的处理分支
     * @param configId 配置ID，后续用于处理身份时定位或关联目标
     * @return 处理后的身份结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 查询已有；查询结果供调用方展示或继续处理。
     *
     * @param ownerType 归属方类型标识，决定后续已有采用的处理分支
     * @param ownerId 归属方ID，后续用于查询已有时定位或关联目标
     * @param targetType 目标类型标识，决定后续已有采用的处理分支
     * @param targetKey 目标键，后续用于授权校验、关联或幂等去重
     * @param eventCode 事件编码，后续用于查询已有时定位或关联目标
     * @return 符合条件的界面事件绑定结果，供调用方继续处理
     */
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

    /**
     * 校验并获取修订版本；不满足约束时阻止后续处理。
     *
     * @param expected 预期，供本方法校验并获取修订版本时使用
     * @param current 当前，作为 {@code RevisionConflictException} 的输入影响后续处理
     */
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

    /**
     * 写入步骤集合；后续读取或执行将使用更新后的状态。
     *
     * @param steps 步骤集合，供本方法写入步骤集合时使用
     * @return 写入后的步骤集合文本，供调用方比较或展示
     */
    private String writeSteps(List<Map<String, Object>> steps) {
        return steps == null || steps.isEmpty()
                ? null : codec.write(
                        normalizeInterfaceReferences(steps),
                        "UI事件步骤");
    }

    /**
     * 把事件草稿统一收敛为单一 {@code extensionId} 引用。
     *
     * <p>迁移前的编辑器可能仍提交 {@code serviceId + operationCode}；
     * 该组合只在读入时用于解析迁移后的扩展记录，落库后立即移除，
     * 防止新发布快照继续扩散多操作服务模型。</p>
     *
     * @param steps 步骤集合，供本方法规范化接口引用时使用
     * @return 界面事件绑定集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> normalizeInterfaceReferences(
            List<Map<String, Object>> steps) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            Map<String, Object> copy = new LinkedHashMap<>(step);
            PINNED_INTERFACE_FIELDS.forEach(copy::remove);
            String referenceId = firstText(
                    step.get("extensionId"), step.get("serviceId"));
            if (StringUtils.hasText(referenceId)) {
                UiExtensionDefinition definition = dataSourceService
                        .requireExecutableDefinition(
                                referenceId,
                                firstText(step.get("operationCode")));
                copy.put("extensionId", definition.getId());
                copy.remove("serviceId");
                copy.remove("operationCode");
            }
            normalized.add(copy);
        }
        return List.copyOf(normalized);
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 界面事件绑定集合，供调用方遍历或展示
     */
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

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面事件绑定的原始输入，结果供调用方继续使用
     * @return 规范化后的界面事件绑定文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    /**
     * 把空白文本转为 null，避免后续把空字符串当作有效配置。
     *
     * @param value 待处理空白截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空白截止空值文本，供调用方比较或展示
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 生成规范化目标键文本，供后续匹配或展示。
     *
     * @param value 待处理规范化目标键的原始输入，结果供调用方继续使用
     * @return 处理后的规范化目标键文本，供调用方比较或展示
     */
    private String normalizedTargetKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
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
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的整数结果，供调用方继续处理
     */
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

    /**
     * 封装配置身份的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityId 实体ID，后续用于处理配置身份时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     */
    private record ConfigIdentity(
            String entityId,
            String entityCode,
            String listKey) {
    }

    /**
     * 封装已解析事件链的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param steps 步骤集合，保存在对象中供后续校验、查询或展示
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param entityId 实体ID，后续用于处理已解析事件链时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param effectiveReleaseId 有效发布版本ID，后续用于处理已解析事件链时定位或关联目标
     * @param effectiveContentHash 有效内容哈希，保存在对象中供后续校验、查询或展示
     */
    public record ResolvedEventChain(
            List<Map<String, Object>> steps,
            String releaseId,
            Integer releaseVersion,
            String entityId,
            String entityCode,
            String listKey,
            Map<String, Object> snapshot,
            String effectiveReleaseId,
            String effectiveContentHash) {

        /**
         * 保留非表单按钮与现有测试构造兼容。
         *
         * @param steps 步骤集合，保存在对象中供后续校验、查询或展示
         * @param releaseId 发布版本 ID，后续用于解析固定配置
         * @param releaseVersion 发布版本号，后续用于校验快照一致性
         * @param entityId 实体ID，后续用于初始化已解析事件链时定位或关联目标
         * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
         * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
         * @param snapshot 快照，保存在对象中供后续校验、查询或展示
         */
        public ResolvedEventChain(
                List<Map<String, Object>> steps,
                String releaseId,
                Integer releaseVersion,
                String entityId,
                String entityCode,
                String listKey,
                Map<String, Object> snapshot) {
            this(
                    steps,
                    releaseId,
                    releaseVersion,
                    entityId,
                    entityCode,
                    listKey,
                    snapshot,
                    releaseId,
                    null);
        }
    }
}
