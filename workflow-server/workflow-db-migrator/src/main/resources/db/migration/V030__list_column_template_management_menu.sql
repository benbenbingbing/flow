INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'list_column_template_menu_001',
  '0',
  '列表列模板',
  'C',
  'Grid',
  73,
  '/system/list-column-templates',
  'system/ListColumnTemplateManagement',
  'system:list-column-template:view',
  '0',
  '0',
  '0',
  '0',
  NULL,
  '0',
  '1',
  '可视化维护列表列模板及其不可变版本',
  0,
  NULL,
  CURRENT_TIMESTAMP,
  NULL,
  CURRENT_TIMESTAMP,
  NULL,
  NULL,
  NULL
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'list_column_template_manage_001',
  'list_column_template_menu_001',
  '维护列表列模板',
  'F',
  NULL,
  1,
  '',
  '',
  'system:list-column-template:manage',
  '0',
  '0',
  '0',
  '0',
  NULL,
  '0',
  '1',
  '新增、编辑和复制列表列模板',
  0,
  NULL,
  CURRENT_TIMESTAMP,
  NULL,
  CURRENT_TIMESTAMP,
  NULL,
  NULL,
  NULL
);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT
  MD5(CONCAT(role_id, ':list_column_template_menu_001')),
  role_id,
  'list_column_template_menu_001',
  CURRENT_TIMESTAMP
FROM sys_role_menu source_grant
WHERE source_grant.menu_id = 'extension_management_menu_001'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = source_grant.role_id
      AND existing_grant.menu_id = 'list_column_template_menu_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT
  MD5(CONCAT(role_id, ':list_column_template_manage_001')),
  role_id,
  'list_column_template_manage_001',
  CURRENT_TIMESTAMP
FROM sys_role_menu source_grant
WHERE source_grant.menu_id = 'extension_update_permission_001'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = source_grant.role_id
      AND existing_grant.menu_id = 'list_column_template_manage_001'
  );
