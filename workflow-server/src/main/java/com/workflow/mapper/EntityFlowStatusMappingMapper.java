package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFlowStatusMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 实体流程状态映射 Mapper
 */
@Mapper
public interface EntityFlowStatusMappingMapper extends BaseMapper<EntityFlowStatusMapping> {
    
    /**
     * 根据流程配置ID查询状态映射
     */
    @Select("SELECT * FROM entity_flow_status_mapping WHERE process_config_id = #{processConfigId} AND deleted = 0 ORDER BY sort_order")
    List<EntityFlowStatusMapping> findByProcessConfigId(@Param("processConfigId") String processConfigId);
    
    /**
     * 根据流程标识查询状态映射
     */
    @Select("SELECT * FROM entity_flow_status_mapping WHERE process_key = #{processKey} AND deleted = 0 ORDER BY sort_order")
    List<EntityFlowStatusMapping> findByProcessKey(@Param("processKey") String processKey);
    
    /**
     * 根据流程配置ID和源节点查询
     */
    @Select("SELECT * FROM entity_flow_status_mapping WHERE process_config_id = #{processConfigId} AND source_node_id = #{sourceNodeId} AND deleted = 0")
    List<EntityFlowStatusMapping> findByProcessAndSourceNode(@Param("processConfigId") String processConfigId, 
                                                             @Param("sourceNodeId") String sourceNodeId);
    
    /**
     * 根据流程配置ID、源节点和目标节点查询
     */
    @Select("SELECT * FROM entity_flow_status_mapping WHERE process_config_id = #{processConfigId} AND source_node_id = #{sourceNodeId} AND target_node_id = #{targetNodeId} AND deleted = 0 LIMIT 1")
    EntityFlowStatusMapping findByProcessAndNodes(@Param("processConfigId") String processConfigId,
                                                   @Param("sourceNodeId") String sourceNodeId,
                                                   @Param("targetNodeId") String targetNodeId);
    
    /**
     * 根据实体编码查询
     */
    @Select("SELECT * FROM entity_flow_status_mapping WHERE entity_code = #{entityCode} AND deleted = 0 ORDER BY sort_order")
    List<EntityFlowStatusMapping> findByEntityCode(@Param("entityCode") String entityCode);
    
    /**
     * 物理删除（避免与已删除数据产生唯一索引冲突）
     */
    @Update("DELETE FROM entity_flow_status_mapping WHERE process_config_id = #{processConfigId}")
    void deleteByProcessConfigId(@Param("processConfigId") String processConfigId);
}
