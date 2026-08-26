-- 统一审计只复用既有 append-only system_operation_log，不复制业务载荷。
-- operation_id 与 trace_id 分离，来源指针仅保存稳定标识；来源详情仍由各模块鉴权读取。
ALTER TABLE `system_operation_log`
  ADD COLUMN `operation_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '业务操作ID；同一次跨模块操作共享' AFTER `event_id`,
  ADD COLUMN `parent_operation_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '父业务操作ID' AFTER `trace_id`,
  ADD COLUMN `source_system` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '权威来源系统/模块' AFTER `parent_operation_id`,
  ADD COLUMN `source_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '权威来源记录类型' AFTER `source_system`,
  ADD COLUMN `source_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '权威来源记录ID' AFTER `source_type`,
  ADD COLUMN `source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '权威来源事件ID' AFTER `source_id`,
  ADD KEY `idx_system_operation_operation` (`operation_id`, `create_time`),
  ADD KEY `idx_system_operation_source` (`source_type`, `source_id`, `create_time`);

-- 历史日志没有可靠的跨模块 operationId，使用自身 eventId 形成单事件操作，
-- 禁止根据相同 traceId 猜测合并，从而避免把同一请求中的独立业务动作误串联。
UPDATE `system_operation_log`
   SET `operation_id` = `event_id`
 WHERE `operation_id` IS NULL OR `operation_id` = '';

-- Expand 阶段必须兼容尚未升级的旧 Pod：旧代码省略 operation_id 时仍可写入。
-- 新应用强制写入 operationId；待所有实例升级并完成存量核验后，才能在独立
-- Contract 迁移中考虑 NOT NULL，不能在本迁移的回填后立刻收紧。
