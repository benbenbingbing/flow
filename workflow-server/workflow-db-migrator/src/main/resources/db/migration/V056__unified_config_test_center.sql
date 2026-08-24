-- 统一配置测试中心：测试套件、用例、运行及可审计结果。
CREATE TABLE config_test_suite (
    id VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    release_gate TINYINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 1,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_config_test_suite_scope (scope_type, scope_id, enabled, deleted),
    KEY idx_config_test_suite_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一配置测试套件';

CREATE TABLE config_test_case (
    id VARCHAR(64) NOT NULL,
    suite_id VARCHAR(64) NOT NULL,
    case_key VARCHAR(100) NOT NULL,
    name VARCHAR(160) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NULL,
    scenario_type VARCHAR(64) NOT NULL,
    input_json LONGTEXT NULL,
    expected_json LONGTEXT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_test_case_key (suite_id, case_key),
    KEY idx_config_test_case_suite (suite_id, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一配置测试用例';

CREATE TABLE config_test_run (
    id VARCHAR(64) NOT NULL,
    suite_id VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    config_fingerprint VARCHAR(64) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    passed_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    blocker_count INT NOT NULL DEFAULT 0,
    operator_id VARCHAR(64) NULL,
    report_json LONGTEXT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_config_test_run_suite (suite_id, started_at),
    KEY idx_config_test_run_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一配置测试运行';

CREATE TABLE config_test_result (
    id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    case_name VARCHAR(160) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NULL,
    status VARCHAR(24) NOT NULL,
    severity VARCHAR(24) NOT NULL,
    result_code VARCHAR(100) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    evidence_json LONGTEXT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_config_test_result_run (run_id, status, severity),
    KEY idx_config_test_result_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一配置测试检查结果';

-- 页面及按钮权限。使用固定唯一 ID，并通过路径/权限码幂等保护重复初始化。
INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075600000000000001', '0', '配置测试中心', 'C', 'Checked', 86,
    '/system/config-test-center', 'system/ConfigTestCenter', 'config:test:list',
    '0', '0', '1', '0', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/system/config-test-center' AND deleted = 0
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075600000000000002', page.id, '管理测试套件', 'F', NULL, 1,
    NULL, NULL, 'config:test:manage', '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT id FROM sys_menu WHERE path = '/system/config-test-center' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm = 'config:test:manage' AND deleted = 0);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075600000000000003', page.id, '运行配置测试', 'F', NULL, 2,
    NULL, NULL, 'config:test:run', '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT id FROM sys_menu WHERE path = '/system/config-test-center' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm = 'config:test:run' AND deleted = 0);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, deleted, create_time, update_time
)
SELECT
    '2075600000000000004', page.id, '导出测试报告', 'F', NULL, 3,
    NULL, NULL, 'config:test:export', '0', '1', '1', '0', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT id FROM sys_menu WHERE path = '/system/config-test-center' AND deleted = 0 LIMIT 1) page
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm = 'config:test:export' AND deleted = 0);
