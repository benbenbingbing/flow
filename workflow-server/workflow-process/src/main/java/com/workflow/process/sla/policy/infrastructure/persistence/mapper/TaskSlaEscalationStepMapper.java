package com.workflow.process.sla.policy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaEscalationStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskSlaEscalationStepMapper
        extends BaseMapper<TaskSlaEscalationStep> {

    @Select("""
            SELECT * FROM task_sla_escalation_step
            WHERE policy_id = #{policyId}
              AND enabled = 1
            ORDER BY sort_order, create_time
            """)
    List<TaskSlaEscalationStep> findEnabledByPolicyId(
            @Param("policyId") String policyId);

    @Select("""
            SELECT * FROM task_sla_escalation_step
            WHERE policy_id = #{policyId}
            ORDER BY sort_order, create_time
            """)
    List<TaskSlaEscalationStep> findByPolicyId(
            @Param("policyId") String policyId);

    @Delete("DELETE FROM task_sla_escalation_step WHERE policy_id = #{policyId}")
    int deleteByPolicyId(@Param("policyId") String policyId);
}
