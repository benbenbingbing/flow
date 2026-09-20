-- 列表查询仅在 LIST_LOAD 中配置。只迁移可编辑草稿，不改写任何历史发布快照及其哈希。
-- 原有效事件链已有 REPLACE 时，旧查询槽位本就不执行；保留该事件链并清理无效槽位。
-- 历史接口保留 legacyListQuery 标记，调用仍使用 LIST_QUERY，避免旧 Provider 的 usage 分支改变返回格式。
-- 使用迁移期辅助表，与现有迁移一致，不要求 CREATE TEMPORARY TABLES 权限。
-- 中途失败后重新执行时从真实草稿重新计算；已追加的替代步骤不会重复迁移。
DROP TABLE IF EXISTS `v094_list_query_event_stage`;
CREATE TABLE `v094_list_query_event_stage` AS
SELECT c.`id` AS `list_id`, b.`id` AS `binding_id`,
       JSON_OBJECT('stepCode', 'migrated_list_query', 'name', '列表查询接口（已迁移）',
           'strategy', 'REPLACE', 'extensionId', c.`query_interface_extension_id`,
           'legacyListQuery', CAST('true' AS JSON), 'failurePolicy', 'STOP',
           'order', 2147483647) AS `query_step`,
       CASE WHEN
           (COALESCE(b.`enabled`, 0) = 1 AND b.`inheritance_mode` <> 'DISABLE'
             AND EXISTS (SELECT 1 FROM JSON_TABLE(COALESCE(b.`steps_document`, '[]'), '$[*]'
                 COLUMNS (`strategy` VARCHAR(20) PATH '$.strategy')) s WHERE s.`strategy` = 'REPLACE'))
           OR
           ((b.`id` IS NULL OR b.`enabled` = 0 OR b.`inheritance_mode` = 'INHERIT')
             AND EXISTS (SELECT 1 FROM `ui_event_binding` e
                 JOIN JSON_TABLE(COALESCE(e.`steps_document`, '[]'), '$[*]'
                     COLUMNS (`strategy` VARCHAR(20) PATH '$.strategy')) s
                 WHERE e.`owner_type` = 'ENTITY' AND e.`owner_id` = c.`entity_id`
                   AND e.`target_type` = 'OWNER' AND e.`target_key` = '' AND e.`event_code` = 'LIST_LOAD'
                   AND e.`enabled` = 1 AND e.`deleted` = 0 AND e.`inheritance_mode` <> 'DISABLE'
                   AND s.`strategy` = 'REPLACE'))
           THEN 0 ELSE 1 END AS `needs_step`
FROM `entity_list_config` c
LEFT JOIN `ui_event_binding` b
    ON b.`owner_type` = 'LIST' AND b.`owner_id` = c.`id`
   AND b.`target_type` = 'OWNER' AND b.`target_key` = '' AND b.`event_code` = 'LIST_LOAD' AND b.`deleted` = 0
WHERE c.`deleted` = 0 AND NULLIF(TRIM(c.`query_interface_extension_id`), '') IS NOT NULL;

-- 已启用链保留原前置/后置步骤；原 DISABLE 需要替换继承链后只执行旧查询。
UPDATE `ui_event_binding` b
JOIN `v094_list_query_event_stage` m ON m.`binding_id` = b.`id` AND m.`needs_step` = 1
SET b.`steps_document` = CASE WHEN b.`enabled` = 1 AND b.`inheritance_mode` <> 'DISABLE'
        THEN JSON_ARRAY_APPEND(COALESCE(b.`steps_document`, '[]'), '$', CAST(m.`query_step` AS JSON))
        ELSE JSON_ARRAY(CAST(m.`query_step` AS JSON)) END,
    b.`inheritance_mode` = CASE WHEN b.`enabled` = 0 THEN 'INHERIT'
        WHEN b.`inheritance_mode` = 'DISABLE' THEN 'REPLACE' ELSE b.`inheritance_mode` END,
    b.`enabled` = 1, b.`revision` = b.`revision` + 1, b.`update_time` = CURRENT_TIMESTAMP;

INSERT INTO `ui_event_binding`
    (`id`, `owner_type`, `owner_id`, `target_type`, `target_key`, `event_code`,
     `inheritance_mode`, `steps_document`, `revision`, `enabled`, `deleted`)
SELECT REPLACE(UUID(), '-', ''), 'LIST', m.`list_id`, 'OWNER', '', 'LIST_LOAD',
       'INHERIT', JSON_ARRAY(CAST(m.`query_step` AS JSON)), 1, 1, 0
FROM `v094_list_query_event_stage` m
WHERE m.`binding_id` IS NULL AND m.`needs_step` = 1;

UPDATE `entity_list_config` c
JOIN `v094_list_query_event_stage` m ON m.`list_id` = c.`id`
SET c.`query_interface_extension_id` = NULL, c.`revision` = c.`revision` + 1,
    c.`draft_hash` = NULL, c.`update_time` = CURRENT_TIMESTAMP;

DROP TABLE `v094_list_query_event_stage`;
