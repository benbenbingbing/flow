-- 动态表已内置主键和审计列，本迁移只补齐设计元数据，不改变业务表或发布快照。
-- 字段清单使用内联派生表，适配不具备 CREATE TEMPORARY TABLES 权限的迁移账号。
-- 已存在同名定义时保留字段 ID 和显示名称，归为只读系统字段，避免重复插入或断开配置引用。
UPDATE entity_field f
JOIN entity_definition e ON e.id = f.entity_id AND e.storage_mode = 'DYNAMIC'
JOIN (
    SELECT _utf8mb4'id' COLLATE utf8mb4_unicode_ci AS field_code, 'ID' AS field_name,
           'STRING' AS field_type, 'varchar(64)' AS db_type, 64 AS field_length,
           'id' AS db_column_name, 0 AS sort_order
    UNION ALL SELECT 'createdAt', '创建时间', 'DATETIME', 'datetime', NULL, 'create_time', 1
    UNION ALL SELECT 'updatedAt', '更新时间', 'DATETIME', 'datetime', NULL, 'update_time', 2
    UNION ALL SELECT 'createdBy', '创建人', 'STRING', 'varchar(64)', 64, 'create_by', 3
    UNION ALL SELECT 'updatedBy', '更新人', 'STRING', 'varchar(64)', 64, 'update_by', 4
    UNION ALL SELECT 'deleted', '删除标记', 'BOOLEAN', 'tinyint', NULL, 'deleted', 5
) s ON s.field_code = f.field_code
SET f.field_type = s.field_type,
    f.db_type = s.db_type,
    f.field_length = s.field_length,
    f.db_column_name = s.db_column_name,
    f.is_system = 1,
    f.editable = 0,
    f.is_required = 0,
    f.is_unique = 0,
    f.default_value = NULL,
    f.validate_rules = NULL,
    f.value_storage = 'SCALAR',
    f.deleted = 0;

-- 主键排在系统字段首位，审计字段追加在已有定义后；未发布实体也需要完整的设计清单。
INSERT INTO entity_field (
    entity_id, field_code, field_name, field_type, db_type, field_length,
    db_column_name, is_required, is_unique, sort_order, is_system, is_published,
    editable, value_storage, deleted
)
SELECT e.id, s.field_code, s.field_name, s.field_type, s.db_type, s.field_length,
       s.db_column_name, 0, 0,
       CASE WHEN s.field_code = 'id' THEN 0 ELSE COALESCE(existing.max_order, 0) + s.sort_order END,
       1, CASE WHEN e.status = 'PUBLISHED' THEN 1 ELSE 0 END, 0, 'SCALAR', 0
FROM entity_definition e
CROSS JOIN (
    SELECT _utf8mb4'id' COLLATE utf8mb4_unicode_ci AS field_code, 'ID' AS field_name,
           'STRING' AS field_type, 'varchar(64)' AS db_type, 64 AS field_length,
           'id' AS db_column_name, 0 AS sort_order
    UNION ALL SELECT 'createdAt', '创建时间', 'DATETIME', 'datetime', NULL, 'create_time', 1
    UNION ALL SELECT 'updatedAt', '更新时间', 'DATETIME', 'datetime', NULL, 'update_time', 2
    UNION ALL SELECT 'createdBy', '创建人', 'STRING', 'varchar(64)', 64, 'create_by', 3
    UNION ALL SELECT 'updatedBy', '更新人', 'STRING', 'varchar(64)', 64, 'update_by', 4
    UNION ALL SELECT 'deleted', '删除标记', 'BOOLEAN', 'tinyint', NULL, 'deleted', 5
) s
LEFT JOIN entity_field f ON f.entity_id = e.id AND f.field_code = s.field_code
LEFT JOIN (SELECT entity_id, MAX(sort_order) AS max_order FROM entity_field GROUP BY entity_id) existing
       ON existing.entity_id = e.id
WHERE e.storage_mode = 'DYNAMIC' AND f.id IS NULL;
