package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProcessTaskSlaMapper extends BaseMapper<ProcessTaskSla> {

    @Select("SELECT * FROM process_task_sla WHERE task_id = #{taskId} LIMIT 1")
    ProcessTaskSla findByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT * FROM process_task_sla
            WHERE task_id = #{taskId}
            LIMIT 1 FOR UPDATE
            """)
    ProcessTaskSla findByTaskIdForUpdate(@Param("taskId") String taskId);

    @Select("""
            SELECT * FROM process_task_sla
            WHERE process_instance_id = #{processInstanceId}
            ORDER BY create_time
            """)
    List<ProcessTaskSla> findByProcessInstanceId(
            @Param("processInstanceId") String processInstanceId);

    @Select("""
            SELECT * FROM process_task_sla
            WHERE overall_status = 'PAUSED'
              AND pause_started_at IS NOT NULL
            ORDER BY pause_started_at
            LIMIT #{limit}
            """)
    List<ProcessTaskSla> findPaused(@Param("limit") int limit);

    @Update("""
            UPDATE process_task_sla
            SET current_assignee_id = #{assignee},
                version = version + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE task_id = #{taskId}
              AND overall_status IN ('RUNNING', 'PAUSED')
            """)
    int updateAssignee(
            @Param("taskId") String taskId,
            @Param("assignee") String assignee);

    @Select("""
            <script>
            SELECT * FROM process_task_sla
            WHERE 1 = 1
            <if test='status != null and status != ""'>
              AND overall_status = #{status}
            </if>
            <if test='processKey != null and processKey != ""'>
              AND process_key = #{processKey}
            </if>
            <if test='assignee != null and assignee != ""'>
              AND current_assignee_id = #{assignee}
            </if>
            <if test='keyword != null and keyword != ""'>
              AND (node_name LIKE CONCAT('%', #{keyword}, '%')
                OR business_key LIKE CONCAT('%', #{keyword}, '%')
                OR policy_code LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY
              CASE overall_status
                WHEN 'BREACHED' THEN 0
                WHEN 'RUNNING' THEN 1
                WHEN 'PAUSED' THEN 2
                ELSE 3
              END,
              completion_due_at,
              create_time DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<ProcessTaskSla> findMonitorPage(
            @Param("status") String status,
            @Param("processKey") String processKey,
            @Param("assignee") String assignee,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM process_task_sla
            WHERE 1 = 1
            <if test='status != null and status != ""'>
              AND overall_status = #{status}
            </if>
            <if test='processKey != null and processKey != ""'>
              AND process_key = #{processKey}
            </if>
            <if test='assignee != null and assignee != ""'>
              AND current_assignee_id = #{assignee}
            </if>
            <if test='keyword != null and keyword != ""'>
              AND (node_name LIKE CONCAT('%', #{keyword}, '%')
                OR business_key LIKE CONCAT('%', #{keyword}, '%')
                OR policy_code LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countMonitor(
            @Param("status") String status,
            @Param("processKey") String processKey,
            @Param("assignee") String assignee,
            @Param("keyword") String keyword);

    @Select("""
            SELECT overall_status AS status, COUNT(*) AS total
            FROM process_task_sla
            GROUP BY overall_status
            """)
    List<Map<String, Object>> statusStatistics();
}
