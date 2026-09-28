package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.process.task.api.response.TaskHandoverTask;
import java.util.List;
import org.apache.ibatis.annotations.*;

/**
 * 管理交接按引擎实际身份查询，刻意不使用普通待办的来源用户启用条件。
 * 候选按用户/有效组/有效角色解析；已认领任务只属于当前办理人。
 */
@Mapper
public interface TaskHandoverMapper {
    String COLUMNS = """
            pt.task_id, pt.node_name AS task_name, pt.process_instance_id, pt.process_name,
            pt.entity_code, pt.entity_data_id, pt.business_name, pt.business_code,
            pt.create_time, pt.assignee_name, pt.node_type, pt.status,
            CASE WHEN pt.node_type = 'ADD_SIGN' THEN 'ADD_SIGN'
                 WHEN NULLIF(ft.ASSIGNEE_, '') IS NULL THEN 'CANDIDATE'
                 ELSE 'ASSIGNED' END AS assignment_type
            """;

    // 与普通待办不同，保留离职人员遗留任务，以及加签暂缓期间尚需交接的责任。
    // 不能去掉引擎/加签存活性校验，否则已完成或失效的镜像也会被“恢复”为待办。
    String SCOPE = """
            FROM process_task pt
            LEFT JOIN ACT_RU_TASK ft ON ft.ID_ = pt.task_id
            WHERE pt.deleted = 0 AND pt.status IN ('todo', 'waiting', 'hold')
              AND EXISTS (
                SELECT 1 FROM sys_user u WHERE u.id = #{sourceUserId}
                  AND (
                    ((pt.node_type IS NULL OR pt.node_type != 'ADD_SIGN')
                     AND ft.ID_ IS NOT NULL AND ft.PROC_INST_ID_ = pt.process_instance_id
                     AND (ft.ASSIGNEE_ IN (u.id, u.username)
                       OR (NULLIF(ft.ASSIGNEE_, '') IS NULL AND EXISTS (
                         SELECT 1 FROM ACT_RU_IDENTITYLINK candidate
                         WHERE candidate.TASK_ID_ = ft.ID_ AND candidate.TYPE_ = 'candidate'
                           AND (candidate.USER_ID_ IN (u.id, u.username)
                             OR EXISTS (
                               SELECT 1 FROM sys_user_group ug JOIN sys_group g ON g.id = ug.group_id
                               WHERE ug.user_id = u.id AND g.deleted = 0 AND g.status = '0'
                                 AND SUBSTR(candidate.GROUP_ID_, 1, 5) != 'ROLE_'
                                 AND candidate.GROUP_ID_ IN (g.id, g.group_code))
                             OR EXISTS (
                               SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                               WHERE ur.user_id = u.id AND r.deleted = 0 AND r.status = '0'
                                 AND candidate.GROUP_ID_ IN (CONCAT('ROLE_', r.id), CONCAT('ROLE_', r.role_code)))
                       )))))
                    OR (pt.node_type = 'ADD_SIGN' AND ft.ID_ IS NULL
                      AND pt.assignee_type = 'user' AND pt.assignee_id IN (u.id, u.username)
                      AND EXISTS (
                        SELECT 1 FROM process_task_add_sign_user child
                        JOIN process_task_add_sign a ON a.id = child.add_sign_id
                        JOIN ACT_RU_TASK source_task ON source_task.ID_ = a.source_task_id
                        JOIN process_task source_mirror ON source_mirror.task_id = a.source_task_id
                        WHERE child.generated_task_id = pt.task_id
                          AND child.status IN ('TODO', 'HOLD') AND child.user_id IN (u.id, u.username)
                          AND a.status = 'ACTIVE' AND a.process_instance_id = pt.process_instance_id
                          AND source_task.PROC_INST_ID_ = pt.process_instance_id
                          AND source_mirror.process_instance_id = pt.process_instance_id
                          AND source_mirror.deleted = 0 AND source_mirror.status IN ('todo', 'waiting')
                          AND (source_mirror.entity_code = pt.entity_code
                            OR source_mirror.entity_code IS NULL AND pt.entity_code IS NULL)
                          AND (source_mirror.entity_data_id = pt.entity_data_id
                            OR source_mirror.entity_data_id IS NULL AND pt.entity_data_id IS NULL)))
                  )
              )
            """;

    /** 分页由数据库插件执行，总数与记录使用同一身份范围。 */
    @Select("SELECT " + COLUMNS + SCOPE + " ORDER BY pt.create_time DESC, pt.id DESC")
    List<TaskHandoverTask> selectPage(IPage<TaskHandoverTask> page, @Param("sourceUserId") String sourceUserId);

    @Select("SELECT COUNT(*) " + SCOPE)
    long count(@Param("sourceUserId") String sourceUserId);

    /** 全部交接读取任务 ID，不能把当前页记录当成全部；排序稳定以降低并发锁冲突。 */
    @Select("SELECT pt.task_id " + SCOPE + " ORDER BY pt.task_id")
    List<String> selectAllTaskIds(@Param("sourceUserId") String sourceUserId);

    /** 持锁后重验归属；禁止 MyBatis 会话缓存复用交接前的查询结果。 */
    @Select("SELECT " + COLUMNS + SCOPE + " AND pt.task_id = #{taskId}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    TaskHandoverTask findEligible(@Param("sourceUserId") String sourceUserId, @Param("taskId") String taskId);
}
