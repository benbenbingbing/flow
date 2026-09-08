-- 将动作定义的实体可见范围迁移到唯一的数据源：关联表。
-- JSON 非数组或格式无效时，旧代码同样会按空集合处理；某定义只要已有任一关联行，
-- 就整体以关系表为准，不能把可能已经过期的 JSON 值合并回来扩大可见范围。
INSERT INTO `process_action_definition_entity` (
  `id`,
  `action_definition_id`,
  `entity_code`,
  `create_time`
)
SELECT
  REPLACE(UUID(), '-', ''),
  legacy_scope.`action_definition_id`,
  legacy_scope.`entity_code`,
  CURRENT_TIMESTAMP
FROM (
  SELECT DISTINCT
    definition.`id` AS `action_definition_id`,
    LOWER(TRIM(json_scope.`entity_code`)) COLLATE utf8mb4_unicode_ci
      AS `entity_code`
  FROM `process_action_definition` definition
  JOIN JSON_TABLE(
    IF(
      JSON_VALID(definition.`entity_codes_json`)
        AND LEFT(TRIM(definition.`entity_codes_json`), 1) = '[',
      definition.`entity_codes_json`,
      JSON_ARRAY()
    ),
    '$[*]' COLUMNS (
      `entity_code` varchar(100) PATH '$' NULL ON EMPTY NULL ON ERROR
    )
  ) json_scope
  WHERE NULLIF(TRIM(json_scope.`entity_code`), '') IS NOT NULL
) legacy_scope
WHERE NOT EXISTS (
  SELECT 1
  FROM `process_action_definition_entity` current_scope
  WHERE current_scope.`action_definition_id` = legacy_scope.`action_definition_id`
);

-- scope_type + element_id 已是运行时的规范定位方式；仅为空时才从旧字段补齐，
-- 避免覆盖新字段中已经保存的 NODE/SEQUENCE_FLOW 绑定。
UPDATE `process_action`
SET `scope_type` = CASE
  WHEN `sequence_flow_id` = '__PROCESS__' THEN 'PROCESS'
  ELSE 'SEQUENCE_FLOW'
END
WHERE `scope_type` IS NULL OR TRIM(`scope_type`) = '';

UPDATE `process_action`
SET `element_id` = NULLIF(TRIM(`sequence_flow_id`), '')
WHERE `scope_type` IN ('NODE', 'SEQUENCE_FLOW')
  AND (`element_id` IS NULL OR TRIM(`element_id`) = '')
  AND `sequence_flow_id` <> '__PROCESS__';

UPDATE `process_action`
SET `element_id` = NULL
WHERE `scope_type` = 'PROCESS';

ALTER TABLE `process_action`
  DROP INDEX `idx_sequence_flow`,
  DROP COLUMN `method_name`,
  DROP COLUMN `sequence_flow_id`;

ALTER TABLE `process_action_definition`
  DROP COLUMN `entity_codes_json`;
