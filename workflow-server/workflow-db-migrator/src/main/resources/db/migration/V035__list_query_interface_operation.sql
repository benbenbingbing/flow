SET @query_source_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_config'
    AND column_name = 'query_data_source_id'
);
SET @query_source_column_sql = IF(
  @query_source_column_exists = 0,
  'ALTER TABLE `entity_list_config` ADD COLUMN `query_data_source_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''统一列表查询数据源ID'' AFTER `draft_hash`',
  'SELECT 1'
);
PREPARE query_source_column_statement FROM @query_source_column_sql;
EXECUTE query_source_column_statement;
DEALLOCATE PREPARE query_source_column_statement;

SET @query_operation_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_config'
    AND column_name = 'query_operation_code'
);
SET @query_operation_column_sql = IF(
  @query_operation_column_exists = 0,
  'ALTER TABLE `entity_list_config` ADD COLUMN `query_operation_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''统一列表查询操作编码'' AFTER `query_data_source_id`',
  'SELECT 1'
);
PREPARE query_operation_column_statement FROM @query_operation_column_sql;
EXECUTE query_operation_column_statement;
DEALLOCATE PREPARE query_operation_column_statement;
