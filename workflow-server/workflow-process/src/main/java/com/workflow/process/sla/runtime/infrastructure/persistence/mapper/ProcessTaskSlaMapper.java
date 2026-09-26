package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.workflow.core.database.mybatis.OffsetPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 定义流程任务SLA的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface ProcessTaskSlaMapper extends BaseMapper<ProcessTaskSla> {

    /**
     * 按任务ID查询流程任务SLA；结果供后续展示或处理。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的流程任务SLA结果，供调用方继续处理
     */
    default ProcessTaskSla findByTaskId(String taskId) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessTaskSla>(1, 1, false), Wrappers.<ProcessTaskSla>lambdaQuery()
                .eq(ProcessTaskSla::getTaskId, taskId)).stream().findFirst().orElse(null);
    }

    /**
     * task_id 有唯一约束，直接锁定唯一行，无需组合分页与 FOR UPDATE。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的流程任务SLA结果，供调用方继续处理
     */
    @Select("""
            SELECT * FROM process_task_sla
            WHERE task_id = #{taskId}
            FOR UPDATE
            """)
    ProcessTaskSla findByTaskIdForUpdate(@Param("taskId") String taskId);

    /**
     * 读取流程实例的任务 SLA 记录，按创建顺序返回。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 流程任务SLA集合，供调用方遍历或展示
     */
    default List<ProcessTaskSla> findByProcessInstanceId(String processInstanceId) {
        return selectList(Wrappers.<ProcessTaskSla>lambdaQuery()
                .eq(ProcessTaskSla::getProcessInstanceId, processInstanceId)
                .orderByAsc(ProcessTaskSla::getCreateTime));
    }

    /**
     * 分批读取已暂停的 SLA，由分页插件限制数量。
     *
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 流程任务SLA集合，供调用方遍历或展示
     */
    default List<ProcessTaskSla> findPaused(int limit) {
        return selectList(new OffsetPage<>(0, limit), Wrappers.<ProcessTaskSla>lambdaQuery()
                .eq(ProcessTaskSla::getOverallStatus, "PAUSED")
                .isNotNull(ProcessTaskSla::getPauseStartedAt)
                .orderByAsc(ProcessTaskSla::getPauseStartedAt));
    }

    /**
     * 更新办理人；后续读取或执行将使用更新后的状态。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param assignee 办理人，供本方法更新办理人时使用
     * @return 更新后的办理人结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla
            SET current_assignee_id = #{assignee},
                version = version + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE task_id = #{taskId}
              AND overall_status IN ('RUNNING', 'PAUSED')
            </script>
            """)
    int updateAssignee(
            @Param("taskId") String taskId,
            @Param("assignee") String assignee);

    /**
     * 按告警优先级读取 SLA 监控列表，CASE 为固定标准 SQL，条件与分页由框架处理。
     *
     * @param status 状态标识，决定后续{@code monitor}分页采用的处理分支
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param assignee 办理人，作为 {@code monitorFilter} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code monitorFilter} 的输入影响后续处理
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 流程任务SLA集合，供调用方遍历或展示
     */
    default List<ProcessTaskSla> findMonitorPage(
            String status, String processKey, String assignee, String keyword, long offset, int limit) {
        var wrapper = monitorFilter(status, processKey, assignee, keyword);
        wrapper.orderByAsc("CASE overall_status WHEN 'BREACHED' THEN 0 WHEN 'RUNNING' THEN 1 "
                        + "WHEN 'PAUSED' THEN 2 ELSE 3 END")
                .orderByAsc("completion_due_at")
                .orderByDesc("create_time");
        return selectList(new OffsetPage<>(offset, limit), wrapper);
    }

    /**
     * 监控计数复用列表筛选，不重复维护动态 SQL。
     *
     * @param status 状态标识，决定后续{@code monitor}采用的处理分支
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param assignee 办理人，作为 {@code selectCount} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code selectCount} 的输入影响后续处理
     * @return 符合条件的{@code monitor}数量
     */
    default long countMonitor(String status, String processKey, String assignee, String keyword) {
        return selectCount(monitorFilter(status, processKey, assignee, keyword));
    }

    /**
     * 固定列名来自 Mapper；请求仅作为绑定值，保留原有空串和 LIKE 通配符语义。
     *
     * @param status 状态标识，决定后续{@code monitor}过滤采用的处理分支
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param assignee 办理人，作为 {@code eq} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code like} 的输入影响后续处理
     * @return 处理后的{@code monitor}过滤结果，供调用方继续处理
     */
    private static QueryWrapper<ProcessTaskSla> monitorFilter(
            String status, String processKey, String assignee, String keyword) {
        return Wrappers.<ProcessTaskSla>query()
                .eq(status != null && !status.isEmpty(), "overall_status", status)
                .eq(processKey != null && !processKey.isEmpty(), "process_key", processKey)
                .eq(assignee != null && !assignee.isEmpty(), "current_assignee_id", assignee)
                .and(keyword != null && !keyword.isEmpty(), text -> text
                        .like("node_name", keyword)
                        .or().like("business_key", keyword)
                        .or().like("policy_code", keyword));
    }

    /**
     * 统计各 SLA 状态数量，固定聚合表达式交给通用 selectMaps。
     *
     * @return 流程任务SLA集合，供调用方遍历或展示
     */
    default List<Map<String, Object>> statusStatistics() {
        return selectMaps(Wrappers.<ProcessTaskSla>query()
                .select("overall_status AS status", "COUNT(*) AS total")
                .groupBy("overall_status"));
    }
}
