package com.workflow.entity.definition.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.api.response.EntityDefinitionQueryDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;

/**
 * 负责实体定义查询构建器的业务处理；协调校验、状态变化及后续结果传递。
 */
final class EntityDefinitionQueryBuilder {

    /**
     * 初始化实体定义查询构建器，保存构造参数供后续方法使用。
     */
    private EntityDefinitionQueryBuilder() {
    }

    /**
     * 构建实体定义查询构建器；结果供后续流程传递或持久化。
     *
     * @param query 查询，作为 {@code wrapper.eq} 的输入影响后续处理
     * @return 构建后的实体定义查询构建器结果，供调用方继续处理
     */
    static LambdaQueryWrapper<EntityDefinition> build(EntityDefinitionQueryDTO query) {
        LambdaQueryWrapper<EntityDefinition> wrapper = Wrappers.<EntityDefinition>lambdaQuery()
                .orderByDesc(EntityDefinition::getCreatedAt);
        if (StringUtils.isNotBlank(query.getStatus())) {
            wrapper.eq(EntityDefinition::getStatus, query.getStatus());
        }
        if (query.getLifecycleMode() != null) {
            wrapper.eq(EntityDefinition::getLifecycleMode, query.getLifecycleMode());
        }
        if (query.getStorageMode() != null) {
            wrapper.eq(EntityDefinition::getStorageMode, query.getStorageMode());
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(nested -> nested.like(EntityDefinition::getEntityName, keyword)
                    .or()
                    .like(EntityDefinition::getEntityCode, keyword));
        }
        return wrapper;
    }
}
