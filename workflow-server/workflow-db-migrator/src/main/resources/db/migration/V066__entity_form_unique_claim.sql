-- 表单发布规则作用域的唯一值占位。
-- constraint_key 包含精确有效快照身份（热修复使用 target ID），避免规则条件跨发布版变更后的旧占位造成假冲突。
-- value gate 则使用不含表单/发布ID/ruleId的稳定实体字段作用域，仅用于跨表单、跨发布版
-- 串行化“加锁后复查”。同一实体字段无论由哪个表单或规则写入都共用门闩，避免不同
-- 表单的并发事务各自查不到未提交记录而同时放行同值。
CREATE TABLE entity_form_unique_value_gate (
    scope_key VARCHAR(255) NOT NULL COMMENT '稳定值锁作用域，格式 ENTITY:{entityCode}:{fieldCode}',
    value_hash CHAR(64) NOT NULL COMMENT '规范化值的 SHA-256',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (scope_key, value_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='表单唯一值跨表单跨发布版事务串行门闩';

CREATE TABLE entity_form_unique_claim (
    constraint_key VARCHAR(255) NOT NULL COMMENT '唯一约束命名空间，格式 FORM:{formId}:{snapshotIdentity}:{ruleId}',
    value_hash CHAR(64) NOT NULL COMMENT '规范化值的 SHA-256',
    entity_code VARCHAR(100) NOT NULL COMMENT '实体编码',
    form_id VARCHAR(64) NOT NULL COMMENT '规则所属表单ID',
    rule_id VARCHAR(100) NOT NULL COMMENT '跨发布版本稳定的规则ID',
    field_code VARCHAR(100) NOT NULL COMMENT '规则字段编码',
    normalized_value VARCHAR(1000) NOT NULL COMMENT '截断后的规范化值，仅用于排障',
    record_id VARCHAR(64) NOT NULL COMMENT '占位所属业务记录ID',
    release_id VARCHAR(64) NULL COMMENT '最近一次维护占位的表单发布ID，仅用于审计',
    release_version INT NULL COMMENT '最近一次维护占位的表单发布版本，仅用于审计',
    effective_release_id VARCHAR(64) NOT NULL COMMENT '规则实际来源发布ID；热修复时为热修复发布ID',
    effective_content_hash CHAR(64) NULL COMMENT '规则实际有效快照哈希，仅用于审计和完整性追踪',
    hotfix_target_id VARCHAR(64) NULL COMMENT '热修复目标ID；非热修复为空',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (constraint_key, value_hash),
    UNIQUE KEY uk_form_unique_claim_record (constraint_key, record_id),
    KEY idx_form_unique_claim_record (entity_code, record_id),
    KEY idx_form_unique_claim_form_rule (form_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='表单作用域唯一值原子占位';
