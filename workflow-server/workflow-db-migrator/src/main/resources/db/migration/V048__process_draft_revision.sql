-- 流程草稿增加独立修订号与内容哈希，避免多人或多标签页静默覆盖整份 BPMN。
ALTER TABLE `process_definition_config`
    ADD COLUMN `draft_revision` BIGINT NOT NULL DEFAULT 1 COMMENT '流程草稿修订号' AFTER `bpmn_xml`,
    ADD COLUMN `published_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '最近发布对应的草稿修订号' AFTER `draft_revision`,
    ADD COLUMN `draft_hash` CHAR(64) DEFAULT NULL COMMENT '当前流程草稿 SHA-256' AFTER `published_revision`,
    ADD COLUMN `published_draft_hash` CHAR(64) DEFAULT NULL COMMENT '最近发布流程草稿 SHA-256' AFTER `draft_hash`,
    ADD COLUMN `base_published_version` INT NOT NULL DEFAULT 0 COMMENT '当前草稿基于的已发布版本' AFTER `published_draft_hash`;

-- 存量已发布流程以迁移时的草稿作为共同基线；内容哈希由应用首次保存或发布时补齐。
UPDATE `process_definition_config`
   SET `published_revision` = CASE WHEN COALESCE(`version`, 0) > 0 THEN `draft_revision` ELSE 0 END,
       `base_published_version` = GREATEST(COALESCE(`version`, 0), 0);
