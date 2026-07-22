package com.workflow.service;

import com.workflow.common.BusinessConflictException;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntityDefinitionAccessPolicy {

    private final EntityDefinitionMapper definitionMapper;

    public EntityDefinition requireDynamicById(String entityId) {
        EntityDefinition entity = definitionMapper.selectById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }
        requireDynamic(entity);
        return entity;
    }

    public EntityDefinition requireDynamicByCode(String entityCode) {
        EntityDefinition entity = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + entityCode));
        requireDynamic(entity);
        return entity;
    }

    public void requireDynamic(EntityDefinition entity) {
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_DEFINITION_PROTECTED",
                    "平台系统实体结构由系统模块维护，不能使用动态实体配置修改");
        }
    }
}
