INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'list_field_guide_v2_001',
  'dev_guide_dir',
  '列表字段扩展2',
  'C',
  'Notebook',
  2,
  '/system/list-field-guide',
  'system/ListFieldExtensionGuide',
  'system:dev:list',
  '0',
  '0',
  '0',
  '0',
  NULL,
  '0',
  '1',
  '列表字段扩展开发手册：单元格组件、虚拟列与 ListFieldDataProvider',
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
  MD5(CONCAT(role_id, ':list_field_guide_v2_001')),
  role_id,
  'list_field_guide_v2_001',
  CURRENT_TIMESTAMP
FROM sys_role_menu parent_grant
WHERE parent_grant.menu_id = 'dev_guide_dir'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = parent_grant.role_id
      AND existing_grant.menu_id = 'list_field_guide_v2_001'
  );

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT
  MD5(CONCAT('1:', 'list_field_guide_v2_001')),
  '1',
  'list_field_guide_v2_001',
  CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_role_menu existing_grant
  WHERE existing_grant.role_id = '1'
    AND existing_grant.menu_id = 'list_field_guide_v2_001'
);
