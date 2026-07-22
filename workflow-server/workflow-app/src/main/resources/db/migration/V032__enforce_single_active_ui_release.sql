UPDATE ui_config_release release_row
JOIN (
  SELECT config_type, config_id, MAX(version) AS keep_version
  FROM ui_config_release
  WHERE status = 'ACTIVE'
  GROUP BY config_type, config_id
) active_release
  ON active_release.config_type = release_row.config_type
 AND active_release.config_id = release_row.config_id
SET release_row.status = 'INACTIVE'
WHERE release_row.status = 'ACTIVE'
  AND release_row.version <> active_release.keep_version;

SET @active_slot_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ui_config_release'
    AND COLUMN_NAME = 'active_slot'
);

SET @add_active_slot_sql = IF(
  @active_slot_exists = 0,
  'ALTER TABLE ui_config_release
     ADD COLUMN active_slot tinyint
       GENERATED ALWAYS AS (
         CASE WHEN status = ''ACTIVE'' THEN 1 ELSE NULL END
       ) STORED
       COMMENT ''保证同一配置只有一个激活版本''
       AFTER status',
  'SELECT 1'
);
PREPARE add_active_slot_statement FROM @add_active_slot_sql;
EXECUTE add_active_slot_statement;
DEALLOCATE PREPARE add_active_slot_statement;

SET @active_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ui_config_release'
    AND INDEX_NAME = 'uk_ui_config_release_active'
);

SET @add_active_index_sql = IF(
  @active_index_exists = 0,
  'ALTER TABLE ui_config_release
     ADD UNIQUE KEY uk_ui_config_release_active
       (config_type, config_id, active_slot)',
  'SELECT 1'
);
PREPARE add_active_index_statement FROM @add_active_index_sql;
EXECUTE add_active_index_statement;
DEALLOCATE PREPARE add_active_index_statement;
