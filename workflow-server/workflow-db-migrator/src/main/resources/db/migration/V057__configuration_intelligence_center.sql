-- P2 配置资产智能中心：蓝图复用、依赖影响图和质量快照。
CREATE TABLE config_blueprint (
    id VARCHAR(64) NOT NULL,
    blueprint_key VARCHAR(100) NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NULL,
    version INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    parameter_schema_json LONGTEXT NOT NULL,
    bundle_json LONGTEXT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    download_count BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_blueprint_version (blueprint_key, version),
    KEY idx_config_blueprint_catalog (category, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置蓝图版本';

CREATE TABLE config_asset_dependency (
    id VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    required_flag TINYINT NOT NULL DEFAULT 1,
    metadata_json LONGTEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_asset_dependency (
        source_type, source_id, target_type, target_id, relation_type
    ),
    KEY idx_config_dependency_source (source_type, source_id),
    KEY idx_config_dependency_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置资产依赖关系';

CREATE TABLE config_quality_snapshot (
    id VARCHAR(64) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    asset_id VARCHAR(64) NOT NULL,
    config_fingerprint VARCHAR(64) NOT NULL,
    score INT NOT NULL,
    grade VARCHAR(8) NOT NULL,
    blocker_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    findings_json LONGTEXT NOT NULL,
    generated_by VARCHAR(64) NULL,
    generated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_config_quality_asset (asset_type, asset_id, generated_at),
    KEY idx_config_quality_score (score, generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置质量评分快照';

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075700000000000001', '0', '配置资产智能', 'C', 'MagicStick', 87,
    '/system/config-intelligence', 'system/ConfigurationIntelligence',
    'config:intelligence:list', '0', '0', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/system/config-intelligence' AND deleted = 0
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075700000000000002', page.id, '管理配置蓝图', 'F', NULL, 1,
    NULL, NULL, 'config:intelligence:manage', '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT id FROM sys_menu WHERE path = '/system/config-intelligence' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm = 'config:intelligence:manage' AND deleted = 0);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075700000000000003', page.id, '分析配置资产', 'F', NULL, 2,
    NULL, NULL, 'config:intelligence:analyze', '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT id FROM sys_menu WHERE path = '/system/config-intelligence' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm = 'config:intelligence:analyze' AND deleted = 0);
