package com.workflow.process.configuration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.configuration.infrastructure.persistence.record.ProcessNodeApprovalOption;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 节点审批选项 Mapper
 * 提供审批选项的查询与删除操作
 */
@Mapper
public interface ProcessNodeApprovalOptionMapper
        extends BaseMapper<ProcessNodeApprovalOption> {

    /**
     * 根据审批配置ID查询审批选项列表（按排序号升序）。
     *
     * @param approvalConfigId 审批配置ID
     * @return 审批选项列表
     */
    default List<ProcessNodeApprovalOption> findByApprovalConfigId(String approvalConfigId) {
        return selectList(Wrappers.<ProcessNodeApprovalOption>lambdaQuery()
                .eq(ProcessNodeApprovalOption::getApprovalConfigId, approvalConfigId)
                .orderByAsc(ProcessNodeApprovalOption::getSortOrder));
    }

    /**
     * 根据审批配置ID删除其下所有审批选项。
     *
     * @param approvalConfigId 审批配置ID
     */
    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param approvalConfigId 审批配置ID，后续用于删除审批配置ID时定位或关联目标
     */
    default void deleteByApprovalConfigId(String approvalConfigId) {
        delete(Wrappers.<ProcessNodeApprovalOption>lambdaQuery()
                .eq(ProcessNodeApprovalOption::getApprovalConfigId, approvalConfigId));
    }
}
