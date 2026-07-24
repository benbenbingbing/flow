package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FlowActionExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程动作执行记录 Mapper。
 *
 * <p>提供执行记录的就绪查询、乐观抢占、中断恢复与按流程实例查询等自定义 SQL。</p>
 */
@Mapper
public interface FlowActionExecutionMapper extends BaseMapper<FlowActionExecution> {

    /**
     * 查询就绪的执行记录：状态为 PENDING 或已到重试时间的 FAILED。
     *
     * @param now   当前时间
     * @param limit 最多返回条数
     * @return 就绪执行记录列表
     */
    @Select("SELECT * FROM process_action_execution " +
            "WHERE status IN ('PENDING', 'FAILED') " +
            "  AND (next_retry_time IS NULL OR next_retry_time <= #{now}) " +
            "ORDER BY created_at " +
            "LIMIT #{limit}")
    List<FlowActionExecution> findReady(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 乐观抢占执行记录：仅当原状态为 PENDING/FAILED 时将其置为 RUNNING。
     *
     * @param id  执行记录 ID
     * @param now 当前时间
     * @return 更新行数，1 表示抢占成功，0 表示已被其他线程抢占
     */
    @Update("UPDATE process_action_execution " +
            "SET status = 'RUNNING', started_at = #{now}, updated_at = #{now} " +
            "WHERE id = #{id} " +
            "  AND status IN ('PENDING', 'FAILED')")
    int claim(@Param("id") String id, @Param("now") LocalDateTime now);

    /**
     * 恢复中断记录：将早于 staleBefore 仍处于 RUNNING 的记录改回 FAILED 以便重新抢占。
     *
     * @param now        当前时间
     * @param staleBefore 中断判定时间阈值
     * @return 恢复的记录条数
     */
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

    /**
     * 按流程实例查询全部执行记录（按创建时间倒序）。
     *
     * @param processInstanceId 流程实例 ID
     * @return 执行记录列表
     */
    @Select("SELECT * FROM process_action_execution " +
            "WHERE process_instance_id = #{processInstanceId} " +
            "ORDER BY created_at DESC")
    List<FlowActionExecution> findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}
