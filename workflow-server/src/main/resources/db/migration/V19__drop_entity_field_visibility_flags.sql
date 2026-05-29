-- 删除实体字段表中冗余的可见性/查询标志列
-- 列表显示、表单显示、查询条件已由独立的列表配置和表单配置管理
ALTER TABLE entity_field DROP COLUMN show_in_list;
ALTER TABLE entity_field DROP COLUMN show_in_form;
ALTER TABLE entity_field DROP COLUMN is_query;
