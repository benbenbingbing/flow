-- 完成接口服务扁平化的 contract 阶段。
--
-- V088 只负责 expand/backfill，并保留旧服务表及列表旧引用列。这里先对
-- 所有迁移结果做 fail-closed 校验，确认新扩展足以独立运行后，才删除旧
-- contract。全程只使用普通辅助表、information_schema 和 PREPARE，不要求
-- CREATE TEMPORARY TABLES 或 CREATE/ALTER ROUTINE 权限。

-- MySQL DDL 会隐式提交。失败后重跑时先清理本版本辅助表，避免上一次的
-- guard 行影响本次按数据库真实状态重新校验。
DROP TABLE IF EXISTS `flow_v089_interface_contract_guard`;

SET @flow_v089_source_table_exists = (
  SELECT COUNT(*)
  FROM `information_schema`.`tables`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'ui_data_source_definition'
    AND `table_type` = 'BASE TABLE'
);

SET @flow_v089_source_required_column_count = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'ui_data_source_definition'
    AND `column_name` IN ('id', 'operations_document', 'deleted')
);

SET @flow_v089_extension_required_column_count = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'ui_extension_definition'
    AND `column_name` IN (
      'id', 'extension_type', 'legacy_service_id',
      'provider_operation_code', 'deleted'
    )
);

SET @flow_v089_list_field_new_column_exists = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'entity_list_field'
    AND `column_name` = 'interface_extension_id'
);

SET @flow_v089_list_query_new_column_exists = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'entity_list_config'
    AND `column_name` = 'query_interface_extension_id'
);

SET @flow_v089_list_field_old_column_count = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'entity_list_field'
    AND `column_name` IN (
      'data_source_id', 'data_source_operation_code'
    )
);

SET @flow_v089_list_query_old_column_count = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'entity_list_config'
    AND `column_name` IN (
      'query_data_source_id', 'query_operation_code'
    )
);

SET @flow_v089_binding_document_column_count = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND (
      (`table_name` = 'entity_form'
        AND `column_name` = 'data_source_bindings_document')
      OR (`table_name` = 'entity_form_node'
        AND `column_name` = 'data_source_bindings_document')
      OR (`table_name` = 'ui_event_binding'
        AND `column_name` = 'steps_document')
    )
);

