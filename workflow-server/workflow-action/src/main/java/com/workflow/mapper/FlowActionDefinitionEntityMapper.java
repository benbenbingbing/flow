package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowActionDefinitionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT entity_code FROM process_action_definition_entity "
            + "WHERE action_definition_id = #{definitionId} ORDER BY entity_code")
    List<String> findEntityCodes(@Param("definitionId") String definitionId);

    /**
     * 删除动作定义下的全部可见实体关系。
     *
     * @param definitionId 动作定义 ID
     */
    @Delete("DELETE FROM process_action_definition_entity "
            + "WHERE action_definition_id = #{definitionId}")
    void deleteByDefinitionId(@Param("definitionId") String definitionId);
}
