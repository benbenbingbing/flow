-- 修复 entity_field 表可能缺失的字段
-- 附件相关字段
ALTER TABLE `entity_field`
    ADD COLUMN IF NOT EXISTS `file_types` VARCHAR(500) NULL COMMENT '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）',
    ADD COLUMN IF NOT EXISTS `file_max_size` INT NULL COMMENT '文件大小限制（MB，用于附件类型）',
    ADD COLUMN IF NOT EXISTS `file_max_count` INT NULL COMMENT '文件数量限制（用于附件类型）',
    ADD COLUMN IF NOT EXISTS `ref_entity_type` VARCHAR(20) NULL COMMENT '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）';
