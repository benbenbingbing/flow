package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessNodeApprovalOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT * FROM process_node_approval_option "
            + "WHERE approval_config_id = #{approvalConfigId} ORDER BY sort_order")
    List<ProcessNodeApprovalOption> findByApprovalConfigId(
            @Param("approvalConfigId") String approvalConfigId);

    /**
     * 根据审批配置ID删除其下所有审批选项。
     *
     * @param approvalConfigId 审批配置ID
     */
    @Delete("DELETE FROM process_node_approval_option "
            + "WHERE approval_config_id = #{approvalConfigId}")
    void deleteByApprovalConfigId(@Param("approvalConfigId") String approvalConfigId);
}