-- 旧服务表仍在时，逐操作核对确定性 extensionId 与 legacy pair。
-- 若旧表已经由一次接近完成的 V089 删除，则只有在全部旧列表列也已删除
-- 时才允许继续；这种状态不再尝试引用已经不存在的表。
SET @flow_v089_invalid_source_operation_count = 0;
SET @flow_v089_validate_source_operations_sql = IF(
  @flow_v089_source_table_exists = 1
    AND @flow_v089_source_required_column_count = 3
    AND @flow_v089_extension_required_column_count = 5,
  CONCAT(
    'SELECT ',
    '(SELECT COUNT(*) ',
    'FROM `ui_data_source_definition` source_definition ',
    'WHERE source_definition.`deleted` = 0 ',
    'AND CASE ',
    'WHEN source_definition.`operations_document` IS NULL ',
    'OR TRIM(source_definition.`operations_document`) = '''' THEN 1 ',
    'WHEN JSON_VALID(source_definition.`operations_document`) = 0 THEN 1 ',
    'WHEN JSON_TYPE(CAST(source_definition.`operations_document` AS JSON)) ',
    '<> ''ARRAY'' THEN 1 ',
    'WHEN JSON_LENGTH(CAST(source_definition.`operations_document` AS JSON)) ',
    '= 0 THEN 1 ELSE 0 END = 1) + ',
    '(SELECT COUNT(*) ',
    'FROM `ui_data_source_definition` source_definition ',
    'JOIN JSON_TABLE(',
    'CASE WHEN JSON_VALID(source_definition.`operations_document`) = 1 ',
    'AND JSON_TYPE(CAST(source_definition.`operations_document` AS JSON)) ',
    '= ''ARRAY'' ',
    'THEN CAST(source_definition.`operations_document` AS JSON) ',
    'ELSE JSON_ARRAY() END, ',
    '''$[*]'' COLUMNS (',
    '`operation_code` varchar(100) PATH ''$.code'' ',
    'NULL ON EMPTY NULL ON ERROR)) operation_row ',
    'LEFT JOIN `ui_extension_definition` extension_definition ',
    'ON extension_definition.`id` = MD5(CONCAT(',
    '''flow:v088:interface:'', source_definition.`id`, '':'', ',
    '(TRIM(operation_row.`operation_code`) ',
    'COLLATE utf8mb4_unicode_ci))) ',
    'AND extension_definition.`extension_type` = ''INTERFACE'' ',
    'AND extension_definition.`legacy_service_id` ',
    '= source_definition.`id` ',
    'AND extension_definition.`provider_operation_code` ',
    '= (TRIM(operation_row.`operation_code`) ',
    'COLLATE utf8mb4_unicode_ci) ',
    'AND extension_definition.`deleted` = 0 ',
    'WHERE source_definition.`deleted` = 0 ',
    'AND (NULLIF(TRIM(operation_row.`operation_code`), '''') IS NULL ',
    'OR extension_definition.`id` IS NULL)) ',
    'INTO @flow_v089_invalid_source_operation_count'
  ),
  'SELECT IF(@flow_v089_source_table_exists = 0, 0, 1) INTO @flow_v089_invalid_source_operation_count'
);
PREPARE `flow_v089_validate_source_operations_statement`
FROM @flow_v089_validate_source_operations_sql;
EXECUTE `flow_v089_validate_source_operations_statement`;
DEALLOCATE PREPARE `flow_v089_validate_source_operations_statement`;

-- 旧列表 pair 尚在时，新列必须精确指向由同一 serviceId + operationCode
-- 迁出的扩展；单边旧 pair、空的新 ID 或指向其他扩展都拒绝 contract。
SET @flow_v089_invalid_list_field_count = 0;
SET @flow_v089_validate_list_field_sql = IF(
  @flow_v089_list_field_old_column_count = 2
    AND @flow_v089_list_field_new_column_exists = 1
    AND @flow_v089_extension_required_column_count = 5,
  CONCAT(
    'SELECT COUNT(*) INTO @flow_v089_invalid_list_field_count ',
    'FROM `entity_list_field` list_field ',
    'WHERE (list_field.`data_source_id` IS NULL) ',
    '<> (list_field.`data_source_operation_code` IS NULL) ',
    'OR (list_field.`data_source_id` IS NOT NULL ',
    'AND list_field.`data_source_operation_code` IS NOT NULL ',
    'AND NOT EXISTS (SELECT 1 ',
    'FROM `ui_extension_definition` extension_definition ',
    'WHERE extension_definition.`id` ',
    '= list_field.`interface_extension_id` ',
    'AND extension_definition.`extension_type` = ''INTERFACE'' ',
    'AND extension_definition.`legacy_service_id` ',
    '= list_field.`data_source_id` ',
    'AND extension_definition.`provider_operation_code` ',
    '= list_field.`data_source_operation_code` ',
    'AND extension_definition.`deleted` = 0))'
  ),
  'SELECT IF(@flow_v089_list_field_old_column_count = 0, 0, 1) INTO @flow_v089_invalid_list_field_count'
);
PREPARE `flow_v089_validate_list_field_statement`
FROM @flow_v089_validate_list_field_sql;
EXECUTE `flow_v089_validate_list_field_statement`;
DEALLOCATE PREPARE `flow_v089_validate_list_field_statement`;

SET @flow_v089_invalid_list_query_count = 0;
SET @flow_v089_validate_list_query_sql = IF(
  @flow_v089_list_query_old_column_count = 2
    AND @flow_v089_list_query_new_column_exists = 1
    AND @flow_v089_extension_required_column_count = 5,
  CONCAT(
    'SELECT COUNT(*) INTO @flow_v089_invalid_list_query_count ',
    'FROM `entity_list_config` list_config ',
    'WHERE (list_config.`query_data_source_id` IS NULL) ',
    '<> (list_config.`query_operation_code` IS NULL) ',
    'OR (list_config.`query_data_source_id` IS NOT NULL ',
    'AND list_config.`query_operation_code` IS NOT NULL ',
    'AND NOT EXISTS (SELECT 1 ',
    'FROM `ui_extension_definition` extension_definition ',
    'WHERE extension_definition.`id` ',
    '= list_config.`query_interface_extension_id` ',
    'AND extension_definition.`extension_type` = ''INTERFACE'' ',
    'AND extension_definition.`legacy_service_id` ',
    '= list_config.`query_data_source_id` ',
    'AND extension_definition.`provider_operation_code` ',
    '= list_config.`query_operation_code` ',
    'AND extension_definition.`deleted` = 0))'
  ),
  'SELECT IF(@flow_v089_list_query_old_column_count = 0, 0, 1) INTO @flow_v089_invalid_list_query_count'
);
PREPARE `flow_v089_validate_list_query_statement`
FROM @flow_v089_validate_list_query_sql;
EXECUTE `flow_v089_validate_list_query_statement`;
DEALLOCATE PREPARE `flow_v089_validate_list_query_statement`;

-- 这里只校验 V088 主动改写的三类可变草稿。仍能通过 legacy pair 找到
-- 新扩展的旧引用说明 backfill 漏项，必须 fail-closed。源服务或操作本来就
-- 不存在的历史悬挂项没有可迁目标，保持原文交给应用校验，不能静默删除或
-- 伪造绑定。无效的非空 JSON 无法可靠分类，仍然拒绝 contract。
SET @flow_v089_invalid_form_binding_count = 0;
SET @flow_v089_invalid_node_binding_count = 0;
SET @flow_v089_invalid_event_step_count = 0;
SET @flow_v089_validate_binding_documents_sql = IF(
  @flow_v089_binding_document_column_count = 3
    AND @flow_v089_extension_required_column_count = 5,
  CONCAT(
    'WITH `binding_owners` AS (',
    'SELECT ''FORM'' AS `owner_type`, ',
    'form_definition.`data_source_bindings_document` AS `document` ',
    'FROM `entity_form` form_definition ',
    'UNION ALL ',
    'SELECT ''NODE'' AS `owner_type`, ',
    'form_node.`data_source_bindings_document` AS `document` ',
    'FROM `entity_form_node` form_node), ',
    '`binding_values` AS (',
    'SELECT binding_owner.`owner_type`, binding_row.`binding_document` ',
    'FROM `binding_owners` binding_owner ',
    'JOIN JSON_TABLE(',
    'CASE ',
    'WHEN binding_owner.`document` IS NULL ',
    'OR TRIM(binding_owner.`document`) = '''' THEN JSON_OBJECT() ',
    'WHEN JSON_VALID(binding_owner.`document`) = 0 THEN JSON_OBJECT() ',
    'WHEN JSON_TYPE(CAST(binding_owner.`document` AS JSON)) = ''OBJECT'' ',
    'THEN CAST(binding_owner.`document` AS JSON) ',
    'ELSE JSON_OBJECT() END, ',
    '''$.*'' COLUMNS (`binding_document` JSON PATH ''$'')) binding_row), ',
    '`legacy_binding_references` AS (',
    'SELECT binding_value.`owner_type`, ',
    'JSON_UNQUOTE(JSON_EXTRACT(',
    'binding_value.`binding_document`, ''$.serviceId'')) AS `service_id`, ',
    'JSON_UNQUOTE(JSON_EXTRACT(',
    'binding_value.`binding_document`, ''$.operationCode'')) ',
    'AS `operation_code` ',
    'FROM `binding_values` binding_value ',
    'WHERE JSON_TYPE(binding_value.`binding_document`) = ''OBJECT'' ',
    'UNION ALL ',
    'SELECT binding_value.`owner_type`, ',
    'JSON_UNQUOTE(JSON_EXTRACT(',
    'array_item.`item_document`, ''$.serviceId'')) AS `service_id`, ',
    'JSON_UNQUOTE(JSON_EXTRACT(',
    'array_item.`item_document`, ''$.operationCode'')) ',
    'AS `operation_code` ',
    'FROM `binding_values` binding_value ',
    'JOIN JSON_TABLE(',
    'CASE WHEN JSON_TYPE(binding_value.`binding_document`) = ''ARRAY'' ',
    'THEN binding_value.`binding_document` ELSE JSON_ARRAY() END, ',
    '''$[*]'' COLUMNS (`item_document` JSON PATH ''$'')) array_item ',
    'WHERE JSON_TYPE(array_item.`item_document`) = ''OBJECT''), ',
    '`resolvable_binding_references` AS (',
    'SELECT legacy_reference.`owner_type` ',
    'FROM `legacy_binding_references` legacy_reference ',
    'JOIN `ui_extension_definition` extension_definition ',
    'ON extension_definition.`legacy_service_id` ',
    '= (legacy_reference.`service_id` COLLATE utf8mb4_unicode_ci) ',
    'AND extension_definition.`provider_operation_code` ',
    '= (legacy_reference.`operation_code` COLLATE utf8mb4_unicode_ci) ',
    'AND extension_definition.`extension_type` = ''INTERFACE'' ',
    'AND extension_definition.`deleted` = 0), ',
    '`legacy_event_references` AS (',
    'SELECT ',
    'JSON_UNQUOTE(JSON_EXTRACT(',
    'event_step.`step_document`, ''$.serviceId'')) AS `service_id`, ',
    'JSON_UNQUOTE(JSON_EXTRACT(',
    'event_step.`step_document`, ''$.operationCode'')) ',
    'AS `operation_code` ',
    'FROM `ui_event_binding` event_binding ',
    'JOIN JSON_TABLE(',
    'CASE ',
    'WHEN event_binding.`steps_document` IS NULL ',
    'OR TRIM(event_binding.`steps_document`) = '''' THEN JSON_ARRAY() ',
    'WHEN JSON_VALID(event_binding.`steps_document`) = 0 ',
    'THEN JSON_ARRAY() ',
    'WHEN JSON_TYPE(CAST(event_binding.`steps_document` AS JSON)) ',
    '= ''ARRAY'' THEN CAST(event_binding.`steps_document` AS JSON) ',
    'ELSE JSON_ARRAY() END, ',
    '''$[*]'' COLUMNS (`step_document` JSON PATH ''$'')) event_step ',
    'WHERE JSON_TYPE(event_step.`step_document`) = ''OBJECT''), ',
    '`resolvable_event_references` AS (',
    'SELECT 1 AS `matched` ',
    'FROM `legacy_event_references` legacy_reference ',
    'JOIN `ui_extension_definition` extension_definition ',
    'ON extension_definition.`legacy_service_id` ',
    '= (legacy_reference.`service_id` COLLATE utf8mb4_unicode_ci) ',
    'AND extension_definition.`provider_operation_code` ',
    '= (legacy_reference.`operation_code` COLLATE utf8mb4_unicode_ci) ',
    'AND extension_definition.`extension_type` = ''INTERFACE'' ',
    'AND extension_definition.`deleted` = 0) ',
    'SELECT ',
    '(SELECT COUNT(*) FROM `entity_form` form_definition ',
    'WHERE CASE ',
    'WHEN form_definition.`data_source_bindings_document` IS NULL ',
    'OR TRIM(form_definition.`data_source_bindings_document`) = '''' THEN 0 ',
    'WHEN JSON_VALID(form_definition.`data_source_bindings_document`) = 0 ',
    'THEN 1 ELSE 0 END = 1) + ',
    '(SELECT COUNT(*) FROM `resolvable_binding_references` ',
    'WHERE `owner_type` = ''FORM''), ',
    '(SELECT COUNT(*) FROM `entity_form_node` form_node ',
    'WHERE CASE ',
    'WHEN form_node.`data_source_bindings_document` IS NULL ',
    'OR TRIM(form_node.`data_source_bindings_document`) = '''' THEN 0 ',
    'WHEN JSON_VALID(form_node.`data_source_bindings_document`) = 0 ',
    'THEN 1 ELSE 0 END = 1) + ',
    '(SELECT COUNT(*) FROM `resolvable_binding_references` ',
    'WHERE `owner_type` = ''NODE''), ',
    '(SELECT COUNT(*) FROM `ui_event_binding` event_binding ',
    'WHERE CASE ',
    'WHEN event_binding.`steps_document` IS NULL ',
    'OR TRIM(event_binding.`steps_document`) = '''' THEN 0 ',
    'WHEN JSON_VALID(event_binding.`steps_document`) = 0 ',
    'THEN 1 ELSE 0 END = 1) + ',
    '(SELECT COUNT(*) FROM `resolvable_event_references`) ',
    'INTO @flow_v089_invalid_form_binding_count, ',
    '@flow_v089_invalid_node_binding_count, ',
    '@flow_v089_invalid_event_step_count'
  ),
  CONCAT(
    'SELECT 1, 1, 1 INTO ',
    '@flow_v089_invalid_form_binding_count, ',
    '@flow_v089_invalid_node_binding_count, ',
    '@flow_v089_invalid_event_step_count'
  )
);
PREPARE `flow_v089_validate_binding_documents_statement`
FROM @flow_v089_validate_binding_documents_sql;
EXECUTE `flow_v089_validate_binding_documents_statement`;
DEALLOCATE PREPARE `flow_v089_validate_binding_documents_statement`;

