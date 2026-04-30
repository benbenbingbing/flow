-- 实体字段表增加数据库列名字段（下划线命名映射）
ALTER TABLE entity_field ADD COLUMN db_column_name VARCHAR(100) COMMENT '数据库列名（下划线命名）';
