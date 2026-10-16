-- 流程公共命令与引擎变更使用同一事务保存回执，跨进程重试复用首次结果。
-- 不复用流程动作执行表：动作重试与一次用户/业务命令不是同一种执行身份。
CREATE TABLE process_command_receipt (
    id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    request_id VARCHAR(200) COLLATE utf8mb4_bin NOT NULL,
    invocation_scope VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    command_type VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    command_hash VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    process_instance_id VARCHAR(128) COLLATE utf8mb4_bin NULL,
    task_id VARCHAR(128) COLLATE utf8mb4_bin NULL,
    changed TINYINT(1) NOT NULL DEFAULT 0,
    result_document LONGTEXT NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_process_command_instance (process_instance_id, create_time),
    KEY idx_process_command_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程公共命令事务内幂等回执';
