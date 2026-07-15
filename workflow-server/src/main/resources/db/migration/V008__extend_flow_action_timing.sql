ALTER TABLE flow_action
    ADD COLUMN scope_type varchar(20) DEFAULT 'SEQUENCE_FLOW' COMMENT '作用域：PROCESS/NODE/SEQUENCE_FLOW' AFTER sequence_flow_id,
    ADD COLUMN element_id varchar(100) DEFAULT NULL COMMENT 'BPMN元素ID，流程级为空' AFTER scope_type,
    ADD COLUMN trigger_timing varchar(50) DEFAULT 'TRANSITION_TAKEN' COMMENT '业务触发时机' AFTER element_id,
    ADD COLUMN execution_mode varchar(30) DEFAULT 'IN_TRANSACTION' COMMENT '执行方式：IN_TRANSACTION/AFTER_COMMIT' AFTER trigger_timing,
    ADD COLUMN failure_policy varchar(20) DEFAULT 'ROLLBACK' COMMENT '失败策略：ROLLBACK/CONTINUE/RETRY/IGNORE' AFTER execution_mode,
    ADD COLUMN retry_config text COMMENT '重试配置JSON' AFTER failure_policy;

UPDATE flow_action
SET scope_type = 'SEQUENCE_FLOW',
    element_id = sequence_flow_id,
    trigger_timing = 'TRANSITION_TAKEN',
    execution_mode = 'IN_TRANSACTION',
    failure_policy = 'ROLLBACK'
WHERE scope_type IS NULL
   OR element_id IS NULL
   OR trigger_timing IS NULL
   OR execution_mode IS NULL
   OR failure_policy IS NULL;

CREATE INDEX idx_flow_action_binding
    ON flow_action(process_config_id, scope_type, element_id, trigger_timing, status, deleted);

CREATE INDEX idx_flow_action_version_binding
    ON flow_action(version_id, scope_type, element_id, trigger_timing, status, deleted);

CREATE TABLE flow_action_execution (
    id varchar(64) NOT NULL,
    action_id varchar(64) NOT NULL,
    version_id varchar(64) DEFAULT NULL,
    process_instance_id varchar(64) NOT NULL,
    process_definition_id varchar(128) DEFAULT NULL,
    execution_id varchar(64) DEFAULT NULL,
    task_id varchar(64) DEFAULT NULL,
    scope_type varchar(20) NOT NULL,
    element_id varchar(100) DEFAULT NULL,
    trigger_timing varchar(50) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    payload_json text,
    status varchar(20) NOT NULL,
    retry_count int DEFAULT 0,
    max_retries int DEFAULT 5,
    next_retry_time datetime DEFAULT NULL,
    error_message text,
    started_at datetime DEFAULT NULL,
    finished_at datetime DEFAULT NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_action_execution_idempotency (idempotency_key),
    KEY idx_flow_action_execution_ready (status, next_retry_time, created_at),
    KEY idx_flow_action_execution_process (process_instance_id, created_at),
    KEY idx_flow_action_execution_action (action_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作执行记录与Outbox';
