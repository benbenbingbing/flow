-- 补充 entity_field 表缺失的字段
-- 这些字段在 EntityField.java 实体中已定义，但数据库中可能不存在

ALTER TABLE `entity_field`
    ADD COLUMN IF NOT EXISTS `db_column_name` VARCHAR(100) NULL COMMENT '数据库列名（下划线命名）',
    ADD COLUMN IF NOT EXISTS `field_precision` INT NULL COMMENT '小数位数（精度），用于 DECIMAL 类型',
    ADD COLUMN IF NOT EXISTS `editable` TINYINT(1) DEFAULT 1 COMMENT '是否可编辑（系统字段中，name和code可配置，其他固定不可编辑）',
    ADD COLUMN IF NOT EXISTS `is_published` TINYINT(1) DEFAULT 0 COMMENT '是否已发布（已同步到数据库表的字段）',
    ADD COLUMN IF NOT EXISTS `ref_entity_id` VARCHAR(64) NULL COMMENT '关联实体ID（用于子表单/实体选择）',
    ADD COLUMN IF NOT EXISTS `display_mode` VARCHAR(20) NULL COMMENT '显示方式：embedded-嵌入, tab-Tab页（用于子表单）',
    ADD COLUMN IF NOT EXISTS `ref_field_code` VARCHAR(100) NULL COMMENT '关联字段编码（用于子表单数据关联）';
