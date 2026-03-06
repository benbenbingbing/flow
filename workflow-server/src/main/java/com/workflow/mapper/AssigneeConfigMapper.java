package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.AssigneeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审批人配置 Mapper
 */
@Mapper
public interface AssigneeConfigMapper extends BaseMapper<AssigneeConfig> {

    /**
     * 根据节点配置ID查询审批人列表
     */
    @Select("SELECT * FROM assignee_config WHERE node_config_id = #{nodeConfigId} ORDER BY priority ASC")
    List<AssigneeConfig> findByNodeConfigId(@Param("nodeConfigId") String nodeConfigId);
}
