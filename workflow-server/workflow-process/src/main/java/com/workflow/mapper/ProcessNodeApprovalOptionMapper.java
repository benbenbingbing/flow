package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessNodeApprovalOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcessNodeApprovalOptionMapper
        extends BaseMapper<ProcessNodeApprovalOption> {

    @Select("SELECT * FROM process_node_approval_option "
            + "WHERE approval_config_id = #{approvalConfigId} ORDER BY sort_order")
    List<ProcessNodeApprovalOption> findByApprovalConfigId(
            @Param("approvalConfigId") String approvalConfigId);

    @Delete("DELETE FROM process_node_approval_option "
            + "WHERE approval_config_id = #{approvalConfigId}")
    void deleteByApprovalConfigId(@Param("approvalConfigId") String approvalConfigId);
}
