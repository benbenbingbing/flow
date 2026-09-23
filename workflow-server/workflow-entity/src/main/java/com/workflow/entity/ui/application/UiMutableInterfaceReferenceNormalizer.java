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

    /**
     * 规范化已配置；输出作为后续校验或处理的输入。
     *
     * @param configured 已配置，供本方法规范化已配置时使用
     * @return 规范化后的已配置结果，供调用方继续处理
     */
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

    /**
     * 规范化引用；输出作为后续校验或处理的输入。
     *
     * @param configured 已配置，作为 {@code text} 的输入影响后续处理
     * @return 引用键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 历史兼容查找不检查 enabled，避免读取旧草稿时因停用而无法修复或保存。
     *
     * @param referenceId 引用ID，后续用于解析定义时定位或关联目标
     * @param operationCode 操作编码，后续用于解析定义时定位或关联目标
     * @return 解析后的定义结果，供调用方继续处理
     */
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

    /**
     * 判断是否引用；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否引用的原始输入，结果供调用方继续使用
     * @return 引用条件成立时为 true，否则为 false
     */
    private boolean isReference(Map<String, Object> value) {
        return value.containsKey("extensionId")
                || value.containsKey("serviceId");
    }

    /**
     * 判断是否接口；判断结果决定调用方的后续分支。
     *
     * @param definition 定义，作为 {@code equalsIgnoreCase} 的输入影响后续处理
     * @return 接口条件成立时为 true，否则为 false
     */
    private boolean isInterface(UiExtensionDefinition definition) {
        return definition != null
                && "INTERFACE".equalsIgnoreCase(
                        definition.getExtensionType())
                && !Integer.valueOf(1).equals(definition.getDeleted());
    }

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param raw 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
    private Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
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
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
