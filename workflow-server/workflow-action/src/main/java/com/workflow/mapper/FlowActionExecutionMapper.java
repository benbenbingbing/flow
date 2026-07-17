package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowActionExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FlowActionExecutionMapper extends BaseMapper<FlowActionExecution> {

    @Select("SELECT * FROM process_action_execution " +
            "WHERE status IN ('PENDING', 'FAILED') " +
            "  AND (next_retry_time IS NULL OR next_retry_time <= #{now}) " +
            "ORDER BY created_at " +
            "LIMIT #{limit}")
    List<FlowActionExecution> findReady(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE process_action_execution " +
            "SET status = 'RUNNING', started_at = #{now}, updated_at = #{now} " +
            "WHERE id = #{id} " +
            "  AND status IN ('PENDING', 'FAILED')")
    int claim(@Param("id") String id, @Param("now") LocalDateTime now);

    @Update("UPDATE process_action_execution " +
            "SET status = 'FAILED', " +
            "    next_retry_time = #{now}, " +
            "    error_message = '执行进程中断，已自动恢复', " +
            "    updated_at = #{now} " +
            "WHERE status = 'RUNNING' " +
            "  AND started_at < #{staleBefore}")
    int recoverStale(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore);

    @Select("SELECT * FROM process_action_execution " +
            "WHERE process_instance_id = #{processInstanceId} " +
            "ORDER BY created_at DESC")
    List<FlowActionExecution> findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}
