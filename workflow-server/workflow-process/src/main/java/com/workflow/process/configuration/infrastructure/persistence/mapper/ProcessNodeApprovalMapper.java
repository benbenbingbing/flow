package com.workflow.process.configuration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.configuration.infrastructure.persistence.record.ProcessNodeApproval;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程节点审批配置Mapper
 */
@Mapper
public interface ProcessNodeApprovalMapper extends BaseMapper<ProcessNodeApproval> {
    
    /**
     * 查询流程的节点审批配置
     *
     * @param processConfigId 流程配置ID，后续用于查询流程配置ID时定位或关联目标
     * @return 流程节点审批集合，供调用方遍历或展示
     */
    default List<ProcessNodeApproval> selectByProcessConfigId(String processConfigId) {
        return selectList(Wrappers.<ProcessNodeApproval>lambdaQuery()
                .eq(ProcessNodeApproval::getProcessConfigId, processConfigId));
    }
    
    /**
     * 查询节点的审批配置
     *
     * @param processConfigId 流程配置ID，后续用于查询节点ID时定位或关联目标
     * @param nodeId 节点ID，后续用于查询节点ID时定位或关联目标
     * @return 查询后的节点ID结果，供调用方继续处理
     */
    default ProcessNodeApproval selectByNodeId(String processConfigId, String nodeId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessNodeApproval>(1, 1, false), Wrappers.<ProcessNodeApproval>lambdaQuery()
                .eq(ProcessNodeApproval::getProcessConfigId, processConfigId)
                .eq(ProcessNodeApproval::getNodeId, nodeId)).stream().findFirst().orElse(null);
    }
    
    /**
     * 删除流程的所有节点审批配置
     */
    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID时定位或关联目标
     */
    default void deleteByProcessConfigId(String processConfigId) {
        delete(Wrappers.<ProcessNodeApproval>lambdaQuery()
                .eq(ProcessNodeApproval::getProcessConfigId, processConfigId));
    }
}
