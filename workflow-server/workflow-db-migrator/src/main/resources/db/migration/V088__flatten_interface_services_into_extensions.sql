-- 将“接口服务 + 多操作”收敛为“一条扩展记录 = 一个可调用接口”。
--
-- 不可变的 ui_config_release / ui_config_hotfix_target 快照及其哈希不在本迁移中
-- 改写；迁移记录保留 legacy_service_id + provider_operation_code，供新运行时解析
-- 历史 serviceId + operationCode。当前草稿和关系列则迁移到单一 extensionId。

-- MySQL DDL 会隐式提交，失败后普通辅助表可能留在 schema 中。
-- 每次执行先清理本版本专用表，使 repair 后的重试不会被旧中间数据影响。
DROP TABLE IF EXISTS `flow_v088_interface_array_item_map`;
DROP TABLE IF EXISTS `flow_v088_migrated_data_bindings`;
DROP TABLE IF EXISTS `flow_v088_interface_target_guard`;
DROP TABLE IF EXISTS `flow_v088_interface_extension_stage`;
DROP TABLE IF EXISTS `flow_v088_interface_migration_guard`;
DROP TABLE IF EXISTS `flow_v088_interface_operation_map`;
DROP TABLE IF EXISTS `flow_v088_interface_schema_guard`;

-- V088 曾可能在首个 ALTER 已提交后失败。只接受“0 列”或“完整 12 列
-- 且唯一索引完整”两种状态；任意部分状态都 fail-closed，避免猜测式修补。
CREATE TABLE `flow_v088_interface_schema_guard` (
  `interface_column_count` int NOT NULL,
  `legacy_index_part_count` int NOT NULL,
  `legacy_index_match_count` int NOT NULL,
  CONSTRAINT `chk_flow_v088_interface_columns_complete`
    CHECK (`interface_column_count` IN (0, 12)),
  CONSTRAINT `chk_flow_v088_interface_index_complete`
    CHECK (
      (`interface_column_count` = 0
        AND `legacy_index_part_count` = 0
        AND `legacy_index_match_count` = 0)
      OR
      (`interface_column_count` = 12
        AND `legacy_index_part_count` = 3
        AND `legacy_index_match_count` = 3)
    )
) ENGINE=InnoDB;

INSERT INTO `flow_v088_interface_schema_guard` (
  `interface_column_count`, `legacy_index_part_count`,
  `legacy_index_match_count`
)
SELECT
  (SELECT COUNT(*)
   FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'ui_extension_definition'
     AND `column_name` IN (
       'implementation_type', 'provider_code', 'scope_type', 'scope_id',
       'implementation_config_document', 'execution_policy_document',
       'input_schema_document', 'output_schema_document', 'interface_kind',
       'interface_context_type', 'provider_operation_code',
       'legacy_service_id'
     )),
  (SELECT COUNT(*)
   FROM `information_schema`.`statistics`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'ui_extension_definition'
     AND `index_name` = 'uk_ui_extension_legacy_interface'),
  (SELECT COALESCE(SUM(
     CASE
       WHEN (`seq_in_index` = 1 AND `column_name` = 'legacy_service_id')
         OR (`seq_in_index` = 2
             AND `column_name` = 'provider_operation_code')
         OR (`seq_in_index` = 3 AND `column_name` = 'deleted')
       THEN 1 ELSE 0
     END
   ), 0)
   FROM `information_schema`.`statistics`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'ui_extension_definition'
     AND `index_name` = 'uk_ui_extension_legacy_interface'
     AND `non_unique` = 0);

SET @flow_v088_interface_column_count = (
  SELECT `interface_column_count`
  FROM `flow_v088_interface_schema_guard`
);

-- PREPARE 本身不需要 CREATE/ALTER ROUTINE 权限。已完整落列时只做无副作用
-- 查询；全新 V087 schema 才一次性增加 12 列和唯一索引。
SET @flow_v088_add_interface_columns_sql = IF(
  @flow_v088_interface_column_count = 0,
  CONCAT(
    'ALTER TABLE `ui_extension_definition` ',
    'ADD COLUMN `implementation_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''接口实现类型'' AFTER `capabilities_document`, ',
    'ADD COLUMN `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''Provider 实现编码'' AFTER `implementation_type`, ',
    'ADD COLUMN `scope_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''接口作用域：GLOBAL/ENTITY/FORM/LIST'' AFTER `provider_code`, ',
    'ADD COLUMN `scope_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''非 GLOBAL 作用域对象 ID'' AFTER `scope_type`, ',
    'ADD COLUMN `implementation_config_document` longtext COLLATE utf8mb4_unicode_ci COMMENT ''接口实现配置 JSON'' AFTER `scope_id`, ',
    'ADD COLUMN `execution_policy_document` longtext COLLATE utf8mb4_unicode_ci COMMENT ''接口执行策略 JSON'' AFTER `implementation_config_document`, ',
    'ADD COLUMN `input_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT ''接口输入 Schema JSON'' AFTER `execution_policy_document`, ',
    'ADD COLUMN `output_schema_document` longtext COLLATE utf8mb4_unicode_ci COMMENT ''接口输出 Schema JSON'' AFTER `input_schema_document`, ',
    'ADD COLUMN `interface_kind` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''接口读写类型：READ/WRITE'' AFTER `output_schema_document`, ',
    'ADD COLUMN `interface_context_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''接口调用上下文：FORM/LIST/ENTITY'' AFTER `interface_kind`, ',
    'ADD COLUMN `provider_operation_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''Provider 内部路由编码，不作为设计器二级选项'' AFTER `interface_context_type`, ',
    'ADD COLUMN `legacy_service_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''迁移前接口服务 ID，仅用于历史快照兼容'' AFTER `provider_operation_code`, ',
    'ADD UNIQUE KEY `uk_ui_extension_legacy_interface` ',
    '(`legacy_service_id`, `provider_operation_code`, `deleted`)'
  ),
  'SELECT 1'
);

