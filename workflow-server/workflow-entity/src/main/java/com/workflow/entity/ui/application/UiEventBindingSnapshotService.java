package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * Builds the published snapshot representation of UI event bindings.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiEventBindingSnapshotService {

    private final UiEventBindingMapper bindingMapper;
    private final UiDataSourceDefinitionMapper dataSourceMapper;
    private final JsonDocumentCodec codec;

    public List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId) {
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
                        normalizedConfigType,
                        sourceCache))
                .map(this::snapshotValue)
                .toList();
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
                    value.get("steps") instanceof List<?> steps
                            ? steps : List.of(),
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
     * 实体级事件会被表单和列表共同查询，因此按步骤引用的接口操作上下文筛选。
     * FORM 操作只进入表单快照，LIST 操作只进入列表快照；配置自身的绑定不在这里
     * 静默过滤，继续交给发布引用校验器严格报错。
     */
    private boolean appliesToSnapshot(
            UiEventBinding binding,
            String configType,
            Map<String, UiDataSourceDefinition> sourceCache) {
        if (!"ENTITY".equals(normalize(binding.getOwnerType()))) {
            return true;
        }
        Set<String> contexts = referencedOperationContexts(
                binding,
                sourceCache);
        if (contexts.isEmpty() || contexts.contains(configType)) {
            return true;
        }
        log.info(
                "实体级UI事件绑定不适用于当前发布类型，跳过快照继承: bindingId={}, eventCode={}, publishType={}, operationContexts={}",
                LogValue.safe(binding.getId()),
                LogValue.safe(binding.getEventCode()),
                LogValue.safe(configType),
                contexts);
        return false;
    }

    private Set<String> referencedOperationContexts(
            UiEventBinding binding,
            Map<String, UiDataSourceDefinition> sourceCache) {
        if (!StringUtils.hasText(binding.getStepsDocument())) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : codec.readArray(
                binding.getStepsDocument(),
                "UI事件绑定步骤")) {
            if (!(item instanceof Map<?, ?> step)) {
                continue;
            }
            String serviceId = text(step.get("serviceId"));
            String operationCode = text(step.get("operationCode"));
            if (!StringUtils.hasText(serviceId)
                    || !StringUtils.hasText(operationCode)) {
                continue;
            }
            UiDataSourceDefinition definition =
                    sourceCache.computeIfAbsent(
                            serviceId,
                            dataSourceMapper::selectById);
            String context = operationContext(
                    definition,
                    operationCode);
            if (StringUtils.hasText(context)) {
                result.add(context);
            }
        }
        return result;
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
                        ? codec.readArray(
                                binding.getStepsDocument(),
                                "UI事件绑定步骤")
                        : List.of());
        value.put("revision", binding.getRevision());
        return value;
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
