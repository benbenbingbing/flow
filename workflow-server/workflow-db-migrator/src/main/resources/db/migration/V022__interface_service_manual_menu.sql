INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'user_manual_interface_service_001',
  'user_manual_dir_001',
  '接口服务',
  'C',
  'Connection',
  4,
  '/manual/interface-service',
  'manual/InterfaceServiceManual',
  'user-manual:interface-service:view',
  '0',
  '0',
  '0',
  '0',
  NULL,
  '0',
  '1',
  '接口服务、服务操作、调试与事件绑定使用手册',
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
  MD5(CONCAT(role_id, ':user_manual_interface_service_001')),
  role_id,
  'user_manual_interface_service_001',
  CURRENT_TIMESTAMP
FROM sys_role_menu parent_grant
WHERE parent_grant.menu_id = 'user_manual_dir_001'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = parent_grant.role_id
      AND existing_grant.menu_id = 'user_manual_interface_service_001'
  );
