package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowActionDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 流程动作定义 Mapper。
 *
 * <p>提供动作处理器目录的活跃记录查询能力。</p>
 */
@Mapper
public interface FlowActionDefinitionMapper extends BaseMapper<FlowActionDefinition> {

    /**
     * 查询全部未删除的动作定义，按展示名与处理器名排序。
     *
     * @return 活跃动作定义列表
     */
    @Select("SELECT * FROM process_action_definition WHERE deleted = 0 ORDER BY display_name, handler_name")
    List<FlowActionDefinition> findAllActive();

    /**
     * 按主键查询未删除的动作定义。
     *
     * @param id 动作定义 ID
     * @return 动作定义；不存在返回 Optional.empty()
     */
    @Select("SELECT * FROM process_action_definition WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Optional<FlowActionDefinition> findActiveById(@Param("id") String id);

    /**
     * 按处理器 Bean 名称查询未删除的动作定义。
     *
     * @param handlerName 处理器 Bean 名称
     * @return 动作定义；不存在返回 Optional.empty()
     */
    @Select("SELECT * FROM process_action_definition WHERE handler_name = #{handlerName} AND deleted = 0 LIMIT 1")
    Optional<FlowActionDefinition> findByHandlerName(@Param("handlerName") String handlerName);
}
