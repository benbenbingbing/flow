package com.workflow.service;

import com.workflow.common.BusinessConflictException;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 实体定义访问策略，阻止对平台系统实体的动态配置修改。
 *
 * <p>所有需要修改动态实体结构（字段、表单、列表等）的入口都应先通过本策略校验，
 * 系统实体由专属模块维护，禁止通过通用动态实体接口改动。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityDefinitionAccessPolicy {

    private final EntityDefinitionMapper definitionMapper;

    /**
     * 按实体ID加载实体并要求其为动态实体。
     *
     * @param entityId 实体ID
     * @return 实体定义
     * @throws IllegalArgumentException 实体不存在时抛出
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public EntityDefinition requireDynamicById(String entityId) {
        EntityDefinition entity = definitionMapper.selectById(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityId);
        }
        requireDynamic(entity);
        return entity;
    }

    /**
     * 按实体编码加载实体并要求其为动态实体。
     *
     * @param entityCode 实体编码
     * @return 实体定义
     * @throws IllegalArgumentException 实体不存在时抛出
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public EntityDefinition requireDynamicByCode(String entityCode) {
        EntityDefinition entity = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException("实体不存在: " + entityCode));
        requireDynamic(entity);
        return entity;
    }

    /**
     * 校验给定实体为动态实体，系统实体将抛出业务冲突异常。
     *
     * @param entity 实体定义
     * @throws BusinessConflictException 实体为系统实体时抛出
     */
    public void requireDynamic(EntityDefinition entity) {
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_DEFINITION_PROTECTED",
                    "平台系统实体结构由系统模块维护，不能使用动态实体配置修改");
        }
    }
}
