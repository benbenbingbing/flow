ALTER TABLE `entity_field`
  ADD COLUMN `ref_list_key` varchar(100)
    COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '子列表或实体引用默认使用的已发布列表编码'
    AFTER `ref_field_code`;

-- 旧的 SUB_FORM_LIST 实际承担的是一对多子表单语义。
-- 新模型中一对一和一对多统一归入 SUB_FORM，SUB_LIST 专用于只读列表嵌入。
UPDATE `entity_field`
SET `field_type` = 'SUB_FORM'
WHERE `field_type` = 'SUB_FORM_LIST';

UPDATE `entity_form_field`
SET `field_type` = 'SUB_FORM',
    `component_type` = 'sub_form'
WHERE `field_type` = 'SUB_FORM_LIST'
   OR `component_type` = 'sub_form_list';

UPDATE `entity_form_node`
SET `props_document` = REPLACE(
        REPLACE(`props_document`, 'SUB_FORM_LIST', 'SUB_FORM'),
        'sub_form_list',
        'sub_form')
WHERE `props_document` LIKE '%SUB_FORM_LIST%'
   OR `props_document` LIKE '%sub_form_list%';

-- 已发布表单快照仍需维持原有的一对多子表单行为。
UPDATE `ui_config_release`
SET `snapshot_document` = REPLACE(
        REPLACE(`snapshot_document`, 'SUB_FORM_LIST', 'SUB_FORM'),
        'sub_form_list',
        'sub_form'),
    `patch_document` = CASE
        WHEN `patch_document` IS NULL THEN NULL
        ELSE REPLACE(
            REPLACE(`patch_document`, 'SUB_FORM_LIST', 'SUB_FORM'),
            'sub_form_list',
            'sub_form')
        END,
    `content_hash` = LOWER(SHA2(
        REPLACE(
            REPLACE(`snapshot_document`, 'SUB_FORM_LIST', 'SUB_FORM'),
            'sub_form_list',
            'sub_form'),
        256))
WHERE `config_type` = 'FORM'
  AND (`snapshot_document` LIKE '%SUB_FORM_LIST%'
       OR `snapshot_document` LIKE '%sub_form_list%'
       OR `patch_document` LIKE '%SUB_FORM_LIST%'
       OR `patch_document` LIKE '%sub_form_list%');

UPDATE `ui_config_hotfix_target`
SET `effective_snapshot_document` = REPLACE(
        REPLACE(
            `effective_snapshot_document`,
            'SUB_FORM_LIST',
            'SUB_FORM'),
        'sub_form_list',
        'sub_form')
WHERE `config_type` = 'FORM'
  AND (`effective_snapshot_document` LIKE '%SUB_FORM_LIST%'
       OR `effective_snapshot_document` LIKE '%sub_form_list%');
