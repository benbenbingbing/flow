-- 收口实体列表数据权限的自由表达式和自定义 SQL。
-- 历史危险规则保留但自动停用，管理员可在配置页中看到并迁移为结构化条件。

SET @has_created_at = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_permission'
    AND column_name = 'created_at'
);
SET @has_create_time = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_permission'
    AND column_name = 'create_time'
);
SET @rename_create_time_sql = IF(
  @has_created_at = 1 AND @has_create_time = 0,
  'ALTER TABLE entity_list_permission CHANGE COLUMN created_at create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
  'SELECT 1'
);
PREPARE rename_create_time_stmt FROM @rename_create_time_sql;
EXECUTE rename_create_time_stmt;
DEALLOCATE PREPARE rename_create_time_stmt;

SET @has_updated_at = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_permission'
    AND column_name = 'updated_at'
);
SET @has_update_time = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_permission'
    AND column_name = 'update_time'
);
SET @rename_update_time_sql = IF(
  @has_updated_at = 1 AND @has_update_time = 0,
  'ALTER TABLE entity_list_permission CHANGE COLUMN updated_at update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
  'SELECT 1'
);
PREPARE rename_update_time_stmt FROM @rename_update_time_sql;
EXECUTE rename_update_time_stmt;
DEALLOCATE PREPARE rename_update_time_stmt;

UPDATE entity_list_permission
SET enabled = 0,
    update_time = NOW()
WHERE deleted = 0
  AND enabled = 1
  AND (
    JSON_UNQUOTE(JSON_EXTRACT(filter_config, '$.type')) IN ('EXPRESSION', 'CUSTOM_SQL')
    OR JSON_SEARCH(match_config, 'one', 'EXPRESSION', NULL, '$.conditions[*].scopeType') IS NOT NULL
  );

UPDATE entity_list_permission
SET match_config = JSON_SET(
      COALESCE(match_config, JSON_OBJECT()),
      '$.version',
      1
    ),
    filter_config = JSON_SET(
      COALESCE(filter_config, JSON_OBJECT('type', 'PERSONAL')),
      '$.version',
      1
    ),
    update_time = NOW()
WHERE deleted = 0
  AND (
    JSON_EXTRACT(match_config, '$.version') IS NULL
    OR JSON_EXTRACT(filter_config, '$.version') IS NULL
  );

UPDATE entity_list_permission
SET filter_config = JSON_SET(
      filter_config,
      '$.fieldMapping.userField',
      'create_by'
    ),
    update_time = NOW()
WHERE deleted = 0
  AND JSON_UNQUOTE(
        JSON_EXTRACT(filter_config, '$.fieldMapping.userField')
      ) = 'created_by';

ALTER TABLE entity_list_permission
  ADD KEY idx_entity_permission_runtime (
    entity_code,
    enabled,
    list_config_id,
    priority
  );
