-- 统一系统实体目录中文名称，并修复历史表注释中的 UTF-8/latin1 乱码。

UPDATE entity_definition
SET entity_name = CASE entity_code
        WHEN 'sys_user' THEN '系统用户'
        WHEN 'sys_role' THEN '系统角色'
        WHEN 'sys_organization' THEN '组织部门'
        WHEN 'sys_group' THEN '用户组'
        WHEN 'sys_menu' THEN '菜单权限'
        WHEN 'sys_dict' THEN '字典类型'
        WHEN 'sys_dict_item' THEN '字典明细'
        WHEN 'sys_user_role' THEN '用户角色关系'
        WHEN 'sys_role_menu' THEN '角色菜单关系'
        WHEN 'sys_user_group' THEN '用户组成员关系'
        ELSE entity_name
    END,
    description = CONCAT('平台系统表目录：', entity_code)
WHERE storage_mode = 'SYSTEM';

UPDATE entity_field field
JOIN entity_definition definition
  ON definition.id = field.entity_id
 AND definition.storage_mode = 'SYSTEM'
JOIN information_schema.COLUMNS columns
  ON columns.TABLE_SCHEMA = DATABASE()
 AND columns.TABLE_NAME = definition.table_name
 AND columns.COLUMN_NAME = field.field_code
SET field.field_name = CASE
        WHEN columns.COLUMN_COMMENT IS NULL OR columns.COLUMN_COMMENT = ''
            THEN columns.COLUMN_NAME
        WHEN columns.COLUMN_COMMENT REGEXP '[çèæåéäïð]'
            THEN CONVERT(CAST(CONVERT(columns.COLUMN_COMMENT USING latin1) AS BINARY) USING utf8mb4)
        ELSE columns.COLUMN_COMMENT
    END;
