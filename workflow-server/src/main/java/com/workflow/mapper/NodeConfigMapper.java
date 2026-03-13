package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.NodeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流程节点配置 Mapper
 */
@Mapper
public interface NodeConfigMapper extends BaseMapper<NodeConfig> {

    /**
     * 根据流程配置ID查询节点列表
     */
    @Select("SELECT * FROM node_config WHERE process_config_id = #{processConfigId} ORDER BY created_at ASC")
    List<NodeConfig> findByProcessConfigId(@Param("processConfigId") String processConfigId);

    /**
     * 根据流程配置ID删除节点
     */
    @Select("DELETE FROM node_config WHERE process_config_id = #{processConfigId}")
    void deleteByProcessConfigId(@Param("processConfigId") String processConfigId);
    
    /**
     * 根据节点ID和流程配置ID查询节点配置
     */
    @Select("SELECT * FROM node_config WHERE node_id = #{nodeId} AND process_config_id = #{processConfigId} LIMIT 1")
    NodeConfig selectByNodeIdAndProcessId(@Param("nodeId") String nodeId, @Param("processConfigId") String processConfigId);
}
