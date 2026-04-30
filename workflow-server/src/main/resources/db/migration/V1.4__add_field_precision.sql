-- 实体字段表增加小数位数（精度）字段，用于 DECIMAL 类型
ALTER TABLE entity_field ADD COLUMN field_precision INT COMMENT '小数位数（精度）';