-- 命名 CHECK 约束既给出明确失败原因，也保证在任何 DROP 之前停止。
CREATE TABLE `flow_v089_interface_contract_guard` (
  `required_schema_ready` tinyint NOT NULL,
  `source_schema_ready` tinyint NOT NULL,
  `list_field_old_shape_valid` tinyint NOT NULL,
  `list_query_old_shape_valid` tinyint NOT NULL,
  `drop_sequence_valid` tinyint NOT NULL,
  `invalid_source_operation_count` bigint NOT NULL,
  `invalid_list_field_count` bigint NOT NULL,
  `invalid_list_query_count` bigint NOT NULL,
  `invalid_form_binding_count` bigint NOT NULL,
  `invalid_node_binding_count` bigint NOT NULL,
  `invalid_event_step_count` bigint NOT NULL,
  `legacy_list_columns_removed` tinyint DEFAULT NULL,
  CONSTRAINT `chk_v089_required_schema_ready`
    CHECK (`required_schema_ready` = 1),
  CONSTRAINT `chk_v089_source_schema_ready`
    CHECK (`source_schema_ready` = 1),
  CONSTRAINT `chk_v089_list_field_old_shape`
    CHECK (`list_field_old_shape_valid` = 1),
  CONSTRAINT `chk_v089_list_query_old_shape`
    CHECK (`list_query_old_shape_valid` = 1),
  CONSTRAINT `chk_v089_drop_sequence`
    CHECK (`drop_sequence_valid` = 1),
  CONSTRAINT `chk_v089_source_operations_migrated`
    CHECK (`invalid_source_operation_count` = 0),
  CONSTRAINT `chk_v089_list_field_backfilled`
    CHECK (`invalid_list_field_count` = 0),
  CONSTRAINT `chk_v089_list_query_backfilled`
    CHECK (`invalid_list_query_count` = 0),
  CONSTRAINT `chk_v089_form_bindings_migrated`
    CHECK (`invalid_form_binding_count` = 0),
  CONSTRAINT `chk_v089_node_bindings_migrated`
    CHECK (`invalid_node_binding_count` = 0),
  CONSTRAINT `chk_v089_event_steps_migrated`
    CHECK (`invalid_event_step_count` = 0),
  CONSTRAINT `chk_v089_legacy_list_columns_removed`
    CHECK (`legacy_list_columns_removed` IS NULL
      OR `legacy_list_columns_removed` = 1)
) ENGINE=InnoDB;

