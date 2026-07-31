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

    public EntityDefinition requireConfigurableById(String entityId) {
        EntityDefinition entity = definitionMapper.selectById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }
        requireSupportedSystemEntity(entity);
        return entity;
    }

    public EntityDefinition requireConfigurableByCode(String entityCode) {
        EntityDefinition entity = definitionMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "实体不存在: " + entityCode));
        requireSupportedSystemEntity(entity);
        return entity;
    }

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
