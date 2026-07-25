-- 表单与列表兼容热修复发布、流程版本绑定、目标快照和发布审计。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v037$$
CREATE PROCEDURE workflow_add_column_if_missing_v037(
    IN p_table VARCHAR(128),
    IN p_column VARCHAR(128),
    IN p_definition VARCHAR(2000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @add_column_sql = CONCAT(
            'ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition
        );
        PREPARE add_column_stmt FROM @add_column_sql;
        EXECUTE add_column_stmt;
        DEALLOCATE PREPARE add_column_stmt;
    END IF;
END$$

DELIMITER ;

CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'release_mode',
  'varchar(20) NOT NULL DEFAULT ''STANDARD'' COMMENT ''STANDARD/HOTFIX'''
);
CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'base_release_id',
  'varchar(64) DEFAULT NULL COMMENT ''热修复基线发布ID'''
);
CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'risk_level',
  'varchar(20) NOT NULL DEFAULT ''SAFE'' COMMENT ''SAFE/REVIEW/BLOCKED'''
);
CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'rollout_scope',
  'varchar(30) DEFAULT NULL COMMENT ''ACTIVE_AND_FUTURE'''
);
CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'patch_document',
  'longtext DEFAULT NULL COMMENT ''稳定ID语义补丁JSON文档'''
);
CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'override_risk',
  'tinyint NOT NULL DEFAULT 0 COMMENT ''是否经授权覆盖REVIEW风险'''
);
CALL workflow_add_column_if_missing_v037(
  'ui_config_release',
  'override_reason',
  'varchar(1000) DEFAULT NULL COMMENT ''风险覆盖原因'''
);

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v037;

UPDATE ui_config_release
SET release_mode = 'STANDARD'
WHERE release_mode IS NULL OR release_mode = '';

UPDATE ui_config_release
SET risk_level = 'SAFE'
WHERE risk_level IS NULL OR risk_level = '';