INSERT INTO `flow_v089_interface_contract_guard` (
  `required_schema_ready`, `source_schema_ready`,
  `list_field_old_shape_valid`, `list_query_old_shape_valid`,
  `drop_sequence_valid`, `invalid_source_operation_count`,
  `invalid_list_field_count`, `invalid_list_query_count`,
  `invalid_form_binding_count`, `invalid_node_binding_count`,
  `invalid_event_step_count`
)
VALUES (
  IF(
    @flow_v089_extension_required_column_count = 5
      AND @flow_v089_list_field_new_column_exists = 1
      AND @flow_v089_list_query_new_column_exists = 1
      AND @flow_v089_binding_document_column_count = 3,
    1, 0
  ),
  IF(
    @flow_v089_source_table_exists = 0
      OR @flow_v089_source_required_column_count = 3,
    1, 0
  ),
  IF(@flow_v089_list_field_old_column_count IN (0, 2), 1, 0),
  IF(@flow_v089_list_query_old_column_count IN (0, 2), 1, 0),
  IF(
    @flow_v089_source_table_exists = 1
      OR (
        @flow_v089_list_field_old_column_count = 0
        AND @flow_v089_list_query_old_column_count = 0
      ),
    1, 0
  ),
  @flow_v089_invalid_source_operation_count,
  @flow_v089_invalid_list_field_count,
  @flow_v089_invalid_list_query_count,
  @flow_v089_invalid_form_binding_count,
  @flow_v089_invalid_node_binding_count,
  @flow_v089_invalid_event_step_count
);

