package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 流程待办Mapper
 */
// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
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
     * <p>字符标识的空值使用 NULLIF；node_type 单独允许 NULL，不能以 COALESCE 空串
     * 代替，否则 Oracle 会排除普通候选任务。组名前缀使用各产品支持的 SUBSTR。</p>
     */
    String TODO_USER_SCOPE = """
            FROM process_task pt
            LEFT JOIN ACT_RU_TASK ft
              ON ft.ID_ = pt.task_id
            WHERE pt.status = 'todo' AND pt.deleted = 0
              AND EXISTS (
                SELECT 1 FROM sys_user u
                WHERE (u.username = #{userId} OR u.id = #{userId})
                  AND u.deleted = 0 AND u.status = '0'
                  AND (
                    (
                      (pt.node_type IS NULL OR pt.node_type != 'ADD_SIGN')
                      AND ft.ID_ IS NOT NULL
                      AND (
                        ft.ASSIGNEE_ IN (u.id, u.username)
                        OR (
                          NULLIF(ft.ASSIGNEE_, '') IS NULL
                          AND EXISTS (
                            SELECT 1 FROM ACT_RU_IDENTITYLINK candidate
                            WHERE candidate.TASK_ID_ = ft.ID_
                              AND candidate.TYPE_ = 'candidate'
                              AND (
                                candidate.USER_ID_ IN (u.id, u.username)
                                OR EXISTS (
                                  SELECT 1 FROM sys_user_group ug
                                  INNER JOIN sys_group g ON g.id = ug.group_id
                                  WHERE ug.user_id = u.id
                                    AND g.deleted = 0 AND g.status = '0'
                                    AND SUBSTR(candidate.GROUP_ID_, 1, 5) != 'ROLE_'
                                    AND candidate.GROUP_ID_ IN (g.id, g.group_code)
                                )
                                OR EXISTS (
                                  SELECT 1 FROM sys_user_role ur
                                  INNER JOIN sys_role r ON r.id = ur.role_id
                                  WHERE ur.user_id = u.id
                                    AND r.deleted = 0 AND r.status = '0'
                                    AND candidate.GROUP_ID_
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
                      AND pt.assignee_id IN (u.id, u.username)
                      AND EXISTS (
                        SELECT 1 FROM process_task_add_sign_user child
                        INNER JOIN process_task_add_sign add_sign ON add_sign.id = child.add_sign_id
                        INNER JOIN ACT_RU_TASK source_task
                          ON source_task.ID_ = add_sign.source_task_id
                        INNER JOIN process_task source_mirror
                          ON source_mirror.task_id = add_sign.source_task_id
                        WHERE child.generated_task_id = pt.task_id
                          AND child.status = 'TODO'
                          AND child.user_id IN (u.id, u.username)
                          AND add_sign.status = 'ACTIVE'
                          AND add_sign.process_instance_id = pt.process_instance_id
                          AND source_task.PROC_INST_ID_ = pt.process_instance_id
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
    default ProcessTask selectActionableAddSignTaskByTaskId(String userId, String taskId) {
        return selectActionableAddSignTaskByTaskIdRows(new OffsetPage<>(0, 1), userId, taskId).stream().findFirst().orElse(null);
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
    @Select("<script> SELECT pt.* " + TODO_USER_SCOPE
            + " AND pt.node_type = 'ADD_SIGN' AND pt.task_id = #{taskId}  </script>")
    List<ProcessTask> selectActionableAddSignTaskByTaskIdRows(
            @Param("page") IPage<ProcessTask> page,
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
            AND NULLIF(pt.entity_data_id, '') IS NOT NULL
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
    default String selectActionableTaskId(String userId, String entityCode, String entityDataId, String processInstanceId, boolean assignedOnly) {
        return selectActionableTaskIdRows(new OffsetPage<>(0, 1), userId, entityCode, entityDataId, processInstanceId, assignedOnly).stream().findFirst().orElse(null);
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
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
              AND (NULLIF(ft.ASSIGNEE_, '') IS NOT NULL OR pt.node_type = 'ADD_SIGN')
            </if>
            ORDER BY pt.create_time DESC, pt.id DESC
            </script>
            """)
    List<String> selectActionableTaskIdRows(
            @Param("page") IPage<String> page,
            @Param("userId") String userId,
            @Param("entityCode") String entityCode,
            @Param("entityDataId") String entityDataId,
            @Param("processInstanceId") String processInstanceId,
            @Param("assignedOnly") boolean assignedOnly);

    /**
     * 精确查询当前用户可办理的任务，并联合约束已鉴权记录与流程实例坐标。
     */
    default ProcessTask selectActionableTaskContext(String userId, String taskId, String entityCode, String entityDataId, String processInstanceId) {
        return selectActionableTaskContextRows(new OffsetPage<>(0, 1), userId, taskId, entityCode, entityDataId, processInstanceId).stream().findFirst().orElse(null);
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
    @Select("<script>SELECT pt.* " + TODO_USER_SCOPE + """
            AND pt.task_id = #{taskId}
            AND pt.entity_code = #{entityCode}
            AND pt.entity_data_id = #{entityDataId}
            AND pt.process_instance_id = #{processInstanceId}
            </script>
            """)
    List<ProcessTask> selectActionableTaskContextRows(
            @Param("page") IPage<ProcessTask> page,
            @Param("userId") String userId,
            @Param("taskId") String taskId,
            @Param("entityCode") String entityCode,
            @Param("entityDataId") String entityDataId,
            @Param("processInstanceId") String processInstanceId);

    /**
     * 查询已办列表（根据用户ID查询用户已完成的）
     */
    @Select("<script> SELECT * FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 ${@com.workflow.integration.database.api.DatabaseQuerySql@page(_databaseId, '0', '1')}) " +
            "OR pt.assignee_id = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 ${@com.workflow.integration.database.api.DatabaseQuerySql@page(_databaseId, '0', '1')})" +
            ") AND pt.status = 'done' AND pt.deleted = 0 ORDER BY pt.end_time DESC </script>")
    List<ProcessTask> selectDoneByUser(@Param("userId") String userId);
    
    /**
     * 根据流程实例ID查询待办
     */
    default List<ProcessTask> selectByProcessInstance(String processInstanceId) {
        return selectList(Wrappers.<ProcessTask>lambdaQuery()
                .eq(ProcessTask::getProcessInstanceId, processInstanceId)
                .orderByAsc(ProcessTask::getCreateTime));
    }
    
    /**
     * 根据流程实例ID查询当前待办任务（status=0）
     */
    default ProcessTask selectTodoTaskByProcessInstance(String processInstanceId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTask>(1, 1, false), Wrappers.<ProcessTask>lambdaQuery()
                .eq(ProcessTask::getProcessInstanceId, processInstanceId)
                .eq(ProcessTask::getStatus, "todo")).stream().findFirst().orElse(null);
    }
    
    /**
     * 根据Flowable任务ID查询
     */
    default ProcessTask selectByTaskId(String taskId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTask>(1, 1, false), Wrappers.<ProcessTask>lambdaQuery()
                .eq(ProcessTask::getTaskId, taskId)).stream().findFirst().orElse(null);
    }

    /**
     * 根据Flowable任务ID加锁查询待办（FOR UPDATE）。
     *
     * @param taskId Flowable任务ID
     * @return 待办记录，无则返回 null
     */
    // task_id 有唯一约束；保留删除过滤与行锁，不组合厂商分页子句。
    @Select("SELECT * FROM process_task WHERE task_id = #{taskId} AND deleted = 0 FOR UPDATE")
    ProcessTask selectByTaskIdForUpdate(@Param("taskId") String taskId);

    @Update("""
            <script>
            UPDATE process_task
            SET due_time = #{dueTime},
                response_due_time = #{responseDueTime},
                sla_status = #{slaStatus},
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE task_id = #{taskId}
              AND deleted = 0
            </script>
            """)
    int updateSlaSummary(
            @Param("taskId") String taskId,
            @Param("responseDueTime") java.time.LocalDateTime responseDueTime,
            @Param("dueTime") java.time.LocalDateTime dueTime,
            @Param("slaStatus") String slaStatus);
    
    /**
     * 完成任务
     */
    @Update("""
            <script>
            UPDATE process_task SET status = #{status}, action = #{action}, comment = #{comment}, end_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}, duration = #{duration}
             WHERE id = #{id}
            </script>
            """)
    int completeTask(@Param("id") Long id, @Param("status") String status, 
                     @Param("action") String action, @Param("comment") String comment,
                     @Param("duration") Long duration);
    
    /** 统计当前用户待办数，授权条件与列表查询保持一致。 */
    @Select("SELECT COUNT(*) " + TODO_USER_SCOPE)
    Long countTodoByUser(@Param("userId") String userId);

    /**
     * 统计用户已办数
     */
    @Select("<script> SELECT COUNT(*) FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 ${@com.workflow.integration.database.api.DatabaseQuerySql@page(_databaseId, '0', '1')}) " +
            "OR pt.assignee_id = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 ${@com.workflow.integration.database.api.DatabaseQuerySql@page(_databaseId, '0', '1')})" +
            ") AND pt.status = 'done' AND pt.deleted = 0 </script>")
    Long countDoneByUser(@Param("userId") String userId);
}
