package com.workflow.process.sla.policy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskSlaPolicyMapper extends BaseMapper<TaskSlaPolicy> {

    @Select("""
            SELECT * FROM task_sla_policy
            WHERE policy_code = #{policyCode}
              AND status = 'PUBLISHED'
              AND deleted = 0
            ORDER BY version DESC
            LIMIT 1
            """)
    TaskSlaPolicy findLatestPublished(@Param("policyCode") String policyCode);

    @Select("""
            SELECT * FROM task_sla_policy
            WHERE status = 'PUBLISHED'
              AND deleted = 0
            ORDER BY policy_code, version DESC
            """)
    List<TaskSlaPolicy> findPublished();

    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM task_sla_policy
            WHERE policy_code = #{policyCode}
              AND deleted = 0
            """)
    int findMaxVersion(@Param("policyCode") String policyCode);
}