-- 每张表的两个旧列在同一个原子 ALTER 中删除，避免产生只有半个 pair 的
-- 可观察状态；information_schema 分支让前一条 DDL 已提交后的重跑成为 no-op。
SET @flow_v089_drop_list_field_columns_sql = IF(
  @flow_v089_list_field_old_column_count = 2,
  'ALTER TABLE `entity_list_field` DROP COLUMN `data_source_id`, DROP COLUMN `data_source_operation_code`',
  'SELECT 1'
);
PREPARE `flow_v089_drop_list_field_columns_statement`
FROM @flow_v089_drop_list_field_columns_sql;
EXECUTE `flow_v089_drop_list_field_columns_statement`;
DEALLOCATE PREPARE `flow_v089_drop_list_field_columns_statement`;

SET @flow_v089_drop_list_query_columns_sql = IF(
  @flow_v089_list_query_old_column_count = 2,
  'ALTER TABLE `entity_list_config` DROP COLUMN `query_data_source_id`, DROP COLUMN `query_operation_code`',
  'SELECT 1'
);
PREPARE `flow_v089_drop_list_query_columns_statement`
FROM @flow_v089_drop_list_query_columns_sql;
EXECUTE `flow_v089_drop_list_query_columns_statement`;
DEALLOCATE PREPARE `flow_v089_drop_list_query_columns_statement`;

