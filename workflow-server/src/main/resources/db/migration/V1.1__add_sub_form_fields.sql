-- 添加子表单相关字段到 entity_field 表
ALTER TABLE `entity_field`
ADD COLUMN IF NOT EXISTS `ref_entity_id` VARCHAR(64) NULL COMMENT '关联实体ID（用于子表单）',
ADD COLUMN IF NOT EXISTS `display_mode` VARCHAR(20) NULL DEFAULT 'embedded' COMMENT '显示方式：embedded-嵌入, tab-Tab页（用于子表单）',
ADD COLUMN IF NOT EXISTS `ref_field_code` VARCHAR(100) NULL COMMENT '关联字段编码（用于子表单数据关联）';

-- 添加子表单字段类型到 entity_form_field 表（如果需要）
-- 注意：entity_form_field 表的字段类型通常是从 entity_field 同步过来的
