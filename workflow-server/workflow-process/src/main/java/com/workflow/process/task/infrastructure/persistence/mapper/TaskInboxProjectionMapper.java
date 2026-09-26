package com.workflow.process.task.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.contracts.entity.model.EntityTaskSummary;
import com.workflow.core.database.mybatis.OffsetPage;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** 摘要和身份独立更新，不能用整行 updateById 覆盖并发业务结果或写不进去的 NULL。 */
@Mapper
public interface TaskInboxProjectionMapper extends BaseMapper<ProcessTask> {
    /** 回填与引擎变更先竞争同一任务行，再刷新平台投影；运行任务消失时继续修复历史。 */
    @Select("SELECT ID_ FROM ACT_RU_TASK WHERE ID_=#{taskId} FOR UPDATE")
    String lockEngineTask(@Param("taskId") String taskId);

    /** 撤回可能先逻辑删除平台任务，再删除引擎任务；仍需找到主键清理候选，不能重新创建。 */
    @Select("SELECT id FROM process_task WHERE task_id=#{taskId} AND deleted=1 FOR UPDATE")
    Long findDeletedTaskId(@Param("taskId") String taskId);

    /** 仅刷新活动任务的办理人投影；允许 NULL 清空旧身份，返回 0 表示任务不存在或已删除。 */
    default int updateIdentity(Long id, String assignee, String name, String type) {
        return update(null, Wrappers.<ProcessTask>lambdaUpdate()
                .set(ProcessTask::getAssigneeId, assignee, "jdbcType=VARCHAR")
                .set(ProcessTask::getAssigneeName, name, "jdbcType=VARCHAR")
                .set(ProcessTask::getAssigneeType, type, "jdbcType=VARCHAR")
                .set(ProcessTask::getInboxIdentityReady, 1)
                .eq(ProcessTask::getId, id));
    }

    /** 覆盖指定活动任务的五个业务摘要字段，空摘要清除旧值且不改动身份和业务状态。 */
    default int updateBusinessSummary(Long id, EntityTaskSummary summary) {
        return update(null, summaryUpdate(summary).eq(ProcessTask::getId, id));
    }

    /** 按业务索引一次更新未删除的历史/活跃任务，避免长流程每次编辑发出数千条 UPDATE。 */
    default int updateBusinessSummaries(String entityCode, String recordId, EntityTaskSummary summary) {
        return update(null, summaryUpdate(summary)
                .eq(ProcessTask::getEntityCode, entityCode)
                .eq(ProcessTask::getEntityDataId, recordId));
    }

    /** 摘要刷新完成后标记可用于列表分页；发起人可为空，不应保留先前的身份。 */
    default int markSummaryReady(Long id, String starter) {
        return update(null, Wrappers.<ProcessTask>lambdaUpdate()
                .set(ProcessTask::getStartUserId, starter, "jdbcType=VARCHAR")
                .set(ProcessTask::getInboxSummaryReady, 1)
                .eq(ProcessTask::getId, id));
    }

    /** 按业务坐标读取最早的活动任务 ID；分页插件限制一行，不存在时返回 null。 */
    default Long findFirstBusinessTask(String entityCode, String recordId) {
        return selectList(new OffsetPage<ProcessTask>(0, 1), Wrappers.<ProcessTask>lambdaQuery()
                .select(ProcessTask::getId)
                .eq(ProcessTask::getEntityCode, entityCode)
                .eq(ProcessTask::getEntityDataId, recordId)
                .orderByAsc(ProcessTask::getId))
                .stream().map(ProcessTask::getId).findFirst().orElse(null);
    }

    /** 按主键游标分批读取待回填任务；limit 为 0 时返回空集合，为负数时拒绝无界扫描。 */
    default List<Long> findUnready(long afterId, int limit) {
        return selectList(new OffsetPage<ProcessTask>(0, limit), unreadyTasks()
                .select(ProcessTask::getId)
                .gt(ProcessTask::getId, afterId)
                .orderByAsc(ProcessTask::getId))
                .stream().map(ProcessTask::getId).toList();
    }

    @Select("SELECT id FROM process_task WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Long lockTask(@Param("id") Long id);

    /** 统计未就绪的活动任务，条件与回填分页共用，防止监控数量与实际回填范围不一致。 */
    default long countUnready() {
        return selectCount(unreadyTasks());
    }

    /** 摘要字段禁止普通实体更新；Wrapper 显式赋值并保留大文本及 NULL 的 JDBC 绑定。 */
    private static LambdaUpdateWrapper<ProcessTask> summaryUpdate(EntityTaskSummary summary) {
        EntityTaskSummary values = summary == null ? EntityTaskSummary.empty() : summary;
        return Wrappers.<ProcessTask>lambdaUpdate()
                .set(ProcessTask::getBusinessName, values.name(), "jdbcType=LONGVARCHAR")
                .set(ProcessTask::getBusinessCode, values.code(), "jdbcType=LONGVARCHAR")
                .set(ProcessTask::getBusinessDataName, values.dataName(), "jdbcType=LONGVARCHAR")
                .set(ProcessTask::getBusinessCurrentTaskName, values.currentTaskName(), "jdbcType=LONGVARCHAR")
                .set(ProcessTask::getBusinessStatus, values.status(), "jdbcType=LONGVARCHAR");
    }

    /** 历史 NULL 与数值 0 都代表未就绪；OR 必须整体分组，不能绕过主键游标或逻辑删除条件。 */
    private static LambdaQueryWrapper<ProcessTask> unreadyTasks() {
        return Wrappers.<ProcessTask>lambdaQuery()
                .and(ready -> ready.isNull(ProcessTask::getInboxSummaryReady)
                        .or().eq(ProcessTask::getInboxSummaryReady, 0)
                        .or().isNull(ProcessTask::getInboxIdentityReady)
                        .or().eq(ProcessTask::getInboxIdentityReady, 0));
    }
}
