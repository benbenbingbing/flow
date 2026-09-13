-- 一次性升级未发布草稿中的列表按钮条件；不修改不可变的 ui_config_release 发布快照。
-- v1 的 HIDE/DISABLE 分别映射到 v2 的 visibleWhen/enabledWhen；没有实际规则的旧对象直接清空。
-- 迁移只使用普通 DML，避免要求结构账号具备 CREATE ROUTINE 或 ALTER ROUTINE 权限。

WITH button_sources AS (
    SELECT c.`id`,
           'TOOLBAR' AS `position`,
           button_rows.`ordinality`,
           button_rows.`button`
      FROM `entity_list_config` c
      JOIN JSON_TABLE(
          CASE
              WHEN c.`toolbar_config` IS NULL
                OR JSON_VALID(c.`toolbar_config`) = 0
                OR JSON_TYPE(CAST(c.`toolbar_config` AS JSON)) <> 'ARRAY'
                  THEN JSON_ARRAY()
              ELSE CAST(c.`toolbar_config` AS JSON)
          END,
          '$[*]' COLUMNS (
              `ordinality` FOR ORDINALITY,
              `button` JSON PATH '$'
          )) button_rows
    UNION ALL
    SELECT c.`id`,
           'ROW' AS `position`,
           button_rows.`ordinality`,
           button_rows.`button`
      FROM `entity_list_config` c
      JOIN JSON_TABLE(
          CASE
              WHEN c.`row_action_config` IS NULL
                OR JSON_VALID(c.`row_action_config`) = 0
                OR JSON_TYPE(CAST(c.`row_action_config` AS JSON)) <> 'ARRAY'
                  THEN JSON_ARRAY()
              ELSE CAST(c.`row_action_config` AS JSON)
          END,
          '$[*]' COLUMNS (
              `ordinality` FOR ORDINALITY,
              `button` JSON PATH '$'
          )) button_rows
), migrated_buttons AS (
    SELECT `id`,
           `position`,
           `ordinality`,
           CASE
               WHEN JSON_CONTAINS_PATH(
                       `button`, 'one', '$.availabilityRule') = 1
                AND (JSON_UNQUOTE(JSON_EXTRACT(
                       `button`, '$.availabilityRule.version')) IS NULL
                  OR JSON_UNQUOTE(JSON_EXTRACT(
                       `button`, '$.availabilityRule.version')) = '1')
                   THEN 1
               ELSE 0
           END AS `needs_migration`,
           CASE
               WHEN JSON_CONTAINS_PATH(
                       `button`, 'one', '$.availabilityRule') = 0
                   THEN `button`
               WHEN JSON_UNQUOTE(JSON_EXTRACT(
                       `button`, '$.availabilityRule.version')) IS NOT NULL
                AND JSON_UNQUOTE(JSON_EXTRACT(
                       `button`, '$.availabilityRule.version')) <> '1'
                   THEN `button`
               WHEN JSON_EXTRACT(
                       `button`, '$.availabilityRule.root') IS NULL
                OR JSON_TYPE(JSON_EXTRACT(
                       `button`, '$.availabilityRule.root')) = 'NULL'
                   THEN JSON_REMOVE(`button`, '$.availabilityRule')
               WHEN UPPER(COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                       `button`,
                       '$.availabilityRule.unavailableBehavior'))), ''),
                       'HIDE')) = 'DISABLE'
                   THEN JSON_SET(
                       `button`,
                       '$.availabilityRule',
                       JSON_OBJECT(
                           'version', 2,
                           'visibleWhen', NULL,
                           'enabledWhen', JSON_EXTRACT(
                               `button`, '$.availabilityRule.root'),
                           'disabledMessage', LEFT(COALESCE(NULLIF(TRIM(
                               JSON_UNQUOTE(JSON_EXTRACT(
                                   `button`,
                                   '$.availabilityRule.message'))), ''),
                               '当前条件不满足，按钮不可操作'), 300)))
               ELSE JSON_SET(
                   `button`,
                   '$.availabilityRule',
                   JSON_OBJECT(
                       'version', 2,
                       'visibleWhen', JSON_EXTRACT(
                           `button`, '$.availabilityRule.root'),
                       'enabledWhen', NULL,
                       'disabledMessage', ''))
           END AS `button`
      FROM button_sources
), aggregated_documents AS (
    SELECT `id`,
           `position`,
           JSON_ARRAYAGG(`button`) OVER (
               PARTITION BY `id`, `position`
               ORDER BY `ordinality`
               ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
           ) AS `migrated_document`,
           ROW_NUMBER() OVER (
               PARTITION BY `id`, `position`
               ORDER BY `ordinality` DESC
           ) AS `terminal_row`,
           MAX(`needs_migration`) OVER (
               PARTITION BY `id`, `position`
           ) AS `migrate_position`
      FROM migrated_buttons
), final_documents AS (
    SELECT `id`,
           MAX(CASE WHEN `position` = 'TOOLBAR'
               THEN CAST(`migrated_document` AS CHAR CHARACTER SET utf8mb4)
                    COLLATE utf8mb4_unicode_ci END) AS `toolbar_config`,
           MAX(CASE WHEN `position` = 'ROW'
               THEN CAST(`migrated_document` AS CHAR CHARACTER SET utf8mb4)
                    COLLATE utf8mb4_unicode_ci END) AS `row_action_config`
      FROM aggregated_documents
     WHERE `terminal_row` = 1
       AND `migrate_position` = 1
     GROUP BY `id`
)
UPDATE `entity_list_config` c
LEFT JOIN final_documents migrated ON migrated.`id` = c.`id`
SET c.`toolbar_config` = CASE
        WHEN migrated.`toolbar_config` IS NULL THEN c.`toolbar_config`
        ELSE migrated.`toolbar_config`
    END,
    c.`row_action_config` = CASE
        WHEN migrated.`row_action_config` IS NULL THEN c.`row_action_config`
        ELSE migrated.`row_action_config`
    END,
    c.`revision` = c.`revision` + 1,
    c.`draft_hash` = NULL,
    c.`update_time` = CURRENT_TIMESTAMP