PREPARE `flow_v088_add_interface_columns_statement`
FROM @flow_v088_add_interface_columns_sql;
EXECUTE `flow_v088_add_interface_columns_statement`;
DEALLOCATE PREPARE `flow_v088_add_interface_columns_statement`;

-- MODIFY 对全新和断点续跑两种状态都是幂等的。
ALTER TABLE `ui_extension_definition`
  MODIFY COLUMN `extension_type` varchar(20)
    COLLATE utf8mb4_unicode_ci NOT NULL
    COMMENT '扩展类型：FORM/NODE/FIELD/LIST/INTERFACE',
  MODIFY COLUMN `extension_key` varchar(255)
    COLLATE utf8mb4_unicode_ci NOT NULL
    COMMENT '扩展稳定编码；INTERFACE 为完整可调用接口编码',
  COMMENT = '统一扩展定义目录';

-- 普通辅助表只在 V088 执行期间存在，不依赖 CREATE TEMPORARY TABLES。
CREATE TABLE `flow_v088_interface_operation_map` (
  `legacy_service_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_ordinal` int NOT NULL,
  `operation_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `interface_kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `interface_context_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_document` json NOT NULL,
  `extension_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `extension_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`legacy_service_id`, `operation_code`),
  UNIQUE KEY `uk_flow_v088_interface_extension_id` (`extension_id`),
  UNIQUE KEY `uk_flow_v088_interface_extension_key` (`extension_key`),
  CONSTRAINT `chk_flow_v088_interface_kind`
    CHECK (`interface_kind` IN ('READ', 'WRITE')),
  CONSTRAINT `chk_flow_v088_interface_context`
    CHECK (`interface_context_type` IN ('FORM', 'LIST', 'ENTITY'))
) ENGINE=InnoDB;

INSERT INTO `flow_v088_interface_operation_map` (
  `legacy_service_id`, `operation_ordinal`, `operation_code`,
  `operation_name`, `interface_kind`, `interface_context_type`,
  `operation_document`, `extension_id`, `extension_key`
)
SELECT
  source_definition.`id`,
  operation_row.`operation_ordinal`,
  NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
    operation_row.`operation_document`, '$.code'))), ''),
  COALESCE(
    NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
      operation_row.`operation_document`, '$.name'))), ''),
    NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
      operation_row.`operation_document`, '$.code'))), ''),
    source_definition.`source_name`
  ),
  UPPER(COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
    operation_row.`operation_document`, '$.kind'))), ''), 'READ')),
  UPPER(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
    operation_row.`operation_document`, '$.contextType'))), '')),
  operation_row.`operation_document`,
  MD5(CONCAT(
    'flow:v088:interface:', source_definition.`id`, ':',
    TRIM(JSON_UNQUOTE(JSON_EXTRACT(
      operation_row.`operation_document`, '$.code')))
  )),
  CASE
    WHEN CHAR_LENGTH(CONCAT(
      source_definition.`source_code`, '.',
      TRIM(JSON_UNQUOTE(JSON_EXTRACT(
        operation_row.`operation_document`, '$.code')))
    )) <= 255
      THEN CONCAT(
        source_definition.`source_code`, '.',
        TRIM(JSON_UNQUOTE(JSON_EXTRACT(
          operation_row.`operation_document`, '$.code')))
      )
    ELSE CONCAT(
      LEFT(source_definition.`source_code`, 100), '.',
      LEFT(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
        operation_row.`operation_document`, '$.code'))), 88), '.',
      SHA2(CONCAT(
        source_definition.`source_code`, '.',
        TRIM(JSON_UNQUOTE(JSON_EXTRACT(
          operation_row.`operation_document`, '$.code')))
      ), 256)
    )
  END
FROM `ui_data_source_definition` source_definition
JOIN JSON_TABLE(
  CASE
    WHEN JSON_VALID(source_definition.`operations_document`) = 1
      AND JSON_TYPE(CAST(
        source_definition.`operations_document` AS JSON)) = 'ARRAY'
      THEN CAST(source_definition.`operations_document` AS JSON)
    ELSE JSON_ARRAY()
  END,
  '$[*]' COLUMNS (
    `operation_ordinal` FOR ORDINALITY,
    `operation_document` JSON PATH '$'
  )
) operation_row
WHERE source_definition.`deleted` = 0;

-- 用普通表 CHECK 约束实现 fail-closed guard，不依赖存储过程。
-- 任一活动服务未解析出操作，或列表存在单边/悬挂历史引用时，
-- INSERT 会违反对应的命名约束并立即中止迁移。
CREATE TABLE `flow_v088_interface_migration_guard` (
  `all_sources_mapped` tinyint NOT NULL,
  `invalid_field_references` bigint NOT NULL,
  `invalid_query_references` bigint NOT NULL,
  CONSTRAINT `chk_flow_v088_all_sources_mapped`
    CHECK (`all_sources_mapped` = 1),
  CONSTRAINT `chk_flow_v088_list_field_refs_mapped`
    CHECK (`invalid_field_references` = 0),
  CONSTRAINT `chk_flow_v088_list_config_refs_mapped`
    CHECK (`invalid_query_references` = 0)
) ENGINE=InnoDB;

