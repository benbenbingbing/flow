SET @operation_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_field'
    AND column_name = 'data_source_operation_code'
);
SET @operation_column_sql = IF(
  @operation_column_exists = 0,
  'ALTER TABLE `entity_list_field` ADD COLUMN `data_source_operation_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''统一数据源操作编码'' AFTER `data_source_id`',
  'SELECT 1'
);
PREPARE operation_column_statement FROM @operation_column_sql;
EXECUTE operation_column_statement;
DEALLOCATE PREPARE operation_column_statement;

SET @query_source_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'entity_list_config'
    AND column_name = 'query_data_source_id'
);
SET @query_source_column_sql = IF(
  @query_source_column_exists = 1,
  'ALTER TABLE `entity_list_config` DROP COLUMN `query_data_source_id`',
  'SELECT 1'
);
PREPARE query_source_column_statement FROM @query_source_column_sql;
EXECUTE query_source_column_statement;
DEALLOCATE PREPARE query_source_column_statement;

SET @input_schema_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ui_data_source_definition'
    AND column_name = 'input_schema_document'
);
SET @input_schema_column_sql = IF(
  @input_schema_column_exists = 1,
  'ALTER TABLE `ui_data_source_definition` DROP COLUMN `input_schema_document`',
  'SELECT 1'
);
PREPARE input_schema_column_statement FROM @input_schema_column_sql;
EXECUTE input_schema_column_statement;
DEALLOCATE PREPARE input_schema_column_statement;

SET @output_schema_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ui_data_source_definition'
    AND column_name = 'output_schema_document'
);
SET @output_schema_column_sql = IF(
  @output_schema_column_exists = 1,
  'ALTER TABLE `ui_data_source_definition` DROP COLUMN `output_schema_document`',
  'SELECT 1'
);
PREPARE output_schema_column_statement FROM @output_schema_column_sql;
EXECUTE output_schema_column_statement;
DEALLOCATE PREPARE output_schema_column_statement;

UPDATE `entity_list_field`
SET `data_source_id` = NULL,
    `data_source_operation_code` = NULL;

UPDATE `entity_list_config`
SET `active_release_id` = NULL,
    `draft_hash` = NULL;

UPDATE `entity_form`
SET `data_source_bindings_document` = NULL,
    `active_release_id` = NULL,
    `draft_hash` = NULL;

UPDATE `entity_form_node`
SET `data_source_bindings_document` = NULL;

UPDATE `entity_version_config`
SET `active_release_id` = NULL,
    `status` = 'DRAFT'
WHERE `id` IN (
  SELECT DISTINCT `config_id`
  FROM `entity_version_step`
  WHERE `step_type` = 'MANAGED_INTERFACE'
);

DELETE FROM `entity_version_config_release`
WHERE `config_id` IN (
  SELECT DISTINCT `config_id`
  FROM `entity_version_step`
  WHERE `step_type` = 'MANAGED_INTERFACE'
);

UPDATE `entity_version_step`
SET `provider_code` = NULL,
    `config_document` = NULL,
    `enabled` = 0
WHERE `step_type` = 'MANAGED_INTERFACE';

DELETE FROM `ui_event_binding`;
DELETE FROM `ui_data_source_definition`;
DELETE FROM `process_ui_release_binding`;
DELETE FROM `ui_config_hotfix_target`;
DELETE FROM `ui_config_release`;
