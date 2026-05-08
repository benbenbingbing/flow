package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessNodeApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流程节点审批配置Mapper
 */
@Mapper
public interface ProcessNodeApprovalMapper extends BaseMapper<ProcessNodeApproval> {
    
    /**
     * 查询流程的节点审批配置
     */
    @Select("SELECT * FROM process_node_approval WHERE process_config_id = #{processConfigId}")
    List<ProcessNodeApproval> selectByProcessConfigId(@Param("processConfigId") String processConfigId);
    
    /**
     * 查询节点的审批配置
     */
    @Select("SELECT * FROM process_node_approval WHERE process_config_id = #{processConfigId} AND node_id = #{nodeId} LIMIT 1")
    ProcessNodeApproval selectByNodeId(@Param("processConfigId") String processConfigId, @Param("nodeId") String nodeId);
    
    /**
     * 删除流程的所有节点审批配置
     */
    @Select("DELETE FROM process_node_approval WHERE process_config_id = #{processConfigId}")
    void deleteByProcessConfigId(@Param("processConfigId") String processConfigId);
}