INSERT INTO `flow_v088_interface_migration_guard` (
  `all_sources_mapped`, `invalid_field_references`,
  `invalid_query_references`
)
SELECT
  IF(
    (SELECT COUNT(*)
       FROM `ui_data_source_definition`
      WHERE `deleted` = 0)
    =
    (SELECT COUNT(DISTINCT `legacy_service_id`)
       FROM `flow_v088_interface_operation_map`),
    1,
    0
  ),
  (SELECT COUNT(*)
   FROM `entity_list_field` list_field
   WHERE (list_field.`data_source_id` IS NULL)
           <> (list_field.`data_source_operation_code` IS NULL)
      OR (
        list_field.`data_source_id` IS NOT NULL
        AND list_field.`data_source_operation_code` IS NOT NULL
        AND NOT EXISTS (
          SELECT 1
          FROM `flow_v088_interface_operation_map` operation_map
          WHERE operation_map.`legacy_service_id`
                  = list_field.`data_source_id`
            AND operation_map.`operation_code`
                  = list_field.`data_source_operation_code`
        )
      )),
  (SELECT COUNT(*)
   FROM `entity_list_config` list_config
   WHERE (list_config.`query_data_source_id` IS NULL)
           <> (list_config.`query_operation_code` IS NULL)
      OR (
        list_config.`query_data_source_id` IS NOT NULL
        AND list_config.`query_operation_code` IS NOT NULL
        AND NOT EXISTS (
          SELECT 1
          FROM `flow_v088_interface_operation_map` operation_map
          WHERE operation_map.`legacy_service_id`
                  = list_config.`query_data_source_id`
            AND operation_map.`operation_code`
                  = list_config.`query_operation_code`
        )
      ));

-- JSON_MERGE_PATCH 会删除显式 null，和旧 Java Map.putAll 语义不同。递归 CTE
-- 逐个 JSON_SET 操作级 key，准确实现“服务级配置 + 操作级浅层覆盖”。
CREATE TABLE `flow_v088_interface_extension_stage`
LIKE `ui_extension_definition`;

