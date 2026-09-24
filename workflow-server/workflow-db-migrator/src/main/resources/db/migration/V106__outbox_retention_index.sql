-- 已完成事件按完成时间、主键分批清理。现有 ready 索引服务重试队列，
-- 无法同时满足 processed_time 范围和稳定排序；单独索引避免每批扫描、排序全部已完成事件。
CREATE INDEX idx_workflow_outbox_retention
    ON workflow_outbox_event (status, processed_time, id);
