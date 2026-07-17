package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowActionDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FlowActionDefinitionMapper extends BaseMapper<FlowActionDefinition> {

    @Select("SELECT * FROM process_action_definition WHERE deleted = 0 ORDER BY display_name, handler_name")
    List<FlowActionDefinition> findAllActive();

    @Select("SELECT * FROM process_action_definition WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Optional<FlowActionDefinition> findActiveById(@Param("id") String id);

    @Select("SELECT * FROM process_action_definition WHERE handler_name = #{handlerName} AND deleted = 0 LIMIT 1")
    Optional<FlowActionDefinition> findByHandlerName(@Param("handlerName") String handlerName);
}
