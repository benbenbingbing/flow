package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 流程待办Mapper
 */
@Mapper
public interface ProcessTaskMapper extends BaseMapper<ProcessTask> {
    
    /**
     * 待办列表和统计共用同一授权范围，以引擎当前办理人及候选关系为准。
     *
     * <p>本地 assignee_id 是展示投影，旧任务可能将多候选人压成逗号串，
     * 混合用户/组时还可能只保留组。直接读取引擎关系可兼容这些已存在任务，
     * 并确保任务被他人认领后，即使本地投影滞后也不再显示给候选人。</p>
     */
    String TODO_USER_SCOPE = """
            FROM process_task pt
            INNER JOIN ACT_RU_TASK ft
              ON ft.ID_ COLLATE utf8mb4_unicode_ci = pt.task_id
            WHERE pt.status = 'todo' AND pt.deleted = 0
              AND EXISTS (
                SELECT 1 FROM sys_user u
                WHERE (u.username = #{userId} OR u.id = #{userId})
                  AND u.deleted = 0 AND u.status = '0'
                  AND (
                    ft.ASSIGNEE_ COLLATE utf8mb4_unicode_ci IN (u.id, u.username)
                    OR (
                      (ft.ASSIGNEE_ IS NULL OR ft.ASSIGNEE_ = '')
                      AND EXISTS (
                        SELECT 1 FROM ACT_RU_IDENTITYLINK candidate
                        WHERE candidate.TASK_ID_ = ft.ID_
                          AND candidate.TYPE_ = 'candidate'
                          AND (
                            candidate.USER_ID_ COLLATE utf8mb4_unicode_ci IN (u.id, u.username)
                            OR EXISTS (
                              SELECT 1 FROM sys_user_group ug
                              INNER JOIN sys_group g ON g.id = ug.group_id
                              WHERE ug.user_id = u.id
                                AND g.deleted = 0 AND g.status = '0'
                                AND LEFT(candidate.GROUP_ID_, 5) != 'ROLE_'
                                AND candidate.GROUP_ID_ COLLATE utf8mb4_unicode_ci IN (g.id, g.group_code)
                            )
                            OR EXISTS (
                              SELECT 1 FROM sys_user_role ur
                              INNER JOIN sys_role r ON r.id = ur.role_id
                              WHERE ur.user_id = u.id
                                AND r.deleted = 0 AND r.status = '0'
                                AND candidate.GROUP_ID_ COLLATE utf8mb4_unicode_ci
                                    IN (CONCAT('ROLE_', r.id), CONCAT('ROLE_', r.role_code))
                            )
                          )
                      )
                    )
                  )
              )
            """;

    /** 查询当前用户的待办，同时接受用户 ID 和用户名。 */
    @Select("SELECT pt.* " + TODO_USER_SCOPE + " ORDER BY pt.create_time DESC")
    List<ProcessTask> selectTodoByUser(@Param("userId") String userId);

    /**
     * 查询已办列表（根据用户ID查询用户已完成的）
     */
    @Select("SELECT * FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 LIMIT 1) " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 LIMIT 1)" +
            ") AND pt.status = 'done' AND pt.deleted = 0 ORDER BY pt.end_time DESC")
    List<ProcessTask> selectDoneByUser(@Param("userId") String userId);
    
    /**
     * 根据流程实例ID查询待办
     */
    @Select("SELECT * FROM process_task WHERE process_instance_id = #{processInstanceId} AND deleted = 0 ORDER BY create_time")
    List<ProcessTask> selectByProcessInstance(@Param("processInstanceId") String processInstanceId);
    
    /**
     * 根据流程实例ID查询当前待办任务（status=0）
     */
    @Select("SELECT * FROM process_task WHERE process_instance_id = #{processInstanceId} AND status = 'todo' AND deleted = 0 LIMIT 1")
    ProcessTask selectTodoTaskByProcessInstance(@Param("processInstanceId") String processInstanceId);
    
    /**
     * 根据Flowable任务ID查询
     */
    @Select("SELECT * FROM process_task WHERE task_id = #{taskId} AND deleted = 0 LIMIT 1")
    ProcessTask selectByTaskId(@Param("taskId") String taskId);

    /**
     * 根据Flowable任务ID加锁查询待办（FOR UPDATE）。
     *
     * @param taskId Flowable任务ID
     * @return 待办记录，无则返回 null
     */
    @Select("SELECT * FROM process_task WHERE task_id = #{taskId} AND deleted = 0 LIMIT 1 FOR UPDATE")
    ProcessTask selectByTaskIdForUpdate(@Param("taskId") String taskId);

    @Update("""
            UPDATE process_task
            SET due_time = #{dueTime},
                response_due_time = #{responseDueTime},
                sla_status = #{slaStatus},
                update_time = UTC_TIMESTAMP(6)
            WHERE task_id = #{taskId}
              AND deleted = 0
            """)
    int updateSlaSummary(
            @Param("taskId") String taskId,
            @Param("responseDueTime") java.time.LocalDateTime responseDueTime,
            @Param("dueTime") java.time.LocalDateTime dueTime,
            @Param("slaStatus") String slaStatus);
    
    /**
     * 完成任务
     */
    @Update("UPDATE process_task SET status = #{status}, action = #{action}, comment = #{comment}, " +
            "end_time = NOW(), duration = #{duration} WHERE id = #{id}")
    int completeTask(@Param("id") Long id, @Param("status") String status, 
                     @Param("action") String action, @Param("comment") String comment,
                     @Param("duration") Long duration);
    
    /** 统计当前用户待办数，授权条件与列表查询保持一致。 */
    @Select("SELECT COUNT(*) " + TODO_USER_SCOPE)
    Long countTodoByUser(@Param("userId") String userId);

    /**
     * 统计用户已办数
     */
    @Select("SELECT COUNT(*) FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 LIMIT 1) " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 LIMIT 1)" +
            ") AND pt.status = 'done' AND pt.deleted = 0")
    Long countDoneByUser(@Param("userId") String userId);
}
