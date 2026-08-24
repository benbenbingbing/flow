-- P2 M4：个人/共享视图、聚合多视图、索引向导和配置使用关系查询。
CREATE TABLE entity_list_saved_view (
    id VARCHAR(64) NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    list_key VARCHAR(100) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    view_name VARCHAR(160) NOT NULL,
    scope_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL',
    view_type VARCHAR(20) NOT NULL DEFAULT 'TABLE',
    config_json LONGTEXT NOT NULL,
    list_release_version INT NULL,
    revision INT NOT NULL DEFAULT 1,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_list_saved_view_owner (owner_user_id, entity_code, list_key, deleted),
    KEY idx_list_saved_view_shared (scope_type, entity_code, list_key, deleted),
    KEY idx_list_saved_view_default (owner_user_id, entity_code, list_key, is_default, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体列表个人和团队共享视图';

CREATE TABLE entity_list_saved_view_audit (
    id VARCHAR(64) NOT NULL,
    view_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    detail_json LONGTEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_list_saved_view_audit (view_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='列表视图共享和变更审计';

CREATE TABLE entity_index_advice (
    id VARCHAR(64) NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    list_key VARCHAR(100) NOT NULL,
    advice_key VARCHAR(160) NOT NULL,
    index_name VARCHAR(64) NOT NULL,
    columns_json LONGTEXT NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SUGGESTED',
    revision INT NOT NULL DEFAULT 1,
    selectivity_estimate DECIMAL(8,4) NULL,
    estimated_rows BIGINT NOT NULL DEFAULT 0,
    write_cost_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    recommendation VARCHAR(1000) NULL,
    schema_operation_id VARCHAR(64) NULL,
    error_message TEXT NULL,
    created_by VARCHAR(64) NULL,
    applied_by VARCHAR(64) NULL,
    rejected_by VARCHAR(64) NULL,
    rejected_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_index_advice_key (entity_code, list_key, advice_key),
    KEY idx_entity_index_advice_status (status, update_time),
    KEY idx_entity_index_advice_entity (entity_code, list_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控字段索引建议';

ALTER TABLE config_migration_asset_dependency
    ADD COLUMN source_asset_type VARCHAR(20) NULL COMMENT '来源资产类型冗余',
    ADD COLUMN source_business_key VARCHAR(100) NULL COMMENT '来源资产稳定标识冗余',
    ADD COLUMN source_version INT NULL COMMENT '来源发布版本',
    ADD COLUMN reference_location VARCHAR(500) NULL COMMENT '引用在配置中的稳定位置',
    ADD COLUMN dependency_strength VARCHAR(20) NOT NULL DEFAULT 'HARD' COMMENT 'HARD/SOFT/UNKNOWN',
    ADD COLUMN parse_status VARCHAR(20) NOT NULL DEFAULT 'RESOLVED' COMMENT 'RESOLVED/UNKNOWN/INVALID',
    ADD COLUMN extracted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '引用抽取时间',
    ADD KEY idx_config_reference_source (source_asset_type, source_business_key, source_version),
    ADD KEY idx_config_reference_parse (parse_status, extracted_at);

ALTER TABLE entity_schema_operation
    ADD COLUMN operation_source VARCHAR(32) NOT NULL DEFAULT 'ENTITY_PUBLISH' COMMENT 'ENTITY_PUBLISH/INDEX_ADVISOR',
    ADD COLUMN source_reference_id VARCHAR(64) NULL COMMENT '索引建议等来源记录ID',
    ADD KEY idx_schema_operation_source (operation_source, source_reference_id);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075800000000000001', '0', '平台增强中心', 'C', 'DataAnalysis', 88,
    '/system/platform-capabilities', 'system/PlatformCapabilityCenter',
    'platform:capability:list', '0', '0', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/system/platform-capabilities' AND deleted = 0
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    permission_id, page.id, permission_name, 'F', NULL, permission_sort,
    NULL, NULL, permission_code, '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT '2075800000000000002' permission_id, '保存个人视图' permission_name,
           1 permission_sort, 'list-view:manage' permission_code
    UNION ALL SELECT '2075800000000000003', '共享团队视图', 2, 'list-view:share'
    UNION ALL SELECT '2075800000000000004', '执行列表聚合', 3, 'list-view:aggregate'
    UNION ALL SELECT '2075800000000000005', '分析字段索引', 4, 'index-advisor:analyze'
    UNION ALL SELECT '2075800000000000006', '执行索引计划', 5, 'index-advisor:execute'
    UNION ALL SELECT '2075800000000000007', '查询配置引用', 6, 'config-reference:list'
) permissions
JOIN (SELECT id FROM sys_menu WHERE path = '/system/platform-capabilities' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.perm = permissions.permission_code AND existing.deleted = 0
);
