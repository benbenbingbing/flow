package com.workflow.admin.extension.action.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.extension.action.infrastructure.persistence.record.FlowActionDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程动作定义可见实体关系 Mapper。
 *
 * <p>维护动作定义与可见实体编码的多对多关系。</p>
 */
@Mapper
public interface FlowActionDefinitionEntityMapper
        extends BaseMapper<FlowActionDefinitionEntity> {

    /**
     * 查询动作定义可见的全部实体编码（按编码排序）。
     *
     * @param definitionId 动作定义 ID
     * @return 实体编码列表
     */
    default List<String> findEntityCodes(String definitionId) {
        return selectObjs(Wrappers.<FlowActionDefinitionEntity>lambdaQuery()
                .select(FlowActionDefinitionEntity::getEntityCode).eq(FlowActionDefinitionEntity::getActionDefinitionId, definitionId)
                .orderByAsc(FlowActionDefinitionEntity::getEntityCode));
    }

    /**
     * 删除动作定义下的全部可见实体关系。
     *
     * @param definitionId 动作定义 ID
     */
    default void deleteByDefinitionId(String definitionId) {
        // 关联表没有逻辑删除字段，BaseMapper 保留物理删除关联关系的行为。
        delete(Wrappers.<FlowActionDefinitionEntity>lambdaQuery().eq(FlowActionDefinitionEntity::getActionDefinitionId, definitionId));
    }
}
