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
     * 并确保任务被他人认领后，即使本地投影滞后也不再显示给候选人。
     * ADD_SIGN 子任务由本地加签编排管理，仅在用户明细、ACTIVE 父加签与
     * 仍存在的源引擎任务共同验证后放行；WAITING_SOURCE 已无可办理子任务。</p>
     */
    String TODO_USER_SCOPE = """
            FROM process_task pt
            LEFT JOIN ACT_RU_TASK ft
              ON ft.ID_ COLLATE utf8mb4_unicode_ci = pt.task_id
            WHERE pt.status = 'todo' AND pt.deleted = 0
              AND EXISTS (
                SELECT 1 FROM sys_user u
                WHERE (u.username = #{userId} OR u.id = #{userId})
                  AND u.deleted = 0 AND u.status = '0'
                  AND (
                    (
                      COALESCE(pt.node_type, '') != 'ADD_SIGN'
                      AND ft.ID_ IS NOT NULL
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
                    OR (
                      pt.node_type = 'ADD_SIGN'
                      AND ft.ID_ IS NULL
                      AND pt.assignee_type = 'user'
                      AND pt.assignee_id COLLATE utf8mb4_unicode_ci IN (u.id, u.username)
                      AND EXISTS (
                        SELECT 1 FROM process_task_add_sign_user child
                        INNER JOIN process_task_add_sign add_sign ON add_sign.id = child.add_sign_id
                        INNER JOIN ACT_RU_TASK source_task
                          ON source_task.ID_ COLLATE utf8mb4_unicode_ci = add_sign.source_task_id
                        INNER JOIN process_task source_mirror
                          ON source_mirror.task_id = add_sign.source_task_id
                        WHERE child.generated_task_id = pt.task_id
                          AND child.status = 'TODO'
                          AND child.user_id COLLATE utf8mb4_unicode_ci IN (u.id, u.username)
                          AND add_sign.status = 'ACTIVE'
                          AND add_sign.process_instance_id = pt.process_instance_id
                          AND source_task.PROC_INST_ID_ COLLATE utf8mb4_unicode_ci = pt.process_instance_id
                          AND source_mirror.process_instance_id = pt.process_instance_id
                          AND source_mirror.deleted = 0 AND source_mirror.status IN ('todo', 'waiting')
                          AND (source_mirror.entity_code = pt.entity_code
                               OR source_mirror.entity_code IS NULL AND pt.entity_code IS NULL)
                          AND (source_mirror.entity_data_id = pt.entity_data_id
                               OR source_mirror.entity_data_id IS NULL AND pt.entity_data_id IS NULL)
                      )
                    )
                  )
              )
            """;

    /** 查询当前用户的待办，同时接受用户 ID 和用户名。 */
    @Select("SELECT pt.* " + TODO_USER_SCOPE + " ORDER BY pt.create_time DESC")
    List<ProcessTask> selectTodoByUser(@Param("userId") String userId);

    /** 按确切任务 ID 查询当前用户可办理的本地加签子任务，供详情与进度入口安全复用。 */
    @Select("SELECT pt.* " + TODO_USER_SCOPE
            + " AND pt.node_type = 'ADD_SIGN' AND pt.task_id = #{taskId} LIMIT 1")
    ProcessTask selectActionableAddSignTaskByTaskId(
            @Param("userId") String userId,
            @Param("taskId") String taskId);

    /** 实例只读入口使用的本地加签参与判定，保留普通任务自身的访问语义。 */
    @Select("SELECT COUNT(*) " + TODO_USER_SCOPE
            + " AND pt.node_type = 'ADD_SIGN' AND pt.process_instance_id = #{processInstanceId}")
    long countActionableAddSignTasksInProcess(
            @Param("userId") String userId,
            @Param("processInstanceId") String processInstanceId);

    /**
     * 返回指定实体的可审批记录 ID，与待办列表及审批入口共用实际候选身份范围。
     * 显式约束 entity_code，不能让其他实体恰好相同的记录 ID 获得 HAS_TODO 可见性。
     */
    @Select("SELECT DISTINCT pt.entity_data_id " + TODO_USER_SCOPE + """
            AND pt.entity_code = #{entityCode}
            AND pt.entity_data_id IS NOT NULL AND pt.entity_data_id != ''
            ORDER BY pt.entity_data_id
            """)
    List<String> selectActionableEntityDataIds(
            @Param("userId") String userId,
            @Param("entityCode") String entityCode);

    /**
     * 绑定业务记录上当前用户可审批的任务，与待办列表复用引擎身份范围。
     * 实体/流程坐标同时存在时联合匹配，不能把摘要中的兄弟任务绑定给当前用户。
     * assignedOnly 为 true 时只接受实际办理人，供非审批的 CURRENT_ASSIGNEE 权限使用。
     */
    @Select("<script>SELECT pt.task_id " + TODO_USER_SCOPE + """
            <choose>
              <when test="entityCode != null and entityDataId != null">
                AND pt.entity_data_id = #{entityDataId} AND pt.entity_code = #{entityCode}
              </when>
              <otherwise>
                <if test="processInstanceId == null">AND 1 = 0</if>
              </otherwise>
            </choose>
            <if test="processInstanceId != null">
              AND pt.process_instance_id = #{processInstanceId}
            </if>
            <if test="assignedOnly">
              AND ((ft.ASSIGNEE_ IS NOT NULL AND ft.ASSIGNEE_ != '') OR pt.node_type = 'ADD_SIGN')
            </if>
            ORDER BY pt.create_time DESC, pt.id DESC LIMIT 1
            </script>
            """)
    String selectActionableTaskId(
            @Param("userId") String userId,
            @Param("entityCode") String entityCode,
            @Param("entityDataId") String entityDataId,
            @Param("processInstanceId") String processInstanceId,
            @Param("assignedOnly") boolean assignedOnly);

    /**
     * 精确查询当前用户可办理的任务，并联合约束已鉴权记录与流程实例坐标。
     */
    @Select("<script>SELECT pt.* " + TODO_USER_SCOPE + """
            AND pt.task_id = #{taskId}
            AND pt.entity_code = #{entityCode}
            AND pt.entity_data_id = #{entityDataId}
            AND pt.process_instance_id = #{processInstanceId}
            LIMIT 1
            </script>
            """)
    ProcessTask selectActionableTaskContext(
            @Param("userId") String userId,
            @Param("taskId") String taskId,
            @Param("entityCode") String entityCode,
            @Param("entityDataId") String entityDataId,
            @Param("processInstanceId") String processInstanceId);

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
