package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;

/**
 * Builds the published snapshot representation of UI event bindings.
 */
@Service
@Slf4j
public class UiEventBindingSnapshotService {

    private static final int OPERATION_SNAPSHOT_VERSION = 1;
    private static final List<String> PINNED_OPERATION_FIELDS = List.of(
            "operationSnapshotVersion",
            "sourceCode",
            "serviceRevision",
            "executableSnapshot",
            "definitionHash",
            "bindingOwnerType",
            "bindingOwnerId",
            "bindingTargetType",
            "bindingTargetKey");

    /**
     * 为激活期的通用接口引用校验构造副本。
     *
     * <p>完整的 FORM_BUTTON_CLICK v1 步骤已经携带独立验哈希的可执行定义，
     * 应按该不可变定义校验并从副本中剥离可变 {@code serviceId} 引用；否则旧发布
     * 会因当前服务改名、移除操作或变更作用域而无法重新激活。完全未钉版的历史
     * 步骤继续保留引用，仍由通用校验器回读当前定义。部分钉版、身份不一致或
     * 定义损坏一律拒绝，绝不降级到历史兼容路径。</p>
     *
     * @param snapshot 已通过发布内容哈希校验的 FORM 快照
     * @return 仅供通用接口引用校验使用的深拷贝
     */
    public Map<String, Object> activationReferenceSnapshot(
            Map<String, Object> snapshot) {
        Map<String, Object> copy = codec.readObject(
                codec.write(snapshot, "表单发布激活接口引用快照"),
                "表单发布激活接口引用快照");
        Object rawBindings = copy.get("eventBindings");
        if (!(rawBindings instanceof List<?> bindings)) {
            return copy;
        }
        for (Object rawBinding : bindings) {
            if (!(rawBinding instanceof Map<?, ?> binding)
                    || !UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                            normalize(text(binding.get("eventCode"))))) {
                continue;
            }
            Object rawSteps = binding.get("steps");
            if (!(rawSteps instanceof List<?> steps)) {
                continue;
            }
            for (Object rawStep : steps) {
                if (rawStep instanceof Map<?, ?> step) {
                    validateAndDetachPinnedActivationReference(
                            step, binding);
                }
            }
        }
        return copy;
    }

    private final UiEventBindingMapper bindingMapper;
    private final UiDataSourceDefinitionMapper dataSourceMapper;
    private final UiDataSourceService dataSourceService;
    private final JsonDocumentCodec codec;

    /**
     * @param dataSourceService 延迟代理用于打断发布服务与执行授权服务之间的构造环；
     *                          只有真正构建发布快照时才解析该依赖
     */
    public UiEventBindingSnapshotService(
            UiEventBindingMapper bindingMapper,
            UiDataSourceDefinitionMapper dataSourceMapper,
            @Lazy UiDataSourceService dataSourceService,
            JsonDocumentCodec codec) {
        this.bindingMapper = bindingMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.dataSourceService = dataSourceService;
        this.codec = codec;
    }

    public List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId) {
        return snapshot(configType, configId, entityId, false);
    }

    /**
     * 构建事件绑定快照，并按发布阶段选择是否固定接口操作定义。
     *
     * <p>设计态快照保留可编辑引用；发布态仅把 {@code FORM_BUTTON_CLICK}
     * 的有效接口步骤转换为带独立哈希的完整可执行定义，使表单按钮的历史发布
     * 不再回读可变的接口服务表。其他事件继续沿用既有发布与执行契约。</p>
     */
    public List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId,
            boolean pinOperationReferences) {
        String normalizedConfigType = normalize(configType);
        Map<String, UiDataSourceDefinition> sourceCache =
                new LinkedHashMap<>();
        return bindingMapper.findForSnapshot(
                        configType,
                        configId,
                        entityId)
                .stream()
                .filter(binding -> appliesToSnapshot(
                        binding,
                        normalizedConfigType))
                .map(binding -> snapshotValue(
                        binding,
                        normalizedConfigType,
                        sourceCache,
                        pinOperationReferences))
                .toList();
    }

    /**
     * 只快照指定所有者的本地事件绑定，供实体级配置迁移使用。
     *
     * <p>与 {@link #snapshot(String, String, String)} 不同，本方法不会混入继承绑定，
     * 避免实体绑定被重复写入每个表单或列表。</p>
     */
    public List<Map<String, Object>> snapshotOwner(
            String ownerType,
            String ownerId) {
        return bindingMapper.findByOwner(
                        normalize(ownerType), ownerId)
                .stream()
                .map(this::snapshotValue)
                .toList();
    }

    /**
     * 锁定指定所有者的全部事件绑定草稿，包含已逻辑删除记录。
     *
     * <p>FORM/LIST 的 revision 不会随事件绑定独立保存而变化，撤销草稿必须先
     * 获取此范围锁，再在同一事务内重算 canonical hash。</p>
     */
    public void lockOwnerBindings(String ownerType, String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            return;
        }
        bindingMapper.findByOwnerForUpdate(
                normalize(ownerType), ownerId);
    }

    /**
     * 将发布快照中的本地绑定精确物化为当前草稿。
     *
     * <p>先物理清理 owner 范围内的草稿行，再使用发布快照中的稳定 ID 重建，
     * 避免逻辑删除后重新插入产生主键冲突或 ID 漂移。发布快照存储在独立表中，
     * 本操作不会修改任何历史发布记录。</p>
     */
    public void restoreLocalBindingsForRelease(
            String configType,
            String configId,
            List<Map<String, Object>> snapshotBindings) {
        String normalizedType = normalize(configType);
        lockOwnerBindings(normalizedType, configId);
        bindingMapper.deleteByOwner(normalizedType, configId);
        for (Map<String, Object> value : snapshotBindings == null
                ? List.<Map<String, Object>>of()
                : snapshotBindings) {
            if (!normalizedType.equals(normalize(text(
                    value.get("ownerType"))))
                    || !configId.equals(text(value.get("ownerId")))) {
                continue;
            }
            UiEventBinding created = new UiEventBinding();
            String publishedId = text(value.get("id"));
            if (StringUtils.hasText(publishedId)) {
                created.setId(publishedId.trim());
            }
            created.setOwnerType(normalizedType);
            created.setOwnerId(configId);
            created.setTargetType(normalize(text(
                    value.get("targetType"))));
            created.setTargetKey(normalizedTargetKey(text(
                    value.get("targetKey"))));
            created.setEventCode(normalize(text(
                    value.get("eventCode"))));
            String inheritanceMode = normalize(text(
                    value.get("inheritanceMode")));
            created.setInheritanceMode(
                    StringUtils.hasText(inheritanceMode)
                            ? inheritanceMode : "INHERIT");
            created.setStepsDocument(codec.write(
                    draftSteps(
                            value.get("steps"),
                            created.getEventCode()),
                    "恢复UI事件绑定步骤"));
            created.setRevision(1);
            created.setEnabled(true);
            created.setDeleted(0);
            created.setCreatedAt(LocalDateTime.now());
            created.setUpdatedAt(LocalDateTime.now());
            bindingMapper.insert(created);
        }
    }

    /**
     * 用不可变发布快照恢复配置自身的事件绑定草稿；实体级继承绑定不受影响。
     */
    public void restoreLocalBindings(
            String configType,
            String configId,
            List<Map<String, Object>> snapshotBindings) {
        String normalizedType = normalize(configType);
        Map<String, UiEventBinding> current =
                new LinkedHashMap<>();
        for (UiEventBinding binding : bindingMapper.findByOwner(
                normalizedType,
                configId)) {
            current.put(bindingKey(
                    binding.getTargetType(),
                    binding.getTargetKey(),
                    binding.getEventCode()), binding);
        }

        for (Map<String, Object> value : snapshotBindings == null
                ? List.<Map<String, Object>>of()
                : snapshotBindings) {
            if (!normalizedType.equals(normalize(text(
                    value.get("ownerType"))))
                    || !configId.equals(text(
                    value.get("ownerId")))) {
                continue;
            }
            String targetType = normalize(text(
                    value.get("targetType")));
            String targetKey = normalizedTargetKey(
                    text(value.get("targetKey")));
            String eventCode = normalize(text(
                    value.get("eventCode")));
            String key = bindingKey(
                    targetType,
                    targetKey,
                    eventCode);
            UiEventBinding existing = current.remove(key);
            String stepsDocument = codec.write(
                    draftSteps(value.get("steps"), eventCode),
                    "恢复UI事件绑定步骤");
            String inheritanceMode = normalize(text(
                    value.get("inheritanceMode")));
            if (existing == null) {
                UiEventBinding created = new UiEventBinding();
                created.setOwnerType(normalizedType);
                created.setOwnerId(configId);
                created.setTargetType(targetType);
                created.setTargetKey(targetKey);
                created.setEventCode(eventCode);
                created.setInheritanceMode(
                        StringUtils.hasText(inheritanceMode)
                                ? inheritanceMode : "INHERIT");
                created.setStepsDocument(stepsDocument);
                created.setRevision(1);
                created.setEnabled(true);
                created.setDeleted(0);
                created.setCreatedAt(LocalDateTime.now());
                created.setUpdatedAt(LocalDateTime.now());
                bindingMapper.insert(created);
                continue;
            }
            UpdateWrapper<UiEventBinding> update = new UpdateWrapper<>();
            update.eq("id", existing.getId())
                    .eq("deleted", 0)
                    .set("inheritance_mode",
                            StringUtils.hasText(inheritanceMode)
                                    ? inheritanceMode : "INHERIT")
                    .set("steps_document", stepsDocument)
                    .set("enabled", 1)
                    .setSql("revision = revision + 1")
                    .set("update_time", LocalDateTime.now());
            bindingMapper.update(null, update);
        }

        for (UiEventBinding stale : current.values()) {
            UpdateWrapper<UiEventBinding> update = new UpdateWrapper<>();
            update.eq("id", stale.getId())
                    .eq("deleted", 0)
                    .set("enabled", 0)
                    .setSql("revision = revision + 1")
                    .set("update_time", LocalDateTime.now());
            bindingMapper.update(null, update);
        }
    }

    private String bindingKey(
            String targetType,
            String targetKey,
            String eventCode) {
        return normalize(targetType)
                + "\u0000"
                + normalizedTargetKey(targetKey)
                + "\u0000"
                + normalize(eventCode);
    }

    private String normalizedTargetKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /**
     * 实体级事件会被表单和列表共同查询，整条绑定先按事件消费域判断是否适用。
     * 接口步骤的 FORM/LIST 投影由 snapshotValue 逐步完成，纯映射步骤因此能在
     * 两类适用快照中保留；配置自身的绑定不做静默过滤。
     */
    private boolean appliesToSnapshot(
            UiEventBinding binding,
            String configType) {
        if (!"ENTITY".equals(normalize(binding.getOwnerType()))) {
            return true;
        }
        if (!UiEventBindingApplicability.appliesTo(
                normalize(binding.getEventCode()), configType)) {
            return false;
        }
        return true;
    }

    private String operationContext(
            UiDataSourceDefinition definition,
            String operationCode) {
        if (definition == null
                || !StringUtils.hasText(
                        definition.getOperationsDocument())) {
            return null;
        }
        return codec.readArray(
                        definition.getOperationsDocument(),
                        "接口服务操作定义")
                .stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(operation -> operationCode.equals(
                        text(operation.get("code"))))
                .map(operation -> normalize(
                        text(operation.get("contextType"))))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> snapshotValue(UiEventBinding binding) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", binding.getId());
        value.put("ownerType", binding.getOwnerType());
        value.put("ownerId", binding.getOwnerId());
        value.put("targetType", binding.getTargetType());
        value.put("targetKey", binding.getTargetKey());
        value.put("eventCode", binding.getEventCode());
        value.put("inheritanceMode", binding.getInheritanceMode());
        value.put(
                "steps",
                StringUtils.hasText(binding.getStepsDocument())
                        ? draftSteps(
                                codec.readArray(
                                        binding.getStepsDocument(),
                                        "UI事件绑定步骤"),
                                binding.getEventCode())
                        : List.of());
        value.put("revision", binding.getRevision());
        return value;
    }

    /**
     * 共享实体事件可以同时配置 FORM 与 LIST 操作；发布某一页面时只保留
     * 与该页面上下文一致的接口步骤，纯映射步骤两边保留。否则另一上下文
     * 的步骤会被发布引用校验器正确拒绝，导致共享默认事件无法发布。
     */
    private Map<String, Object> snapshotValue(
            UiEventBinding binding,
            String configType,
            Map<String, UiDataSourceDefinition> sourceCache,
            boolean pinOperationReferences) {
        Map<String, Object> value = snapshotValue(binding);
        List<Map<String, Object>> steps = new ArrayList<>();
        if (value.get("steps") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> step)
                        || "ENTITY".equals(normalize(binding.getOwnerType()))
                        && !appliesToConfig(
                                step, configType, sourceCache)) {
                    continue;
                }
                Map<String, Object> copy = new LinkedHashMap<>();
                step.forEach((key, child) ->
                        copy.put(String.valueOf(key), child));
                if (pinOperationReferences
                        && UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                                normalize(binding.getEventCode()))) {
                    pinOperation(copy, binding, configType);
                }
                steps.add(copy);
            }
        }
        value.put("steps", steps);
        return value;
    }

    /** 表单按钮发布快照只信任当前权威服务记录和 Provider 制品身份。 */
    private void pinOperation(
            Map<String, Object> step,
            UiEventBinding binding,
            String configType) {
        String serviceId = text(step.get("serviceId"));
        if (!StringUtils.hasText(serviceId)) {
            return;
        }
        String operationCode = text(step.get("operationCode"));
        if (!StringUtils.hasText(operationCode)) {
            // 缺失引用仍交由统一发布校验器报告精确路径。
            return;
        }
        UiDataSourceService.PublishedOperationSnapshot operation =
                dataSourceService.freezeOperation(
                        serviceId, operationCode);
        // 表单按钮 Provider 只能做无副作用的读取、校验和结果映射；实体写入必须
        // 继续走平台默认处理或受控命令计划，外部投递则由业务事务写 Outbox。
        dataSourceService.validatePinnedReadOperation(
                operation.document(),
                operation.hash(),
                operation.serviceId(),
                operation.sourceCode(),
                operation.serviceRevision(),
                operation.operationCode(),
                configType);
        step.put("operationSnapshotVersion", 1);
        step.put("sourceCode", operation.sourceCode());
        step.put("serviceRevision", operation.serviceRevision());
        step.put("executableSnapshot", operation.document());
        step.put("definitionHash", operation.hash());
        step.put("bindingOwnerType", normalize(binding.getOwnerType()));
        step.put("bindingOwnerId", binding.getOwnerId());
        step.put("bindingTargetType", normalize(binding.getTargetType()));
        step.put("bindingTargetKey", normalizedTargetKey(
                binding.getTargetKey()));
    }

    /**
     * 校验不可变操作定义和来源绑定身份后，只在临时副本中移除当前服务引用。
     */
    @SuppressWarnings("unchecked")
    private void validateAndDetachPinnedActivationReference(
            Map<?, ?> rawStep,
            Map<?, ?> binding) {
        Map<String, Object> step = (Map<String, Object>) rawStep;
        boolean hasVersion = step.containsKey("operationSnapshotVersion");
        boolean hasPinnedField = PINNED_OPERATION_FIELDS.stream()
                .filter(field -> !"operationSnapshotVersion".equals(field))
                .anyMatch(step::containsKey);
        if (!hasVersion) {
            if (hasPinnedField) {
                throw new IllegalArgumentException(
                        "表单按钮发布步骤包含不完整的钉版操作字段");
            }
            return;
        }
        Integer version = positiveInteger(
                step.get("operationSnapshotVersion"));
        if (!Integer.valueOf(OPERATION_SNAPSHOT_VERSION).equals(version)
                || !PINNED_OPERATION_FIELDS.stream()
                        .allMatch(step::containsKey)) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤的钉版操作版本或字段不完整");
        }
        String serviceId = requiredText(step, "serviceId");
        String operationCode = requiredText(step, "operationCode");
        String sourceCode = requiredText(step, "sourceCode");
        Integer serviceRevision = positiveInteger(
                step.get("serviceRevision"));
        String document = requiredText(step, "executableSnapshot");
        String hash = requiredText(step, "definitionHash");
        if (serviceRevision == null) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤的接口服务修订号无效");
        }
        requirePinnedBindingIdentity(step, binding);
        dataSourceService.validatePinnedReadOperation(
                document,
                hash,
                serviceId,
                sourceCode,
                serviceRevision,
                operationCode,
                "FORM");
        // 通用校验器会回读 serviceId；完整钉版步骤已经由上面的不可变定义校验。
        step.remove("serviceId");
    }

    private void requirePinnedBindingIdentity(
            Map<String, Object> step,
            Map<?, ?> binding) {
        boolean matches = Objects.equals(
                        normalize(text(step.get("bindingOwnerType"))),
                        normalize(text(binding.get("ownerType"))))
                && Objects.equals(
                        text(step.get("bindingOwnerId")),
                        text(binding.get("ownerId")))
                && Objects.equals(
                        normalize(text(step.get("bindingTargetType"))),
                        normalize(text(binding.get("targetType"))))
                && Objects.equals(
                        normalizedTargetKey(text(
                                step.get("bindingTargetKey"))),
                        normalizedTargetKey(text(
                                binding.get("targetKey"))));
        if (!matches) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤的来源绑定身份不完整或不匹配");
        }
    }

    private String requiredText(
            Map<String, Object> value,
            String key) {
        String result = text(value.get(key));
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException(
                    "表单按钮发布步骤缺少钉版字段: " + key);
        }
        return result;
    }

    private Integer positiveInteger(Object value) {
        if (value instanceof Number number
                && number.doubleValue() == number.intValue()
                && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int result = Integer.parseInt(text(value));
            return result > 0 ? result : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    /**
     * 表单按钮的不可变执行定义属于发布制品，恢复到草稿或导出可编辑绑定时
     * 必须剥离。其他事件的同名扩展字段保持原样，避免改变既有通用事件契约。
     */
    private List<Map<String, Object>> draftSteps(
            Object rawSteps,
            String eventCode) {
        if (!(rawSteps instanceof List<?> steps)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : steps) {
            if (!(raw instanceof Map<?, ?> step)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            step.forEach((key, value) ->
                    copy.put(String.valueOf(key), value));
            if (UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                    normalize(eventCode))) {
                PINNED_OPERATION_FIELDS.forEach(copy::remove);
            }
            result.add(copy);
        }
        return List.copyOf(result);
    }

    private boolean appliesToConfig(
            Map<?, ?> step,
            String configType,
            Map<String, UiDataSourceDefinition> sourceCache) {
        String serviceId = text(step.get("serviceId"));
        String operationCode = text(step.get("operationCode"));
        if (!StringUtils.hasText(serviceId)) {
            return true;
        }
        UiDataSourceDefinition definition = sourceCache.computeIfAbsent(
                serviceId,
                dataSourceMapper::selectById);
        String context = operationContext(definition, operationCode);
        // 缺失或损坏的引用继续进入快照，由发布校验器给出精确错误。
        return !StringUtils.hasText(context)
                || Objects.equals(configType, context);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