-- 再读一次真实 schema，只有两个 ALTER 都完成才允许删除旧服务表。
SET @flow_v089_remaining_legacy_list_column_count = (
  SELECT COUNT(*)
  FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND (
      (`table_name` = 'entity_list_field'
        AND `column_name` IN (
          'data_source_id', 'data_source_operation_code'
        ))
      OR (`table_name` = 'entity_list_config'
        AND `column_name` IN (
          'query_data_source_id', 'query_operation_code'
        ))
    )
);

UPDATE `flow_v089_interface_contract_guard`
SET `legacy_list_columns_removed` = IF(
  @flow_v089_remaining_legacy_list_column_count = 0, 1, 0
);

-- 会话变量只用于当前迁移，先清空；随后移除 helper。旧服务表必须是
-- 最后一个 DDL，使任何更早断点都仍保留可重算、可核验的迁移来源。
SET @flow_v089_source_table_exists = NULL;
SET @flow_v089_source_required_column_count = NULL;
SET @flow_v089_extension_required_column_count = NULL;
SET @flow_v089_list_field_new_column_exists = NULL;
SET @flow_v089_list_query_new_column_exists = NULL;
SET @flow_v089_list_field_old_column_count = NULL;
SET @flow_v089_list_query_old_column_count = NULL;
SET @flow_v089_binding_document_column_count = NULL;
SET @flow_v089_invalid_source_operation_count = NULL;
SET @flow_v089_validate_source_operations_sql = NULL;
SET @flow_v089_invalid_list_field_count = NULL;
SET @flow_v089_validate_list_field_sql = NULL;
SET @flow_v089_invalid_list_query_count = NULL;
SET @flow_v089_validate_list_query_sql = NULL;
SET @flow_v089_invalid_form_binding_count = NULL;
SET @flow_v089_invalid_node_binding_count = NULL;
SET @flow_v089_invalid_event_step_count = NULL;
SET @flow_v089_validate_binding_documents_sql = NULL;
SET @flow_v089_drop_list_field_columns_sql = NULL;
SET @flow_v089_drop_list_query_columns_sql = NULL;
SET @flow_v089_remaining_legacy_list_column_count = NULL;

DROP TABLE `flow_v089_interface_contract_guard`;

-- 数据与引用已完全迁往 ui_extension_definition，旧目录最后删除。若该 DDL
-- 已提交但 Flyway 尚未记成功，repair 后重跑会安全执行同一条 no-op。
DROP TABLE IF EXISTS `ui_data_source_definition`;
