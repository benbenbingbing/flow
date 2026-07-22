-- 统一实体列表运行时和数据范围配置。
-- 项目尚未上线，本迁移直接切换到新模型，不保留旧表运行时兼容。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_rename_column_if_needed$$
CREATE PROCEDURE workflow_rename_column_if_needed(
    IN p_table VARCHAR(128),
    IN p_source VARCHAR(128),
    IN p_target VARCHAR(128),
    IN p_definition VARCHAR(1000)
)
BEGIN
    DECLARE v_source_exists INT DEFAULT 0;
    DECLARE v_target_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_source_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table
      AND column_name = p_source;

    SELECT COUNT(*) INTO v_target_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table
      AND column_name = p_target;

    IF v_source_exists = 1 AND v_target_exists = 0 THEN
        SET @rename_column_sql = CONCAT(
            'ALTER TABLE `', p_table, '` CHANGE COLUMN `', p_source, '` `',
            p_target, '` ', p_definition
        );
        PREPARE rename_column_stmt FROM @rename_column_sql;
        EXECUTE rename_column_stmt;
        DEALLOCATE PREPARE rename_column_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing$$
CREATE PROCEDURE workflow_add_column_if_missing(
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

CALL workflow_rename_column_if_needed(
  'entity_list_config',
  'created_at',
  'create_time',
  'datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'''
);
CALL workflow_rename_column_if_needed(
  'entity_list_config',
  'updated_at',
  'update_time',
  'datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'''
);
CALL workflow_rename_column_if_needed(
  'entity_list_field',
  'created_at',
  'create_time',
  'datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'''
);
CALL workflow_rename_column_if_needed(
  'entity_list_field',
  'updated_at',
  'update_time',
  'datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'''
);

CALL workflow_add_column_if_missing(
  'entity_list_config',
  'data_scope_mode',
  'varchar(20) NOT NULL DEFAULT ''INHERIT'' COMMENT ''数据范围模式：INHERIT/NARROW/OVERRIDE'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'access_permission_code',
  'varchar(200) DEFAULT NULL COMMENT ''列表访问权限码，空时继承 entity:{code}:list'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'allowed_scenes',
  'json DEFAULT NULL COMMENT ''允许场景：MENU/PAGE/DIALOG/DRAWER/EMBEDDED/FORM_PICKER/SUB_TABLE'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'selection_config',
  'json DEFAULT NULL COMMENT ''单选、多选和返回映射配置'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'fixed_filter_config',
  'json DEFAULT NULL COMMENT ''服务端固定查询条件'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'context_binding_config',
  'json DEFAULT NULL COMMENT ''来源记录上下文绑定配置'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'query_provider_code',
  'varchar(100) DEFAULT NULL COMMENT ''自定义安全查询提供者编码'''
);
CALL workflow_add_column_if_missing(
  'entity_list_config',
  'published_version',
  'int NOT NULL DEFAULT 1 COMMENT ''列表发布版本'''
);

CALL workflow_add_column_if_missing(
  'sys_menu',
  'resource_type',
  'varchar(30) DEFAULT NULL COMMENT ''菜单资源类型，ENTITY_LIST 表示动态实体列表'''
);
CALL workflow_add_column_if_missing(
  'sys_menu',
  'list_key',
  'varchar(100) DEFAULT NULL COMMENT ''实体列表稳定编码'''
);

DROP PROCEDURE IF EXISTS workflow_rename_column_if_needed;
DROP PROCEDURE IF EXISTS workflow_add_column_if_missing;

CREATE TABLE IF NOT EXISTS entity_list_scope_policy (
  id varchar(64) NOT NULL COMMENT '主键ID',
  entity_code varchar(100) NOT NULL COMMENT '实体编码',
  policy_key varchar(100) NOT NULL COMMENT '方案稳定编码',
  policy_name varchar(200) NOT NULL COMMENT '方案名称',
  description varchar(500) DEFAULT NULL COMMENT '方案说明',
  preset_code varchar(50) DEFAULT NULL COMMENT '内置模板编码',
  filter_config json NOT NULL COMMENT '结构化数据条件',
  status varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  version int NOT NULL DEFAULT 1 COMMENT '配置版本',
  review_required tinyint NOT NULL DEFAULT 0 COMMENT '旧复杂规则是否需要人工确认',
  created_by varchar(64) DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_entity_list_scope_policy (entity_code, policy_key, deleted),
  KEY idx_entity_list_scope_policy_runtime (entity_code, status, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围方案';

CREATE TABLE IF NOT EXISTS entity_list_scope_binding (
  id varchar(64) NOT NULL COMMENT '主键ID',
  entity_code varchar(100) NOT NULL COMMENT '实体编码',
  policy_id varchar(64) NOT NULL COMMENT '数据范围方案ID',
  list_key varchar(100) DEFAULT NULL COMMENT '列表编码，空表示实体默认范围',
  match_config json NOT NULL COMMENT '适用用户结构化条件',
  rule_effect varchar(20) NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
  enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  effective_start_time datetime DEFAULT NULL COMMENT '生效时间',
  effective_end_time datetime DEFAULT NULL COMMENT '失效时间',
  created_by varchar(64) DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_entity_list_scope_binding_runtime (entity_code, list_key, enabled, deleted),
  KEY idx_entity_list_scope_binding_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围适用对象绑定';

CREATE TABLE IF NOT EXISTS entity_list_scope_release (
  id varchar(64) NOT NULL COMMENT '主键ID',
  entity_code varchar(100) NOT NULL COMMENT '实体编码',
  version int NOT NULL COMMENT '发布版本',
  snapshot_json longtext NOT NULL COMMENT '方案、绑定和列表模式完整快照',
  content_hash varchar(64) NOT NULL COMMENT 'SHA-256',
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  description varchar(500) DEFAULT NULL COMMENT '发布说明',
  published_by varchar(64) DEFAULT NULL COMMENT '发布人',
  published_at datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_entity_list_scope_release (entity_code, version),
  KEY idx_entity_list_scope_release_active (entity_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围发布快照';

CREATE TABLE IF NOT EXISTS entity_list_scope_audit_log (
  id varchar(64) NOT NULL COMMENT '主键ID',
  entity_code varchar(100) NOT NULL COMMENT '实体编码',
  list_key varchar(100) DEFAULT NULL COMMENT '列表编码',
  user_id varchar(64) DEFAULT NULL COMMENT '操作或被校验用户',
  operation varchar(50) NOT NULL COMMENT 'SAVE/PUBLISH/ROLLBACK/SIMULATE/BYPASS/DENY',
  result varchar(20) NOT NULL COMMENT 'SUCCESS/FAILED/DENIED',
  detail_json longtext COMMENT '结构化详情',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_entity_list_scope_audit (entity_code, list_key, create_time),
  KEY idx_entity_list_scope_audit_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围审计日志';

CREATE TABLE IF NOT EXISTS entity_list_scope_delegation (
  id varchar(64) NOT NULL COMMENT '主键ID',
  entity_code varchar(100) DEFAULT NULL COMMENT '实体编码，空表示全部实体',
  from_user_id varchar(64) NOT NULL COMMENT '委托方用户ID',
  to_user_id varchar(64) NOT NULL COMMENT '受托方用户ID',
  delegate_scope varchar(50) NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL/CREATED/SUBMITTED/CURRENT_TASK/POLICY/CONDITION',
  policy_id varchar(64) DEFAULT NULL COMMENT '指定方案ID',
  delegate_config json DEFAULT NULL COMMENT '附加结构化条件',
  start_time datetime DEFAULT NULL COMMENT '开始时间',
  end_time datetime DEFAULT NULL COMMENT '结束时间',
  enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_by varchar(64) DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_entity_list_scope_delegate_runtime (to_user_id, entity_code, enabled, deleted),
  KEY idx_entity_list_scope_delegate_from (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表数据范围委托';

INSERT IGNORE INTO entity_list_scope_policy (
  id, entity_code, policy_key, policy_name, preset_code, filter_config,
  status, enabled, version, review_required, created_by, create_time, update_time, deleted
)
SELECT
  id,
  entity_code,
  CONCAT('legacy_', LEFT(id, 40)),
  rule_name,
  JSON_UNQUOTE(JSON_EXTRACT(filter_config, '$.type')),
  COALESCE(filter_config, JSON_OBJECT('version', 1, 'type', 'PERSONAL')),
  'PUBLISHED',
  enabled,
  1,
  IF(UPPER(COALESCE(combine_mode, 'UNION')) = 'INTERSECT' OR COALESCE(stop_processing, 0) = 1, 1, 0),
  created_by,
  COALESCE(create_time, NOW()),
  COALESCE(update_time, NOW()),
  deleted
FROM entity_list_permission;

INSERT IGNORE INTO entity_list_scope_binding (
  id, entity_code, policy_id, list_key, match_config, rule_effect, enabled,
  created_by, create_time, update_time, deleted
)
SELECT
  SHA2(CONCAT('entity-list-scope-binding:', permission.id), 256),
  permission.entity_code,
  permission.id,
  list_config.list_key,
  COALESCE(permission.match_config, JSON_OBJECT(
    'version', 1,
    'logic', 'OR',
    'conditions', JSON_ARRAY(JSON_OBJECT('scopeType', 'ALL_USERS'))
  )),
  UPPER(COALESCE(permission.rule_effect, 'ALLOW')),
  permission.enabled,
  permission.created_by,
  COALESCE(permission.create_time, NOW()),
  COALESCE(permission.update_time, NOW()),
  permission.deleted
FROM entity_list_permission permission
LEFT JOIN entity_list_config list_config
  ON list_config.id COLLATE utf8mb4_unicode_ci =
     permission.list_config_id COLLATE utf8mb4_unicode_ci
 AND list_config.deleted = 0;

INSERT IGNORE INTO entity_list_scope_delegation (
  id, entity_code, from_user_id, to_user_id, delegate_scope,
  delegate_config, start_time, end_time, enabled, create_time, deleted
)
SELECT
  id, entity_code, from_user_id, to_user_id,
  CASE UPPER(COALESCE(delegate_scope, 'PERSONAL'))
    WHEN 'ALL' THEN 'PERSONAL'
    ELSE UPPER(COALESCE(delegate_scope, 'PERSONAL'))
  END,
  delegate_config, start_time, end_time, enabled,
  COALESCE(create_time, NOW()), 0
FROM entity_list_permission_delegate;

UPDATE entity_list_config
SET allowed_scenes = JSON_ARRAY(
      'MENU', 'PAGE', 'DIALOG', 'DRAWER', 'EMBEDDED', 'FORM_PICKER', 'SUB_TABLE'
    ),
    selection_config = JSON_OBJECT(
      'selectionMode', 'NONE',
      'valueField', 'id',
      'returnMappings', JSON_ARRAY()
    )
WHERE allowed_scenes IS NULL;

UPDATE sys_menu menu
SET menu.resource_type = 'ENTITY_LIST',
    menu.list_key = COALESCE(
      (
        SELECT config.list_key
        FROM entity_definition definition
        JOIN entity_list_config config
          ON config.entity_id = definition.id
         AND config.deleted = 0
        WHERE definition.entity_code = menu.entity_code
        ORDER BY config.is_default DESC, config.create_time ASC
        LIMIT 1
      ),
      'default'
    )
WHERE menu.menu_type = 'C'
  AND menu.entity_code IS NOT NULL
  AND menu.entity_code <> '';

UPDATE sys_menu
SET path = CONCAT('/entity-list/', entity_code, '/', list_key),
    component = 'entity/EntityListRuntime'
WHERE resource_type = 'ENTITY_LIST';

DROP TABLE IF EXISTS entity_list_permission_delegate;
DROP TABLE IF EXISTS entity_list_permission;
