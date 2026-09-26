-- 保留已有四种失败策略；只有 CUSTOM 使用策略版本快照和额外重试次数语义。
ALTER TABLE process_action
    ADD COLUMN failure_strategy_code VARCHAR(64) NULL;
ALTER TABLE process_action ADD COLUMN failure_strategy_version VARCHAR(32) NULL;
ALTER TABLE process_action ADD COLUMN failure_strategy_config TEXT NULL;

-- 决策和人工处理轨迹沿用 execution_trace_json，与状态在同一次条件更新中持久化。
-- replay_of_id 关联原失败记录；handler_idempotency_key 保证重放对下游沿用原幂等键。
ALTER TABLE process_action_execution
    ADD COLUMN failure_strategy_snapshot MEDIUMTEXT NULL;
ALTER TABLE process_action_execution ADD COLUMN attempt_no INT NOT NULL DEFAULT 0;
ALTER TABLE process_action_execution ADD COLUMN attempt_lease_token BIGINT NULL;
ALTER TABLE process_action_execution ADD COLUMN termination_reason VARCHAR(64) NULL;
ALTER TABLE process_action_execution ADD COLUMN resolution_status VARCHAR(20) NULL;
ALTER TABLE process_action_execution ADD COLUMN replay_root_id VARCHAR(64) NULL;
ALTER TABLE process_action_execution ADD COLUMN replay_of_id VARCHAR(64) NULL;
ALTER TABLE process_action_execution ADD COLUMN handler_idempotency_key VARCHAR(128) NULL;