INSERT INTO `flow_v088_interface_extension_stage` (
  `id`, `extension_type`, `extension_key`, `display_name`, `version`,
  `snapshot_version`, `visibility_scope`, `entity_codes_document`,
  `supported_modes_document`, `supported_node_types_document`,
  `supported_bindings_document`, `config_schema_document`,
  `capabilities_document`, `implementation_type`, `provider_code`,
  `scope_type`, `scope_id`, `implementation_config_document`,
  `execution_policy_document`, `input_schema_document`,
  `output_schema_document`, `interface_kind`, `interface_context_type`,
  `provider_operation_code`, `legacy_service_id`, `status`, `revision`,
  `create_time`, `update_time`, `deleted`
)
WITH RECURSIVE `interface_source` AS (
  SELECT
    operation_map.*,
    source_definition.`source_name`,
    source_definition.`source_type`,
    source_definition.`provider_code`,
    source_definition.`scope_type`,
    source_definition.`scope_id`,
    source_definition.`enabled`,
    source_definition.`revision`,
    source_definition.`create_time`,
    source_definition.`update_time`,
    CASE
      WHEN source_definition.`config_document` IS NULL
        OR TRIM(source_definition.`config_document`) = ''
        THEN JSON_OBJECT()
      ELSE CAST(source_definition.`config_document` AS JSON)
    END AS `base_config`,
    CASE
      WHEN JSON_TYPE(JSON_EXTRACT(
        operation_map.`operation_document`, '$.config')) = 'OBJECT'
        THEN JSON_EXTRACT(operation_map.`operation_document`, '$.config')
      ELSE JSON_OBJECT()
    END AS `operation_config`,
    CASE
      WHEN source_definition.`execution_policy_document` IS NULL
        OR TRIM(source_definition.`execution_policy_document`) = ''
        THEN JSON_OBJECT()
      ELSE CAST(source_definition.`execution_policy_document` AS JSON)
    END AS `base_policy`,
    CASE
      WHEN JSON_TYPE(JSON_EXTRACT(
        operation_map.`operation_document`, '$.executionPolicy')) = 'OBJECT'
        THEN JSON_EXTRACT(
          operation_map.`operation_document`, '$.executionPolicy')
      ELSE JSON_OBJECT()
    END AS `operation_policy`
  FROM `flow_v088_interface_operation_map` operation_map
  JOIN `ui_data_source_definition` source_definition
    ON source_definition.`id` = operation_map.`legacy_service_id`
), `merge_state` AS (
  SELECT
    interface_source.*,
    JSON_KEYS(interface_source.`operation_config`) AS `config_keys`,
    JSON_KEYS(interface_source.`operation_policy`) AS `policy_keys`,
    0 AS `merge_index`,
    interface_source.`base_config` AS `merged_config`,
    interface_source.`base_policy` AS `merged_policy`
  FROM `interface_source`

  UNION ALL

  SELECT
    merge_state.`legacy_service_id`,
    merge_state.`operation_ordinal`,
    merge_state.`operation_code`,
    merge_state.`operation_name`,
    merge_state.`interface_kind`,
    merge_state.`interface_context_type`,
    merge_state.`operation_document`,
    merge_state.`extension_id`,
    merge_state.`extension_key`,
    merge_state.`source_name`,
    merge_state.`source_type`,
    merge_state.`provider_code`,
    merge_state.`scope_type`,
    merge_state.`scope_id`,
    merge_state.`enabled`,
    merge_state.`revision`,
    merge_state.`create_time`,
    merge_state.`update_time`,
    merge_state.`base_config`,
    merge_state.`operation_config`,
    merge_state.`base_policy`,
    merge_state.`operation_policy`,
    merge_state.`config_keys`,
    merge_state.`policy_keys`,
    merge_state.`merge_index` + 1,
    CASE
      WHEN merge_state.`merge_index`
        < JSON_LENGTH(merge_state.`config_keys`)
        THEN JSON_SET(
          merge_state.`merged_config`,
          CONCAT(
            '$."',
            REPLACE(REPLACE(
              JSON_UNQUOTE(JSON_EXTRACT(
                merge_state.`config_keys`,
                CONCAT('$[', merge_state.`merge_index`, ']')
              )),
              CHAR(92), CONCAT(CHAR(92), CHAR(92))
            ), CHAR(34), CONCAT(CHAR(92), CHAR(34))),
            '"'
          ),
          JSON_EXTRACT(
            merge_state.`operation_config`,
            CONCAT(
              '$."',
              REPLACE(REPLACE(
                JSON_UNQUOTE(JSON_EXTRACT(
                  merge_state.`config_keys`,
                  CONCAT('$[', merge_state.`merge_index`, ']')
                )),
                CHAR(92), CONCAT(CHAR(92), CHAR(92))
              ), CHAR(34), CONCAT(CHAR(92), CHAR(34))),
              '"'
            )
          )
        )
      ELSE merge_state.`merged_config`
    END,
    CASE
      WHEN merge_state.`merge_index`
        < JSON_LENGTH(merge_state.`policy_keys`)
        THEN JSON_SET(
          merge_state.`merged_policy`,
          CONCAT(
            '$."',
            REPLACE(REPLACE(
              JSON_UNQUOTE(JSON_EXTRACT(
                merge_state.`policy_keys`,
                CONCAT('$[', merge_state.`merge_index`, ']')
              )),
              CHAR(92), CONCAT(CHAR(92), CHAR(92))
            ), CHAR(34), CONCAT(CHAR(92), CHAR(34))),
            '"'
          ),
          JSON_EXTRACT(
            merge_state.`operation_policy`,
            CONCAT(
              '$."',
              REPLACE(REPLACE(
                JSON_UNQUOTE(JSON_EXTRACT(
                  merge_state.`policy_keys`,
                  CONCAT('$[', merge_state.`merge_index`, ']')
                )),
                CHAR(92), CONCAT(CHAR(92), CHAR(92))
              ), CHAR(34), CONCAT(CHAR(92), CHAR(34))),
              '"'
            )
          )
        )
      ELSE merge_state.`merged_policy`
    END
  FROM `merge_state`
  WHERE merge_state.`merge_index` < GREATEST(
    JSON_LENGTH(merge_state.`config_keys`),
    JSON_LENGTH(merge_state.`policy_keys`)
  )
)
SELECT
  `extension_id`,
  'INTERFACE',
  `extension_key`,
  LEFT(CONCAT(`source_name`, ' / ', `operation_name`), 200),
  1,
  1,
  'GLOBAL',
  JSON_ARRAY(),
  JSON_ARRAY(),
  JSON_ARRAY(),
  JSON_ARRAY(),
  JSON_OBJECT(),
  JSON_OBJECT('migratedFrom', 'ui_data_source_definition'),
  `source_type`,
  `provider_code`,
  `scope_type`,
  `scope_id`,
  `merged_config`,
  `merged_policy`,
  CASE
    WHEN JSON_TYPE(JSON_EXTRACT(`operation_document`, '$.inputSchema'))
      = 'OBJECT'
      THEN JSON_EXTRACT(`operation_document`, '$.inputSchema')
    ELSE JSON_OBJECT()
  END,
  CASE
    WHEN JSON_TYPE(JSON_EXTRACT(`operation_document`, '$.outputSchema'))
      = 'OBJECT'
      THEN JSON_EXTRACT(`operation_document`, '$.outputSchema')
    ELSE JSON_OBJECT()
  END,
  `interface_kind`,
  `interface_context_type`,
  `operation_code`,
  `legacy_service_id`,
  IF(`enabled` = 1, 'ACTIVE', 'DISABLED'),
  `revision`,
  `create_time`,
  `update_time`,
  0
FROM `merge_state`
WHERE `merge_index` = GREATEST(
  JSON_LENGTH(`config_keys`), JSON_LENGTH(`policy_keys`)
);

-- 断点续跑时允许上一次 V088 已完整插入的同一行；任何 ID、
-- extension key 或 legacy pair 指向其他内容都属于真实冲突，必须 fail-closed。
CREATE TABLE `flow_v088_interface_target_guard` (
  `conflicting_target_count` bigint NOT NULL,
  CONSTRAINT `chk_flow_v088_interface_targets_compatible`
    CHECK (`conflicting_target_count` = 0)
) ENGINE=InnoDB;

