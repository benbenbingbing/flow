-- 添加引用实体类型字段（用于REFERENCE/MULTI_REFERENCE字段）
ALTER TABLE `entity_field`
    ADD COLUMN IF NOT EXISTS `ref_entity_type` VARCHAR(20) NULL COMMENT '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）' AFTER `ref_entity_id`;
