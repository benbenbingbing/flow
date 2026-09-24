package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.infrastructure.persistence.record.DoneTaskAggregate;
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

    /** 已办明细、计数和时长聚合共享身份范围，避免统计口径随查询分叉。 */
    String DONE_USER_SCOPE = " FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 ${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, '0', '1')}) " +
            "OR pt.assignee_id = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 ${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, '0', '1')})" +
            ") AND pt.status = 'done' AND pt.deleted = 0 ";
    
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

    /**
     * 查询当前用户的待办，同时接受用户 ID 和用户名。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 流程任务集合，供调用方遍历或展示
     */
    @Select("SELECT pt.* " + TODO_USER_SCOPE + " ORDER BY pt.create_time DESC")
    List<ProcessTask> selectTodoByUser(@Param("userId") String userId);

    /**
     * 按确切任务 ID 查询当前用户可办理的本地加签子任务，供详情与进度入口安全复用。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 查询后的可执行添加签名任务任务ID结果，供调用方继续处理
     */
    default ProcessTask selectActionableAddSignTaskByTaskId(String userId, String taskId) {
        return selectActionableAddSignTaskByTaskIdRows(new OffsetPage<>(0, 1), userId, taskId).stream().findFirst().orElse(null);
    }

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 流程任务集合，供调用方遍历或展示
     */
    @Select("<script> SELECT pt.* " + TODO_USER_SCOPE
            + " AND pt.node_type = 'ADD_SIGN' AND pt.task_id = #{taskId}  </script>")
    List<ProcessTask> selectActionableAddSignTaskByTaskIdRows(
            @Param("page") IPage<ProcessTask> page,
            @Param("userId") String userId,
            @Param("taskId") String taskId);

    /**
     * 实例只读入口使用的本地加签参与判定，保留普通任务自身的访问语义。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的可执行添加签名任务集合流程数量
     */
    @Select("SELECT COUNT(*) " + TODO_USER_SCOPE
            + " AND pt.node_type = 'ADD_SIGN' AND pt.process_instance_id = #{processInstanceId}")
    long countActionableAddSignTasksInProcess(
            @Param("userId") String userId,
            @Param("processInstanceId") String processInstanceId);

    /**
     * 返回指定实体的可审批记录 ID，与待办列表及审批入口共用实际候选身份范围。
     * 显式约束 entity_code，不能让其他实体恰好相同的记录 ID 获得 HAS_TODO 可见性。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 流程任务集合，供调用方遍历或展示
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
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param assignedOnly {@code assigned}仅，供本方法查询可执行任务ID时使用
     * @return 查询后的可执行任务ID文本，供调用方比较或展示
     */
    default String selectActionableTaskId(String userId, String entityCode, String entityDataId, String processInstanceId, boolean assignedOnly) {
        return selectActionableTaskIdRows(new OffsetPage<>(0, 1), userId, entityCode, entityDataId, processInstanceId, assignedOnly).stream().findFirst().orElse(null);
    }

    /**
     * 一批记录复用相同的引擎身份范围，最新任务优先；不能把不同记录的实体/流程坐标做笛卡尔匹配。
     * 调用方每批最多 100 组，空集合明确返回空，避免退化为该用户全部待办查询。
     */
    @Select("<script>SELECT pt.task_id, pt.node_name, pt.entity_code, pt.entity_data_id, pt.process_instance_id, "
            + "CASE WHEN NULLIF(ft.ASSIGNEE_, '') IS NOT NULL OR pt.node_type = 'ADD_SIGN' THEN 1 ELSE 0 END AS assigned_flag "
            + TODO_USER_SCOPE + """
            <choose>
              <when test="records != null and records.size() > 0">
                AND (
                  <foreach collection="records" item="record" separator=" OR ">
                    (1 = 1
                      <choose>
                        <when test="record.entityCode != null and record.entityDataId != null">
                          AND pt.entity_code = #{record.entityCode} AND pt.entity_data_id = #{record.entityDataId}
                        </when>
                        <otherwise><if test="record.processInstanceId == null">AND 1 = 0</if></otherwise>
                      </choose>
                      <if test="record.processInstanceId != null">AND pt.process_instance_id = #{record.processInstanceId}</if>
                    )
                  </foreach>
                )
              </when>
              <otherwise>AND 1 = 0</otherwise>
            </choose>
            ORDER BY pt.create_time DESC, pt.id DESC
            </script>
            """)
    List<com.workflow.process.task.infrastructure.persistence.record.ActionableTaskSummaryRow> selectActionableTaskSummaries(
            @Param("userId") String userId,
            @Param("records") List<com.workflow.contracts.process.port.ProcessTaskAccessPort.RecordCoordinates> records);

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param assignedOnly {@code assigned}仅，供本方法查询可执行任务ID行时使用
     * @return 流程任务集合，供调用方遍历或展示
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
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 查询后的可执行任务上下文结果，供调用方继续处理
     */
    default ProcessTask selectActionableTaskContext(String userId, String taskId, String entityCode, String entityDataId, String processInstanceId) {
        return selectActionableTaskContextRows(new OffsetPage<>(0, 1), userId, taskId, entityCode, entityDataId, processInstanceId).stream().findFirst().orElse(null);
    }

    /**
     * 与审批上下文使用完全相同的用户及记录范围，直接读取标量名称。
     * 列表展示不能依赖实体单任务摘要，也避免 pt.* 映射的空属性造成误导。
     */
    default String selectActionableTaskName(String userId, String taskId,
            String entityCode, String entityDataId, String processInstanceId) {
        return selectActionableTaskNameRows(new OffsetPage<>(0, 1),
                userId, taskId, entityCode, entityDataId, processInstanceId)
                .stream().findFirst().orElse(null);
    }

    @Select("<script>SELECT pt.node_name " + TODO_USER_SCOPE + """
            AND pt.task_id = #{taskId}
            AND pt.entity_code = #{entityCode}
            AND pt.entity_data_id = #{entityDataId}
            AND pt.process_instance_id = #{processInstanceId}
            </script>
            """)
    List<String> selectActionableTaskNameRows(
            @Param("page") IPage<String> page,
            @Param("userId") String userId,
            @Param("taskId") String taskId,
            @Param("entityCode") String entityCode,
            @Param("entityDataId") String entityDataId,
            @Param("processInstanceId") String processInstanceId);

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 流程任务集合，供调用方遍历或展示
     */
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
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 流程任务集合，供调用方遍历或展示
     */
    @Select("<script> SELECT * " + DONE_USER_SCOPE + " ORDER BY pt.end_time DESC </script>")
    List<ProcessTask> selectDoneByUser(@Param("userId") String userId);
    
    /**
     * 根据流程实例ID查询待办
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 流程任务集合，供调用方遍历或展示
     */
    default List<ProcessTask> selectByProcessInstance(String processInstanceId) {
        return selectList(Wrappers.<ProcessTask>lambdaQuery()
                .eq(ProcessTask::getProcessInstanceId, processInstanceId)
                .orderByAsc(ProcessTask::getCreateTime));
    }
    
    /**
     * 根据流程实例ID查询当前待办任务（status=0）
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 查询后的待办任务流程实例结果，供调用方继续处理
     */
    default ProcessTask selectTodoTaskByProcessInstance(String processInstanceId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTask>(1, 1, false), Wrappers.<ProcessTask>lambdaQuery()
                .eq(ProcessTask::getProcessInstanceId, processInstanceId)
                .eq(ProcessTask::getStatus, "todo")).stream().findFirst().orElse(null);
    }
    
    /**
     * 根据Flowable任务ID查询
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 查询后的任务ID结果，供调用方继续处理
     */
    default ProcessTask selectByTaskId(String taskId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTask>(1, 1, false), Wrappers.<ProcessTask>lambdaQuery()
                .eq(ProcessTask::getTaskId, taskId)).stream().findFirst().orElse(null);
    }

    /** 流程历史一次读取操作结果的最小投影，节点循环不再逐任务回查。 */
    @Select("SELECT task_id, action, action_label, comment FROM process_task WHERE process_instance_id = #{processInstanceId} AND deleted = 0 ORDER BY id")
    List<ProcessTask> selectProgressHistoryByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /**
     * 根据Flowable任务ID加锁查询待办（FOR UPDATE）。
     *
     * @param taskId Flowable任务ID
     * @return 待办记录，无则返回 null
     */
    // task_id 有唯一约束；保留删除过滤与行锁，不组合厂商分页子句。
    @Select("SELECT * FROM process_task WHERE task_id = #{taskId} AND deleted = 0 FOR UPDATE")
    ProcessTask selectByTaskIdForUpdate(@Param("taskId") String taskId);

    /**
     * 更新SLA摘要；后续读取或执行将使用更新后的状态。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param responseDueTime 响应{@code due}时间，后续用于判断有效期或展示该事件的发生时间
     * @param dueTime {@code due}时间，后续用于判断有效期或展示该事件的发生时间
     * @param slaStatus SLA状态标识，决定后续SLA摘要采用的处理分支
     * @return 更新后的SLA摘要结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task
            SET due_time = #{dueTime},
                response_due_time = #{responseDueTime},
                sla_status = #{slaStatus},
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param status 状态标识，决定后续完成任务采用的处理分支
     * @param action 动作标识，决定后续完成任务采用的处理分支
     * @param comment 注释，供本方法处理完成任务时使用
     * @param duration 时长，供本方法处理完成任务时使用
     * @return 处理后的完成任务结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task SET status = #{status}, action = #{action}, comment = #{comment}, end_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@currentNow(_databaseId)}, duration = #{duration}
             WHERE id = #{id}
            </script>
            """)
    int completeTask(@Param("id") Long id, @Param("status") String status, 
                     @Param("action") String action, @Param("comment") String comment,
                     @Param("duration") Long duration);
    
    /**
     * 统计当前用户待办数，授权条件与列表查询保持一致。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 符合条件的待办用户数量
     */
    @Select("SELECT COUNT(*) " + TODO_USER_SCOPE)
    Long countTodoByUser(@Param("userId") String userId);

    /**
     * 统计用户已办数
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 符合条件的{@code done}用户数量
     */
    @Select("<script> SELECT COUNT(*) " + DONE_USER_SCOPE + " </script>")
    Long countDoneByUser(@Param("userId") String userId);

    /** 一次查询返回同一数据快照中的数量和总时长；NULL 时长仍计入 COUNT(*)。 */
    @Select("<script> SELECT COUNT(*) AS task_count, COALESCE(SUM(pt.duration), 0) AS duration_total "
            + DONE_USER_SCOPE + " </script>")
    DoneTaskAggregate aggregateDoneByUser(@Param("userId") String userId);
}