WHERE (migrated.`toolbar_config` IS NOT NULL
       AND NOT (c.`toolbar_config` <=> migrated.`toolbar_config`))
   OR (migrated.`row_action_config` IS NOT NULL
       AND NOT (c.`row_action_config` <=> migrated.`row_action_config`))
   OR EXISTS (
       SELECT 1
         FROM `entity_list_action` a
        WHERE a.`list_config_id` = c.`id`
          AND (
              (a.`availability_rule_document` IS NOT NULL
               AND JSON_VALID(a.`availability_rule_document`) = 1
               AND IF(
                   JSON_VALID(a.`availability_rule_document`) = 1,
                   JSON_TYPE(CAST(
                       a.`availability_rule_document` AS JSON)),
                   NULL) = 'OBJECT'
               AND (JSON_EXTRACT(
                       a.`availability_rule_document`, '$.version') IS NULL
                    OR JSON_UNQUOTE(JSON_EXTRACT(
                       a.`availability_rule_document`, '$.version')) = '1'))
              OR a.`unavailable_behavior` IS NOT NULL
              OR (a.`action_params_document` IS NOT NULL
                  AND JSON_VALID(a.`action_params_document`) = 1
                  AND IF(
                      JSON_VALID(a.`action_params_document`) = 1,
                      JSON_TYPE(CAST(a.`action_params_document` AS JSON)),
                      NULL) = 'OBJECT'
                  AND JSON_CONTAINS_PATH(
                      a.`action_params_document`,
                      'one',
                      '$.availabilityRule') = 1)
          )
   );

-- 关系表是按钮条件的唯一事实来源；同时移除旧列和扩展参数中的重复副本。
UPDATE `entity_list_action` a
SET a.`availability_rule_document` = CASE
        WHEN a.`availability_rule_document` IS NULL
          OR JSON_VALID(a.`availability_rule_document`) = 0
            THEN a.`availability_rule_document`
        WHEN JSON_TYPE(CAST(
                a.`availability_rule_document` AS JSON)) <> 'OBJECT'
            THEN a.`availability_rule_document`
        WHEN JSON_UNQUOTE(JSON_EXTRACT(
                a.`availability_rule_document`, '$.version')) IS NOT NULL
         AND JSON_UNQUOTE(JSON_EXTRACT(
                a.`availability_rule_document`, '$.version')) <> '1'
            THEN a.`availability_rule_document`
        WHEN JSON_EXTRACT(
                a.`availability_rule_document`, '$.root') IS NULL
          OR JSON_TYPE(JSON_EXTRACT(
                a.`availability_rule_document`, '$.root')) = 'NULL'
            THEN NULL
        WHEN UPPER(COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(
                a.`availability_rule_document`,
                '$.unavailableBehavior'))), ''), 'HIDE')) = 'DISABLE'
            THEN JSON_OBJECT(
                'version', 2,
                'visibleWhen', NULL,
                'enabledWhen', JSON_EXTRACT(
                    a.`availability_rule_document`, '$.root'),
                'disabledMessage', LEFT(COALESCE(NULLIF(TRIM(
                    JSON_UNQUOTE(JSON_EXTRACT(
                        a.`availability_rule_document`, '$.message'))), ''),
                    '当前条件不满足，按钮不可操作'), 300))
        ELSE JSON_OBJECT(
            'version', 2,
            'visibleWhen', JSON_EXTRACT(
                a.`availability_rule_document`, '$.root'),
            'enabledWhen', NULL,
            'disabledMessage', '')
    END,
    a.`unavailable_behavior` = NULL,
    a.`action_params_document` = CASE
        WHEN a.`action_params_document` IS NOT NULL
         AND JSON_VALID(a.`action_params_document`) = 1
         AND IF(
             JSON_VALID(a.`action_params_document`) = 1,
             JSON_TYPE(CAST(a.`action_params_document` AS JSON)),
             NULL) = 'OBJECT'
         AND JSON_CONTAINS_PATH(
             a.`action_params_document`, 'one', '$.availabilityRule') = 1
          THEN JSON_REMOVE(
              CAST(a.`action_params_document` AS JSON),
              '$.availabilityRule')
        ELSE a.`action_params_document`
    END,
    a.`revision` = a.`revision` + 1,
    a.`update_time` = CURRENT_TIMESTAMP
WHERE (a.`availability_rule_document` IS NOT NULL
       AND JSON_VALID(a.`availability_rule_document`) = 1
       AND IF(
           JSON_VALID(a.`availability_rule_document`) = 1,
           JSON_TYPE(CAST(a.`availability_rule_document` AS JSON)),
           NULL) = 'OBJECT'
       AND (JSON_EXTRACT(
               a.`availability_rule_document`, '$.version') IS NULL
            OR JSON_UNQUOTE(JSON_EXTRACT(
               a.`availability_rule_document`, '$.version')) = '1'))
   OR a.`unavailable_behavior` IS NOT NULL
   OR (a.`action_params_document` IS NOT NULL
       AND JSON_VALID(a.`action_params_document`) = 1
       AND IF(
           JSON_VALID(a.`action_params_document`) = 1,
           JSON_TYPE(CAST(a.`action_params_document` AS JSON)),
           NULL) = 'OBJECT'
       AND JSON_CONTAINS_PATH(
           a.`action_params_document`, 'one', '$.availabilityRule') = 1);