INSERT INTO `flow_v088_interface_target_guard` (`conflicting_target_count`)
SELECT COUNT(*)
FROM `flow_v088_interface_extension_stage` staged
JOIN `ui_extension_definition` existing
  ON existing.`id` = staged.`id`
  OR (
    existing.`extension_type` = staged.`extension_type`
    AND existing.`extension_key` = staged.`extension_key`
    AND existing.`version` = staged.`version`
    AND existing.`deleted` = staged.`deleted`
  )
  OR (
    existing.`legacy_service_id` = staged.`legacy_service_id`
    AND existing.`provider_operation_code`
          = staged.`provider_operation_code`
    AND existing.`deleted` = staged.`deleted`
  )
WHERE NOT (
  existing.`id` = staged.`id`
  AND existing.`extension_type` = staged.`extension_type`
  AND existing.`extension_key` = staged.`extension_key`
  AND existing.`display_name` = staged.`display_name`
  AND existing.`version` = staged.`version`
  AND existing.`snapshot_version` = staged.`snapshot_version`
  AND existing.`visibility_scope` = staged.`visibility_scope`
  AND existing.`entity_codes_document`
        <=> staged.`entity_codes_document`
  AND existing.`supported_modes_document`
        <=> staged.`supported_modes_document`
  AND existing.`supported_node_types_document`
        <=> staged.`supported_node_types_document`
  AND existing.`supported_bindings_document`
        <=> staged.`supported_bindings_document`
  AND existing.`config_schema_document`
        <=> staged.`config_schema_document`
  AND existing.`capabilities_document`
        <=> staged.`capabilities_document`
  AND existing.`implementation_type`
        <=> staged.`implementation_type`
  AND existing.`provider_code` <=> staged.`provider_code`
  AND existing.`scope_type` <=> staged.`scope_type`
  AND existing.`scope_id` <=> staged.`scope_id`
  AND existing.`implementation_config_document`
        <=> staged.`implementation_config_document`
  AND existing.`execution_policy_document`
        <=> staged.`execution_policy_document`
  AND existing.`input_schema_document`
        <=> staged.`input_schema_document`
  AND existing.`output_schema_document`
        <=> staged.`output_schema_document`
  AND existing.`interface_kind` <=> staged.`interface_kind`
  AND existing.`interface_context_type`
        <=> staged.`interface_context_type`
  AND existing.`provider_operation_code`
        <=> staged.`provider_operation_code`
  AND existing.`legacy_service_id` <=> staged.`legacy_service_id`
  AND existing.`status` = staged.`status`
  AND existing.`revision` = staged.`revision`
  AND existing.`create_time` = staged.`create_time`
  AND existing.`update_time` = staged.`update_time`
  AND existing.`deleted` = staged.`deleted`
);

-- 先 guard 后幂等插入：重跑时已有的完整迁移行保持不动。
INSERT INTO `ui_extension_definition` (
  `id`, `extension_type`, `extension_key`, `display_name`, `version`,
  `snapshot_version`, `visibility_scope`, `entity_codes_document`,
  `supported_modes_document`, `supported_node_types_document`,
  `supported_bindings_document`, `config_schema_document`,
  `capabilities_document`, `implementation_type`, `provider_code`,
  `scope_type`, `scope_id`, `implementation_config_document`,
  `execution_policy_document`, `input_schema_document`,
  `output_schema_document`, `interface_kind`, `interface_context_type`,
  `provider_operation_code`, `legacy_service_id`, `status`, `revision`,
  `create_time`, `update_time`, `deleted`
)
SELECT
  staged.`id`, staged.`extension_type`, staged.`extension_key`,
  staged.`display_name`, staged.`version`, staged.`snapshot_version`,
  staged.`visibility_scope`, staged.`entity_codes_document`,
  staged.`supported_modes_document`, staged.`supported_node_types_document`,
  staged.`supported_bindings_document`, staged.`config_schema_document`,
  staged.`capabilities_document`, staged.`implementation_type`,
  staged.`provider_code`, staged.`scope_type`, staged.`scope_id`,
  staged.`implementation_config_document`,
  staged.`execution_policy_document`, staged.`input_schema_document`,
  staged.`output_schema_document`, staged.`interface_kind`,
  staged.`interface_context_type`, staged.`provider_operation_code`,
  staged.`legacy_service_id`, staged.`status`, staged.`revision`,
  staged.`create_time`, staged.`update_time`, staged.`deleted`
FROM `flow_v088_interface_extension_stage` staged
WHERE NOT EXISTS (
  SELECT 1
  FROM `ui_extension_definition` existing
  WHERE existing.`id` = staged.`id`
);

-- V088 只 expand/backfill：新列可以独立续跑，旧 pair 由 V089 校验后再删除。
SET @flow_v088_add_list_field_extension_sql = IF(
  (SELECT COUNT(*)
   FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'entity_list_field'
     AND `column_name` = 'interface_extension_id') = 0,
  'ALTER TABLE `entity_list_field` ADD COLUMN `interface_extension_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''列表列数据接口扩展 ID'' AFTER `data_source_operation_code`',
  'SELECT 1'
);
PREPARE `flow_v088_add_list_field_extension_statement`
FROM @flow_v088_add_list_field_extension_sql;
EXECUTE `flow_v088_add_list_field_extension_statement`;
DEALLOCATE PREPARE `flow_v088_add_list_field_extension_statement`;

SET @flow_v088_add_list_query_extension_sql = IF(
  (SELECT COUNT(*)
   FROM `information_schema`.`columns`
   WHERE `table_schema` = DATABASE()
     AND `table_name` = 'entity_list_config'
     AND `column_name` = 'query_interface_extension_id') = 0,
  'ALTER TABLE `entity_list_config` ADD COLUMN `query_interface_extension_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''列表查询接口扩展 ID'' AFTER `query_operation_code`',
  'SELECT 1'
);
PREPARE `flow_v088_add_list_query_extension_statement`
FROM @flow_v088_add_list_query_extension_sql;
EXECUTE `flow_v088_add_list_query_extension_statement`;
DEALLOCATE PREPARE `flow_v088_add_list_query_extension_statement`;

