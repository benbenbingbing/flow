package com.workflow.observability.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.builder.annotation.ProviderContext;

/** 持久化异步队列聚合查询；两次查询绑定相同数据库时间，业务层原子发布指标。 */
@Mapper
public interface AsyncQueueMetricsMapper {
    /** 统计 Outbox 到期任务及最早等待时间；now 必须来自数据库 UTC 时钟。 */
    @SelectProvider(type = Sql.class, method = "snapshot")
    QueueSnapshot outbox(@Param("now") LocalDateTime now);

    /** 流程动作采用 RUNNING 状态，复用本轮 Outbox 查询时间以保证告警口径一致。 */
    @SelectProvider(type = Sql.class, method = "snapshot")
    QueueSnapshot flowAction(@Param("now") LocalDateTime now);

    /** 一次查询的完整聚合值；业务层成功取得两个快照后统一发布。 */
    record QueueSnapshot(long ready, long running, long dead, LocalDateTime oldestReadyAt) {}

    class Sql {
        /** 仅依据已声明的 Mapper 方法选择表名，禁止从请求传入标识符。空队列仍返回一行。 */
        public static String snapshot(ProviderContext context) {
            boolean outbox = "outbox".equals(context.getMapperMethod().getName());
            String table = outbox ? "workflow_outbox_event" : "process_action_execution";
            String running = outbox ? "PROCESSING" : "RUNNING";
            return "SELECT ready.item_count AS ready_count, running.item_count AS running_count,"
                + " dead.item_count AS dead_count, ready.oldest_ready_at"
                + " FROM (SELECT COUNT(*) AS item_count, MIN(create_time) AS oldest_ready_at FROM " + table
                + " WHERE status IN ('PENDING', 'FAILED') AND (next_retry_time IS NULL OR next_retry_time <= #{now})) ready"
                + " CROSS JOIN (SELECT COUNT(*) AS item_count FROM " + table + " WHERE status = '" + running + "') running"
                + " CROSS JOIN (SELECT COUNT(*) AS item_count FROM " + table + " WHERE status = 'DEAD') dead";
        }
    }
}