CREATE TABLE IF NOT EXISTS process_ui_release_binding (
  id varchar(64) NOT NULL COMMENT '绑定记录ID',
  process_version_history_id varchar(64) NOT NULL COMMENT '流程发布历史ID',
  process_config_id varchar(64) NOT NULL COMMENT '流程配置ID',
  process_key varchar(100) NOT NULL COMMENT '流程标识',
  process_version int NOT NULL COMMENT '流程版本号',
  deployment_id varchar(100) DEFAULT NULL COMMENT 'Flowable部署ID',
  node_id varchar(100) NOT NULL COMMENT '流程节点ID',
  node_name varchar(200) DEFAULT NULL COMMENT '流程节点名称',
  config_type varchar(20) NOT NULL DEFAULT 'FORM' COMMENT 'FORM/LIST',
  config_id varchar(64) NOT NULL COMMENT '表单或列表配置ID',
  pinned_release_id varchar(64) NOT NULL COMMENT '流程发布时固定的UI发布ID',
  pinned_release_version int NOT NULL COMMENT '流程发布时固定的UI版本号',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_ui_release_binding (
    process_version_history_id,
    node_id,
    config_type,
    config_id
  ),
  KEY idx_process_ui_binding_config (
    config_type,
    config_id,
    process_version_history_id
  ),
  KEY idx_process_ui_binding_release (
    pinned_release_id,
    process_version_history_id
  ),
  KEY idx_process_ui_binding_deployment (deployment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程发布版本与UI发布快照绑定';

CREATE TABLE IF NOT EXISTS ui_config_hotfix_target (
  id varchar(64) NOT NULL COMMENT '热修复目标ID',
  hotfix_release_id varchar(64) NOT NULL COMMENT '热修复发布ID',
  config_type varchar(20) NOT NULL COMMENT 'FORM/LIST',
  config_id varchar(64) NOT NULL COMMENT '配置ID',
  process_version_history_id varchar(64) NOT NULL COMMENT '目标流程发布历史ID',
  pinned_release_id varchar(64) NOT NULL COMMENT '目标原始钉定发布ID',
  pinned_release_version int NOT NULL COMMENT '目标原始钉定版本号',
  previous_target_id varchar(64) DEFAULT NULL COMMENT '上一有效热修复目标ID',
  effective_snapshot_document longtext NOT NULL COMMENT '目标有效完整快照JSON文档',
  effective_content_hash varchar(64) NOT NULL COMMENT '目标有效快照SHA-256',
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUPERSEDED/ROLLED_BACK',
  active_slot tinyint GENERATED ALWAYS AS (
    CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
  ) STORED COMMENT '保证同一流程版本只有一个有效目标',
  activated_by varchar(64) DEFAULT NULL COMMENT '激活人',
  activated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '激活时间',
  rolled_back_by varchar(64) DEFAULT NULL COMMENT '撤回人',
  rolled_back_at datetime DEFAULT NULL COMMENT '撤回时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_hotfix_target_active (
    config_type,
    config_id,
    process_version_history_id,
    active_slot
  ),
  KEY idx_ui_hotfix_target_release (hotfix_release_id, status),
  KEY idx_ui_hotfix_target_pinned (pinned_release_id, status),
  KEY idx_ui_hotfix_target_process (process_version_history_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='UI热修复流程版本目标快照';

CREATE TABLE IF NOT EXISTS ui_config_release_audit (
  id varchar(64) NOT NULL COMMENT '审计ID',
  config_type varchar(20) NOT NULL COMMENT 'FORM/LIST',
  config_id varchar(64) NOT NULL COMMENT '配置ID',
  release_id varchar(64) DEFAULT NULL COMMENT '关联发布ID',
  operation varchar(40) NOT NULL COMMENT 'PREVIEW/PUBLISH_STANDARD/PUBLISH_HOTFIX/ROLLBACK_HOTFIX/OVERRIDE',
  risk_level varchar(20) DEFAULT NULL COMMENT 'SAFE/REVIEW/BLOCKED',
  actor_id varchar(64) DEFAULT NULL COMMENT '操作人ID',
  actor_name varchar(100) DEFAULT NULL COMMENT '操作人名称',
  reason varchar(1000) DEFAULT NULL COMMENT '发布或覆盖原因',
  trace_id varchar(100) DEFAULT NULL COMMENT '业务追踪ID',
  detail_document longtext DEFAULT NULL COMMENT '影响范围与差异JSON文档',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ui_release_audit_config (config_type, config_id, create_time),
  KEY idx_ui_release_audit_release (release_id, create_time),
  KEY idx_ui_release_audit_operation (operation, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='UI发布与热修复审计日志';

SET @ui_hotfix_permission_parent = COALESCE(
    (
        SELECT id
        FROM sys_menu
        WHERE path = '/entity'
          AND menu_type = 'M'
          AND deleted = 0
        LIMIT 1
    ),
    (
        SELECT id
        FROM sys_menu
        WHERE menu_name = '配置管理'
          AND menu_type = 'M'
          AND deleted = 0
        LIMIT 1
    ),
    '0'
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
  'entity_ui_hotfix_publish_permission',
  @ui_hotfix_permission_parent,
  'UI配置兼容热修复',
  'F',
  NULL,
  90,
  NULL,
  NULL,
  'entity:ui-config:hotfix',
  '0',
  '1',
  '0',
  '0',
  NOW(),
  NOW(),
  0
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_menu
  WHERE perm = 'entity:ui-config:hotfix'
    AND deleted = 0
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
  'entity_ui_hotfix_override_permission',
  @ui_hotfix_permission_parent,
  'UI配置热修复风险覆盖',
  'F',
  NULL,
  91,
  NULL,
  NULL,
  'entity:ui-config:hotfix:override',
  '0',
  '1',
  '0',
  '0',
  NOW(),
  NOW(),
  0
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_menu
  WHERE perm = 'entity:ui-config:hotfix:override'
    AND deleted = 0
);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.perm = 'entity:ui-config:hotfix'
 AND menu.deleted = 0
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.perm = 'entity:ui-config:hotfix:override'
 AND menu.deleted = 0
WHERE role.role_code = 'super_admin'
  AND role.deleted = 0;
