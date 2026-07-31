package com.workflow.entity.definition.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.api.response.EntityDefinitionQueryDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;

final class EntityDefinitionQueryBuilder {

    private EntityDefinitionQueryBuilder() {
    }

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
