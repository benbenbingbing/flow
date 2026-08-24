-- 实体结构发布操作：保存不可变 DDL 计划、风险评估、执行状态和漂移结果。
CREATE TABLE `entity_schema_operation` (
    `id` VARCHAR(64) NOT NULL COMMENT '操作ID',
    `entity_id` VARCHAR(64) NOT NULL COMMENT '实体ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `status` VARCHAR(32) NOT NULL COMMENT 'METADATA_SAVED/DDL_PENDING/DDL_RUNNING/SCHEMA_CONSISTENT/DDL_FAILED/TERMINATED',
    `plan_hash` CHAR(64) NOT NULL COMMENT '不可变DDL计划摘要',
    `idempotency_key` VARCHAR(160) NOT NULL COMMENT '幂等键',
    `plan_json` LONGTEXT NOT NULL COMMENT '不可变DDL计划JSON',
    `target_fingerprint` CHAR(64) NOT NULL COMMENT '目标结构指纹',
    `actual_fingerprint` CHAR(64) DEFAULT NULL COMMENT '实际结构指纹',
    `drift_json` LONGTEXT DEFAULT NULL COMMENT '结构漂移JSON',
    `unique_conflict_json` LONGTEXT DEFAULT NULL COMMENT '唯一性冲突扫描结果JSON',
    `risk_level` VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH',
    `risk_reason` VARCHAR(1000) DEFAULT NULL COMMENT '风险原因',
    `estimated_rows` BIGINT NOT NULL DEFAULT 0 COMMENT '预估数据行数',
    `lock_risk` VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '锁表风险',
    `release_window` VARCHAR(255) DEFAULT NULL COMMENT '建议发布窗口',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '执行次数',
    `error_message` TEXT DEFAULT NULL COMMENT '最近失败信息',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_schema_operation_plan` (`entity_id`, `plan_hash`),
    UNIQUE KEY `uk_entity_schema_operation_idempotency` (`idempotency_key`),
    KEY `idx_entity_schema_operation_latest` (`entity_id`, `create_time`),
    KEY `idx_entity_schema_operation_status` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体结构发布操作';

CREATE TABLE `entity_schema_operation_event` (
    `id` VARCHAR(64) NOT NULL COMMENT '事件ID',
    `operation_id` VARCHAR(64) NOT NULL COMMENT '操作ID',
    `from_status` VARCHAR(32) DEFAULT NULL COMMENT '原状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `message` VARCHAR(1000) DEFAULT NULL COMMENT '状态说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_entity_schema_operation_event` (`operation_id`, `create_time`),
    CONSTRAINT `fk_entity_schema_operation_event_operation`
        FOREIGN KEY (`operation_id`) REFERENCES `entity_schema_operation` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体结构操作事件';

-- 由数据库主键完成唯一值串行化，覆盖复杂字段和软删除场景，消除先查后写的竞态窗口。
CREATE TABLE `entity_unique_value` (
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `field_code` VARCHAR(100) NOT NULL COMMENT '字段编码',
    `value_hash` CHAR(64) NOT NULL COMMENT '规范化值摘要',
    `normalized_value` VARCHAR(1000) NOT NULL COMMENT '规范化值审计副本',
    `record_id` VARCHAR(64) NOT NULL COMMENT '占用该值的记录ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`entity_code`, `field_code`, `value_hash`),
    KEY `idx_entity_unique_value_record` (`entity_code`, `record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体字段唯一值预留';