UPDATE `entity_list_field` list_field
JOIN `flow_v088_interface_operation_map` operation_map
  ON operation_map.`legacy_service_id` = list_field.`data_source_id`
 AND operation_map.`operation_code`
      = list_field.`data_source_operation_code`
SET list_field.`interface_extension_id` = operation_map.`extension_id`
WHERE NOT (list_field.`interface_extension_id`
             <=> operation_map.`extension_id`);

UPDATE `entity_list_config` list_config
JOIN `flow_v088_interface_operation_map` operation_map
  ON operation_map.`legacy_service_id` = list_config.`query_data_source_id`
 AND operation_map.`operation_code` = list_config.`query_operation_code`
SET list_config.`query_interface_extension_id` = operation_map.`extension_id`
WHERE NOT (list_config.`query_interface_extension_id`
             <=> operation_map.`extension_id`);

-- 表单与节点数据接口绑定是 usage -> binding|binding[] 的 JSON
-- 对象。同一 usage 可以有多个有序步骤，因此单对象和数组元素都必须
-- 按 serviceId + operationCode 精确转换。损坏、悬挂或非标准历史值
-- 原样保留，交给应用校验给出可定位的错误。
CREATE TABLE `flow_v088_migrated_data_bindings` (
  `owner_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `binding_document` json NOT NULL,
  PRIMARY KEY (`owner_type`, `owner_id`)
) ENGINE=InnoDB;

INSERT INTO `flow_v088_migrated_data_bindings` (
  `owner_type`, `owner_id`, `binding_document`
)
WITH `binding_owners` AS (
  SELECT
    'FORM' AS `owner_type`,
    form_definition.`id` AS `owner_id`,
    CAST(form_definition.`data_source_bindings_document` AS JSON)
      AS `bindings_document`
  FROM `entity_form` form_definition
  WHERE JSON_VALID(form_definition.`data_source_bindings_document`) = 1
    AND JSON_TYPE(CAST(
      form_definition.`data_source_bindings_document` AS JSON)) = 'OBJECT'

  UNION ALL

  SELECT
    'NODE' AS `owner_type`,
    form_node.`id` AS `owner_id`,
    CAST(form_node.`data_source_bindings_document` AS JSON)
      AS `bindings_document`
  FROM `entity_form_node` form_node
  WHERE JSON_VALID(form_node.`data_source_bindings_document`) = 1
    AND JSON_TYPE(CAST(
      form_node.`data_source_bindings_document` AS JSON)) = 'OBJECT'
), `binding_values` AS (
  SELECT
    binding_owner.`owner_type`,
    binding_owner.`owner_id`,
    binding_key_row.`binding_key`,
    JSON_EXTRACT(
      binding_owner.`bindings_document`,
      CONCAT(
        '$."',
        REPLACE(REPLACE(
          binding_key_row.`binding_key`,
          CHAR(92), CONCAT(CHAR(92), CHAR(92))
        ), CHAR(34), CONCAT(CHAR(92), CHAR(34))),
        '"'
      )
    ) AS `binding_document`
  FROM `binding_owners` binding_owner
  JOIN JSON_TABLE(
    JSON_KEYS(binding_owner.`bindings_document`),
    '$[*]' COLUMNS (
      `binding_key` varchar(255) PATH '$'
    )
  ) binding_key_row
), `array_item_rows` AS (
  SELECT
    binding_value.`owner_type`,
    binding_value.`owner_id`,
    binding_value.`binding_key`,
    item_row.`item_ordinal`,
    item_row.`item_document`
  FROM `binding_values` binding_value
  JOIN JSON_TABLE(
    CASE
      WHEN JSON_TYPE(binding_value.`binding_document`) = 'ARRAY'
        THEN binding_value.`binding_document`
      ELSE JSON_ARRAY()
    END,
    '$[*]' COLUMNS (
      `item_ordinal` FOR ORDINALITY,
      `item_document` JSON PATH '$'
    )
  ) item_row
), `migrated_array_items` AS (
  SELECT
    item_row.`owner_type`,
    item_row.`owner_id`,
    item_row.`binding_key`,
    item_row.`item_ordinal`,
    CASE
      WHEN operation_map.`extension_id` IS NULL
        THEN item_row.`item_document`
      ELSE JSON_REMOVE(
        JSON_SET(
          item_row.`item_document`,
          '$.extensionId', operation_map.`extension_id`
        ),
        '$.serviceId', '$.operationCode', '$.sourceCode',
        '$.serviceName', '$.serviceRevision', '$.operationName'
      )
    END AS `item_document`,
    IF(operation_map.`extension_id` IS NULL, 0, 1) AS `was_migrated`
  FROM `array_item_rows` item_row
  LEFT JOIN `flow_v088_interface_operation_map` operation_map
    ON operation_map.`legacy_service_id` = JSON_UNQUOTE(JSON_EXTRACT(
         item_row.`item_document`, '$.serviceId'))
   AND operation_map.`operation_code` = JSON_UNQUOTE(JSON_EXTRACT(
         item_row.`item_document`, '$.operationCode'))
), `array_windows` AS (
  SELECT
    `owner_type`,
    `owner_id`,
    `binding_key`,
    JSON_ARRAYAGG(`item_document`) OVER (
      PARTITION BY `owner_type`, `owner_id`, `binding_key`
      ORDER BY `item_ordinal`
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS `array_document`,
    MAX(`was_migrated`) OVER (
      PARTITION BY `owner_type`, `owner_id`, `binding_key`
    ) AS `was_migrated`,
    ROW_NUMBER() OVER (
      PARTITION BY `owner_type`, `owner_id`, `binding_key`
      ORDER BY `item_ordinal` DESC
    ) AS `last_item`
  FROM `migrated_array_items`
), `migrated_arrays` AS (
  SELECT
    `owner_type`, `owner_id`, `binding_key`, `array_document`
  FROM `array_windows`
  WHERE `last_item` = 1
    AND `was_migrated` = 1
), `migrated_binding_values` AS (
  SELECT
    binding_value.`owner_type`,
    binding_value.`owner_id`,
    binding_value.`binding_key`,
    CASE
      WHEN object_map.`extension_id` IS NOT NULL
        THEN JSON_REMOVE(
          JSON_SET(
            binding_value.`binding_document`,
            '$.extensionId', object_map.`extension_id`
          ),
          '$.serviceId', '$.operationCode', '$.sourceCode',
          '$.serviceName', '$.serviceRevision', '$.operationName'
        )
      WHEN migrated_array.`array_document` IS NOT NULL
        THEN migrated_array.`array_document`
      ELSE binding_value.`binding_document`
    END AS `binding_document`,
    IF(object_map.`extension_id` IS NOT NULL
        OR migrated_array.`array_document` IS NOT NULL, 1, 0)
      AS `was_migrated`
  FROM `binding_values` binding_value
  LEFT JOIN `flow_v088_interface_operation_map` object_map
    ON JSON_TYPE(binding_value.`binding_document`) = 'OBJECT'
   AND object_map.`legacy_service_id` = JSON_UNQUOTE(JSON_EXTRACT(
         binding_value.`binding_document`, '$.serviceId'))
   AND object_map.`operation_code` = JSON_UNQUOTE(JSON_EXTRACT(
         binding_value.`binding_document`, '$.operationCode'))
  LEFT JOIN `migrated_arrays` migrated_array
    ON migrated_array.`owner_type` = binding_value.`owner_type`
   AND migrated_array.`owner_id` = binding_value.`owner_id`
   AND migrated_array.`binding_key` = binding_value.`binding_key`
), `migrated_documents` AS (
  SELECT
    `owner_type`,
    `owner_id`,
    JSON_OBJECTAGG(`binding_key`, `binding_document`) AS `binding_document`,
    MAX(`was_migrated`) AS `was_migrated`
  FROM `migrated_binding_values`
  GROUP BY `owner_type`, `owner_id`
)
SELECT `owner_type`, `owner_id`, `binding_document`
FROM `migrated_documents`
WHERE `was_migrated` = 1;

UPDATE `entity_form` form_definition
JOIN `flow_v088_migrated_data_bindings` migrated
  ON migrated.`owner_type` = 'FORM'
 AND migrated.`owner_id` = form_definition.`id`
SET form_definition.`data_source_bindings_document`
      = migrated.`binding_document`,
    form_definition.`revision` = form_definition.`revision` + 1,
    form_definition.`draft_hash` = NULL,
    form_definition.`update_time` = CURRENT_TIMESTAMP;

UPDATE `entity_form_node` form_node
JOIN `flow_v088_migrated_data_bindings` migrated
  ON migrated.`owner_type` = 'NODE'
 AND migrated.`owner_id` = form_node.`id`
SET form_node.`data_source_bindings_document` = migrated.`binding_document`,
    form_node.`revision` = form_node.`revision` + 1,
    form_node.`update_time` = CURRENT_TIMESTAMP;

-- 事件绑定是实际运行配置，不能按“事件使用情况”删除。这里只迁移其可变草稿，
-- 已发布快照仍由 legacy_service_id 兼容，避免破坏内容哈希。
WITH `event_step_rows` AS (
  SELECT
    binding.`id`,
    step_row.`step_ordinal`,
    step_row.`step_document`,
    operation_map.`extension_id`
  FROM `ui_event_binding` binding
  JOIN JSON_TABLE(
    CASE
      WHEN JSON_VALID(binding.`steps_document`) = 1
        AND JSON_TYPE(CAST(binding.`steps_document` AS JSON)) = 'ARRAY'
        THEN CAST(binding.`steps_document` AS JSON)
      ELSE JSON_ARRAY()
    END,
    '$[*]' COLUMNS (
      `step_ordinal` FOR ORDINALITY,
      `step_document` JSON PATH '$'
    )
  ) step_row
  LEFT JOIN `flow_v088_interface_operation_map` operation_map
    ON operation_map.`legacy_service_id` = JSON_UNQUOTE(JSON_EXTRACT(
         step_row.`step_document`, '$.serviceId'))
   AND operation_map.`operation_code` = JSON_UNQUOTE(JSON_EXTRACT(
         step_row.`step_document`, '$.operationCode'))
), `event_step_migration` AS (
  SELECT
    `id`,
    `step_ordinal`,
    CASE
      WHEN `extension_id` IS NULL THEN `step_document`
      ELSE JSON_REMOVE(
        JSON_SET(`step_document`, '$.extensionId', `extension_id`),
        '$.serviceId', '$.operationCode', '$.sourceCode',
        '$.serviceName', '$.serviceRevision', '$.operationName'
      )
    END AS `step_document`,
    IF(`extension_id` IS NULL, 0, 1) AS `was_migrated`
  FROM `event_step_rows`
), `event_documents` AS (
  SELECT
    `id`,
    JSON_ARRAYAGG(`step_document`) OVER (
      PARTITION BY `id`
      ORDER BY `step_ordinal`
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS `steps_document`,
    MAX(`was_migrated`) OVER (
      PARTITION BY `id`
    ) AS `was_migrated`,
    ROW_NUMBER() OVER (
      PARTITION BY `id` ORDER BY `step_ordinal` DESC
    ) AS `last_step`
  FROM `event_step_migration`
)
UPDATE `ui_event_binding` binding
JOIN `event_documents` migrated
  ON migrated.`id` = binding.`id`
 AND migrated.`last_step` = 1
 AND migrated.`was_migrated` = 1
SET binding.`steps_document` = migrated.`steps_document`,
    binding.`revision` = binding.`revision` + 1,
    binding.`update_time` = CURRENT_TIMESTAMP;

-- 旧接口权限按能力等价迁给扩展管理，再删除旧授权与菜单。角色不会因升级
-- 丢失查看、维护或测试能力；超级管理员和自定义角色遵循同一规则。
INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`)
SELECT
  MD5(CONCAT(role_grant.`role_id`, ':extension_list_permission_001')),
  role_grant.`role_id`,
  'extension_list_permission_001',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` role_grant
JOIN `sys_menu` extension_permission
  ON extension_permission.`id` = 'extension_list_permission_001'
WHERE role_grant.`menu_id` IN (
  'interface_service_menu_001',
  'interface_service_list_001'
);

INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`)
SELECT
  MD5(CONCAT(role_grant.`role_id`, ':extension_update_permission_001')),
  role_grant.`role_id`,
  'extension_update_permission_001',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` role_grant
JOIN `sys_menu` extension_permission
  ON extension_permission.`id` = 'extension_update_permission_001'
WHERE role_grant.`menu_id` = 'interface_service_update_001';

INSERT IGNORE INTO `sys_role_menu` (`id`, `role_id`, `menu_id`, `create_time`)
SELECT
  MD5(CONCAT(role_grant.`role_id`, ':extension_test_permission_001')),
  role_grant.`role_id`,
  'extension_test_permission_001',
  CURRENT_TIMESTAMP
FROM `sys_role_menu` role_grant
JOIN `sys_menu` extension_permission
  ON extension_permission.`id` = 'extension_test_permission_001'
WHERE role_grant.`menu_id` = 'interface_service_test_001';

DELETE role_grant
FROM `sys_role_menu` role_grant
LEFT JOIN `sys_menu` menu ON menu.`id` = role_grant.`menu_id`
LEFT JOIN `sys_menu` parent ON parent.`id` = menu.`parent_id`
WHERE role_grant.`menu_id` IN (
        'interface_service_menu_001',
        'interface_service_list_001',
        'interface_service_update_001',
        'interface_service_test_001',
        'user_manual_interface_service_001'
      )
   OR menu.`perm` IN (
        'system:interface-service:list',
        'system:interface-service:update',
        'system:interface-service:test',
        'user-manual:interface-service:view'
      )
   OR menu.`path` IN (
        '/system/interface-services',
        '/manual/interface-service'
      )
   OR menu.`component` IN (
        'system/InterfaceServices',
        'manual/InterfaceServiceManual'
      )
   OR parent.`id` = 'interface_service_menu_001'
   OR parent.`perm` = 'system:interface-service:list'
   OR parent.`path` = '/system/interface-services'
   OR parent.`component` = 'system/InterfaceServices';

DELETE menu
FROM `sys_menu` menu
LEFT JOIN `sys_menu` parent ON parent.`id` = menu.`parent_id`
WHERE menu.`id` IN (
        'interface_service_menu_001',
        'interface_service_list_001',
        'interface_service_update_001',
        'interface_service_test_001',
        'user_manual_interface_service_001'
      )
   OR menu.`perm` IN (
        'system:interface-service:list',
        'system:interface-service:update',
        'system:interface-service:test',
        'user-manual:interface-service:view'
      )
   OR menu.`path` IN (
        '/system/interface-services',
        '/manual/interface-service'
      )
   OR menu.`component` IN (
        'system/InterfaceServices',
        'manual/InterfaceServiceManual'
      )
   OR parent.`id` = 'interface_service_menu_001'
   OR parent.`perm` = 'system:interface-service:list'
   OR parent.`path` = '/system/interface-services'
   OR parent.`component` = 'system/InterfaceServices';

-- V088 只完成 expand/backfill，辅助表在成功前统一清理。旧表和旧列
-- 保留给 V089 contract，避免任一 DDL 断点让本版迁移失去重算来源。
DROP TABLE `flow_v088_migrated_data_bindings`;
DROP TABLE `flow_v088_interface_target_guard`;
DROP TABLE `flow_v088_interface_extension_stage`;
DROP TABLE `flow_v088_interface_migration_guard`;
DROP TABLE `flow_v088_interface_operation_map`;
DROP TABLE `flow_v088_interface_schema_guard`;

SET @flow_v088_interface_column_count = NULL;
SET @flow_v088_add_interface_columns_sql = NULL;
SET @flow_v088_add_list_field_extension_sql = NULL;
SET @flow_v088_add_list_query_extension_sql = NULL;
