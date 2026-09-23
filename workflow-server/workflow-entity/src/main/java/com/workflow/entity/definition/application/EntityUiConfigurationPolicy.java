package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 表单和列表 UI 配置的实体访问策略。
 *
 * <p>动态实体和平台系统实体都可以维护展示配置；实体结构、状态、流程绑定
 * 和数据写入仍由 {@link EntityDefinitionAccessPolicy} 单独保护。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityUiConfigurationPolicy {

    private final EntityDefinitionMapper definitionMapper;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;

    /**
     * 校验并获取{@code configurable}ID；不满足约束时阻止后续处理。
     *
     * @param entityId 实体ID，后续用于校验并获取{@code configurable}ID时定位或关联目标
     * @return 校验并获取后的{@code configurable}ID结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public EntityDefinition requireConfigurableById(String entityId) {
        EntityDefinition entity = definitionMapper.selectById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }
        requireSupportedSystemEntity(entity);
        return entity;
    }

    /**
     * 校验并获取{@code configurable}编码；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 校验并获取后的{@code configurable}编码结果，供调用方继续处理
     */
    public EntityDefinition requireConfigurableByCode(String entityCode) {
        EntityDefinition entity = definitionMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "实体不存在: " + entityCode));
        requireSupportedSystemEntity(entity);
        return entity;
    }

    /**
     * 校验并获取{@code supported}系统实体；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireSupportedSystemEntity(
            EntityDefinition entity) {
        if (entity.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM
                && !systemEntityFieldPolicy.isSupportedEntity(
                        entity.getEntityCode())) {
            throw new IllegalArgumentException(
                    "平台系统实体不在可配置白名单: "
                            + entity.getEntityCode());
        }
    }
}
