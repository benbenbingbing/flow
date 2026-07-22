package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowActionDefinitionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FlowActionDefinitionEntityMapper
        extends BaseMapper<FlowActionDefinitionEntity> {

    @Select("SELECT entity_code FROM process_action_definition_entity "
            + "WHERE action_definition_id = #{definitionId} ORDER BY entity_code")
    List<String> findEntityCodes(@Param("definitionId") String definitionId);

    @Delete("DELETE FROM process_action_definition_entity "
            + "WHERE action_definition_id = #{definitionId}")
    void deleteByDefinitionId(@Param("definitionId") String definitionId);
}
