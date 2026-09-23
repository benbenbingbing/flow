package com.workflow.process.configuration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 审批人配置 Mapper
 */
@Mapper
public interface AssigneeConfigMapper extends BaseMapper<AssigneeConfig> {

    /**
     * 根据节点配置ID查询审批人列表
     */
    default List<AssigneeConfig> findByNodeConfigId(String nodeConfigId) {
        return selectList(Wrappers.<AssigneeConfig>lambdaQuery()
                .eq(AssigneeConfig::getNodeConfigId, nodeConfigId)
                .orderByAsc(AssigneeConfig::getPriority));
    }
}
