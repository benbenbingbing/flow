package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessNodeForm;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流程节点表单绑定Mapper
 */
@Mapper
public interface ProcessNodeFormMapper extends BaseMapper<ProcessNodeForm> {
    
    /**
     * 查询流程的节点表单绑定
     */
    @Select("SELECT * FROM process_node_form WHERE process_config_id = #{processConfigId} ORDER BY node_id ASC, sort_order ASC, create_time ASC")
    List<ProcessNodeForm> selectByProcessConfigId(@Param("processConfigId") String processConfigId);
    
    /**
     * 查询节点的表单绑定
     */
    @Select("SELECT * FROM process_node_form WHERE process_config_id = #{processConfigId} AND node_id = #{nodeId} ORDER BY sort_order ASC, create_time ASC LIMIT 1")
    ProcessNodeForm selectByNodeId(@Param("processConfigId") String processConfigId, @Param("nodeId") String nodeId);

    /**
     * 查询节点的所有表单绑定
     */
    @Select("SELECT * FROM process_node_form WHERE process_config_id = #{processConfigId} AND node_id = #{nodeId} ORDER BY sort_order ASC, create_time ASC")
    List<ProcessNodeForm> selectListByNodeId(@Param("processConfigId") String processConfigId, @Param("nodeId") String nodeId);

    /**
     * 删除节点的所有表单绑定
     */
    @Delete("DELETE FROM process_node_form WHERE process_config_id = #{processConfigId} AND node_id = #{nodeId}")
    void deleteByProcessConfigIdAndNodeId(@Param("processConfigId") String processConfigId, @Param("nodeId") String nodeId);
    
    /**
     * 删除流程的所有节点表单绑定
     */
    @Delete("DELETE FROM process_node_form WHERE process_config_id = #{processConfigId}")
    void deleteByProcessConfigId(@Param("processConfigId") String processConfigId);
}
