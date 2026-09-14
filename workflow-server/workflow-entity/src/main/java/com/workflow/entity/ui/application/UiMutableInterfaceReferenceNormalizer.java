package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将表单、节点等可变草稿里的接口绑定收敛为单一 {@code extensionId}。
 *
 * <p>迁移前的 {@code serviceId + operationCode} 只用于查找 V088 拆分后的
 * 接口扩展。草稿保存后会删除旧身份和发布钉版字段，避免下一次编辑再次形成
 * “服务 + 操作”的二级模型。解析阶段允许定义处于 DISABLED；执行和发布入口
 * 仍须独立校验 ACTIVE 状态。</p>
 */
@Component
@RequiredArgsConstructor
public class UiMutableInterfaceReferenceNormalizer {

    private static final Set<String> MUTABLE_FORBIDDEN_IDENTITY_FIELDS = Set.of(
            "serviceId", "operationCode", "sourceCode", "serviceName",
            "serviceRevision", "operationName", "extensionKey",
            "extensionRevision", "operationSnapshotVersion",
            "executableSnapshot", "definitionHash", "legacyServiceId",
            "providerOperationCode");

    private final UiExtensionDefinitionMapper extensionMapper;

    /**
     * 规范化按 usage 分组的接口绑定对象；每个 usage 可配置一个步骤或步骤数组。
     *
     * @param bindings 表单或节点的可变绑定配置
     * @return 保持原顺序的防御性副本
     * @throws IllegalArgumentException 历史 pair 不完整或无法解析时抛出
     */
    public Map<String, Object> normalizeBindings(Map<String, Object> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return bindings == null ? Map.of() : new LinkedHashMap<>(bindings);
        }
        if (isReference(bindings)) {
            return normalizeReference(bindings);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        bindings.forEach((usage, configured) ->
                result.put(usage, normalizeConfigured(configured)));
        return result;
    }

    private Object normalizeConfigured(Object configured) {
        if (configured instanceof Map<?, ?> raw) {
            return normalizeReference(stringMap(raw));
        }
        if (configured instanceof List<?> values) {
            List<Object> normalized = new ArrayList<>(values.size());
            for (Object value : values) {
                normalized.add(value instanceof Map<?, ?> raw
                        ? normalizeReference(stringMap(raw))
                        : value);
            }
            return normalized;
        }
        return configured;
    }

    private Map<String, Object> normalizeReference(
            Map<String, Object> configured) {
        Map<String, Object> result = new LinkedHashMap<>(configured);
        String extensionId = text(configured.get("extensionId"));
        String legacyServiceId = text(configured.get("serviceId"));
        String operationCode = text(configured.get("operationCode"));
        String referenceId = StringUtils.hasText(extensionId)
                ? extensionId.trim()
                : trimToNull(legacyServiceId);
        if (!StringUtils.hasText(referenceId)) {
            // 未选择接口的空绑定仍可由上层配置规则决定是否允许。
            MUTABLE_FORBIDDEN_IDENTITY_FIELDS.forEach(result::remove);
            return result;
        }
        if (!StringUtils.hasText(extensionId)
                && !StringUtils.hasText(operationCode)) {
            throw new IllegalArgumentException(
                    "历史接口绑定缺少 operationCode，无法迁移为 extensionId");
        }
        UiExtensionDefinition definition = resolveDefinition(
                referenceId, operationCode);
        result.put("extensionId", definition.getId());
        MUTABLE_FORBIDDEN_IDENTITY_FIELDS.forEach(result::remove);
        return result;
    }

    /** 历史兼容查找不检查 enabled，避免读取旧草稿时因停用而无法修复或保存。 */
    private UiExtensionDefinition resolveDefinition(
            String referenceId,
            String operationCode) {
        UiExtensionDefinition definition = extensionMapper.selectById(
                referenceId);
        if (!isInterface(definition) && StringUtils.hasText(operationCode)) {
            definition = extensionMapper.selectOne(
                    new LambdaQueryWrapper<UiExtensionDefinition>()
                            .eq(UiExtensionDefinition::getExtensionType,
                                    "INTERFACE")
                            .eq(UiExtensionDefinition::getLegacyServiceId,
                                    referenceId)
                            .eq(UiExtensionDefinition::getProviderOperationCode,
                                    operationCode.trim())
                            .eq(UiExtensionDefinition::getDeleted, 0));
        }
        if (!isInterface(definition)) {
            throw new IllegalArgumentException(
                    "接口扩展不存在或历史引用尚未迁移: "
                            + referenceId
                            + (StringUtils.hasText(operationCode)
                            ? "/" + operationCode.trim() : ""));
        }
        return definition;
    }

    private boolean isReference(Map<String, Object> value) {
        return value.containsKey("extensionId")
                || value.containsKey("serviceId");
    }

    private boolean isInterface(UiExtensionDefinition definition) {
        return definition != null
                && "INTERFACE".equalsIgnoreCase(
                        definition.getExtensionType())
                && !Integer.valueOf(1).equals(definition.getDeleted());
    }

    private Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
