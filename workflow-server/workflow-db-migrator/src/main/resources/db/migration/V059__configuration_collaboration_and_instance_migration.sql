-- P2 M5：配置协作、评审定时发布和流程实例版本迁移。
CREATE TABLE config_collaboration_workspace (
    id VARCHAR(64) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    asset_id VARCHAR(100) NOT NULL,
    asset_name VARCHAR(200) NULL,
    content_json LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    owner_user_id VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_workspace_asset (asset_type, asset_id),
    KEY idx_config_workspace_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置协作工作区';

CREATE TABLE config_collaboration_branch (
    id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    branch_key VARCHAR(100) NOT NULL,
    branch_name VARCHAR(160) NOT NULL,
    base_hash CHAR(64) NOT NULL,
    content_json LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    owner_user_id VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_branch_key (workspace_id, branch_key),
    KEY idx_config_branch_status (workspace_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置协作分支';

CREATE TABLE config_collaboration_comment (
    id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NULL,
    target_key VARCHAR(300) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    author_user_id VARCHAR(64) NOT NULL,
    resolved_by VARCHAR(64) NULL,
    resolved_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_config_comment_target (workspace_id, target_key, status),
    KEY idx_config_comment_branch (branch_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='稳定配置节点评论';

CREATE TABLE config_collaboration_review (
    id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NULL,
    requested_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    requester_user_id VARCHAR(64) NOT NULL,
    reviewer_user_id VARCHAR(64) NULL,
    review_note VARCHAR(1000) NULL,
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_config_review_workspace (workspace_id, status, requested_at),
    KEY idx_config_review_reviewer (reviewer_user_id, status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置评审与内容哈希绑定';

CREATE TABLE config_scheduled_release (
    id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    review_id VARCHAR(64) NOT NULL,
    release_candidate_id VARCHAR(64) NOT NULL,
    approved_hash CHAR(64) NOT NULL,
    candidate_revision INT NOT NULL,
    candidate_hash CHAR(64) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
    idempotency_key VARCHAR(128) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    executed_by VARCHAR(64) NULL,
    executed_at DATETIME(3) NULL,
    failure_message TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_scheduled_release_idempotency (idempotency_key),
    KEY idx_config_scheduled_release_due (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绑定审批哈希的定时发布';

CREATE TABLE process_instance_migration_batch (
    id VARCHAR(64) NOT NULL,
    batch_name VARCHAR(200) NOT NULL,
    source_process_definition_id VARCHAR(128) NOT NULL,
    target_process_definition_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    revision INT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128) NOT NULL,
    activity_mapping_json LONGTEXT NOT NULL,
    variable_mapping_json LONGTEXT NOT NULL,
    form_release_mapping_json LONGTEXT NOT NULL,
    dry_run_report_json LONGTEXT NULL,
    total_count INT NOT NULL DEFAULT 0,
    ready_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    blocked_count INT NOT NULL DEFAULT 0,
    requested_by VARCHAR(64) NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_migration_idempotency (idempotency_key),
    KEY idx_process_migration_status (status, update_time),
    KEY idx_process_migration_definitions (source_process_definition_id, target_process_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例版本迁移批次';

CREATE TABLE process_instance_migration_item (
    id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(128) NOT NULL,
    source_process_definition_id VARCHAR(128) NOT NULL,
    target_process_definition_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    validation_json LONGTEXT NULL,
    active_activities_json LONGTEXT NULL,
    variables_snapshot_json LONGTEXT NULL,
    post_validation_json LONGTEXT NULL,
    reversible TINYINT NOT NULL DEFAULT 0,
    form_mapping_applied TINYINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    lock_token VARCHAR(64) NULL,
    error_message TEXT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_migration_item (batch_id, process_instance_id),
    KEY idx_process_migration_item_status (batch_id, status, update_time),
    KEY idx_process_migration_instance (process_instance_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例迁移条目';

CREATE TABLE process_instance_migration_lock (
    process_instance_id VARCHAR(128) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    lock_token VARCHAR(64) NOT NULL,
    acquired_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (process_instance_id),
    UNIQUE KEY uk_process_migration_lock_token (lock_token),
    KEY idx_process_migration_lock_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例迁移跨批次互斥锁';

CREATE TABLE process_instance_migration_audit (
    id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64) NULL,
    action_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    detail_json LONGTEXT NULL,
    actor_id VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_process_migration_audit (batch_id, item_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例迁移审计';

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    permission_id, page.id, permission_name, 'F', NULL, permission_sort,
    NULL, NULL, permission_code, '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT '2075900000000000001' permission_id, '管理配置协作' permission_name,
           7 permission_sort, 'config-collaboration:manage' permission_code
    UNION ALL SELECT '2075900000000000002', '审批配置变更', 8, 'config-collaboration:review'
    UNION ALL SELECT '2075900000000000003', '安排配置发布', 9, 'config-collaboration:schedule'
    UNION ALL SELECT '2075900000000000004', '预检实例迁移', 10, 'process-instance-migration:preview'
    UNION ALL SELECT '2075900000000000005', '执行实例迁移', 11, 'process-instance-migration:execute'
) permissions
JOIN (SELECT id FROM sys_menu WHERE path = '/system/platform-capabilities' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.perm = permissions.permission_code AND existing.deleted = 0
);
