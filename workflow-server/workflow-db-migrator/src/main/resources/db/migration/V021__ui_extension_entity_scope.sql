ALTER TABLE `ui_extension_definition`
  ADD COLUMN `visibility_scope` varchar(20)
    COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GLOBAL'
    COMMENT '适用范围：GLOBAL/ENTITY'
    AFTER `snapshot_version`,
  ADD COLUMN `entity_codes_document` longtext
    COLLATE utf8mb4_unicode_ci
    COMMENT '指定适用实体编码JSON数组'
    AFTER `visibility_scope`;

