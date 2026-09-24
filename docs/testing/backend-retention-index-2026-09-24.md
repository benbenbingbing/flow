# 后端保留期清理索引验证

仅使用本任务独立临时 MySQL 8.0.27，禁用 TCP，仅经本地 Unix socket 访问；未读取业务数据，测试结束后正常关闭实例。项目生产基线为 MySQL 8.4，本记录不替代生产规模与该版本的压测。

两张表结构提取自不可变 V001，每表写入 100000 行合成数据。Outbox 包含 90000 行 PROCESSED，其中 54000 行早于截止日期，另有 10000 行 PENDING。排序值存在大量重复。前后均执行 `EXPLAIN ANALYZE`，加索引步骤执行实际 `V106__outbox_retention_index.sql`。

验证 SQL：

```sql
SELECT id FROM workflow_outbox_event
WHERE status = 'PROCESSED' AND processed_time < '2026-05-01'
ORDER BY processed_time, id LIMIT 1000;

SELECT id FROM system_operation_log
WHERE create_time < '2026-05-01'
ORDER BY create_time, id LIMIT 1000;
```

Outbox 原计划读取 90000 行并排序；新计划为覆盖索引范围扫描，读取 1000 行即达到本批上限。强制忽略新索引与默认计划返回的 1000 个 ID 无差异。这里只把耗时作为该合成数据的一次观察，不承诺线上提速倍数。

审计已有 `idx_system_operation_created(create_time)`。InnoDB 二级索引包含主键，实际计划已是按时间/主键顺序的覆盖索引扫描，无须新增重复索引。

原始执行计划：

```text
MySQL VERSION()
8.0.27

Synthetic rows per table: 100000

OUTBOX BEFORE
EXPLAIN
-> Limit: 1000 row(s)  (cost=7055.77 rows=1000) (actual time=194.000..194.063 rows=1000 loops=1)
    -> Sort: workflow_outbox_event.processed_time, workflow_outbox_event.id, limit input to 1000 row(s) per chunk  (cost=7055.77 rows=45429) (actual time=193.998..194.026 rows=1000 loops=1)
        -> Filter: (workflow_outbox_event.processed_time < TIMESTAMP'2026-05-01 00:00:00')  (actual time=0.149..133.809 rows=54000 loops=1)
            -> Index lookup on workflow_outbox_event using idx_workflow_outbox_ready (status='PROCESSED')  (actual time=0.148..130.628 rows=90000 loops=1)


OUTBOX AFTER
EXPLAIN
-> Limit: 1000 row(s)  (cost=27897.02 rows=1000) (actual time=0.015..0.339 rows=1000 loops=1)
    -> Filter: ((workflow_outbox_event.`status` = 'PROCESSED') and (workflow_outbox_event.processed_time < TIMESTAMP'2026-05-01 00:00:00'))  (cost=27897.02 rows=47542) (actual time=0.015..0.304 rows=1000 loops=1)
        -> Covering index range scan on workflow_outbox_event using idx_workflow_outbox_retention  (cost=27897.02 rows=47542) (actual time=0.013..0.194 rows=1000 loops=1)


AUDIT EXISTING INDEX
EXPLAIN
-> Limit: 1000 row(s)  (cost=11363.53 rows=1000) (actual time=0.013..0.202 rows=1000 loops=1)
    -> Filter: (system_operation_log.create_time < TIMESTAMP'2026-05-01 00:00:00')  (cost=11363.53 rows=36637) (actual time=0.012..0.168 rows=1000 loops=1)
        -> Covering index range scan on system_operation_log using idx_system_operation_created  (cost=11363.53 rows=36637) (actual time=0.012..0.129 rows=1000 loops=1)


OUTBOX RESULT STABILITY
mismatches
0

```
