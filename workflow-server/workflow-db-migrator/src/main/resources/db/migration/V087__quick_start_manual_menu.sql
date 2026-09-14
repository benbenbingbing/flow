-- 快速开始排在用户手册首位，原有手册的顺序和角色授权保持不变。
INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'user_manual_quick_start_001', 'user_manual_dir_001', '快速开始', 'C',
  'Guide', 0, '/manual/quick-start', 'manual/QuickStartManual',
  'user-manual:quick-start:view', '0', '0', '0', '0', NULL, '0', '1',
  '从配置流程、实体表单和列表到绑定流程、配置菜单的入门步骤',
  0, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL, NULL
);

-- 沿用用户手册目录的可见范围，并兼容超级管理员未单独持有目录授权的环境。
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT
  MD5(CONCAT(role_record.id, ':user_manual_quick_start_001')),
  role_record.id,
  'user_manual_quick_start_001',
  CURRENT_TIMESTAMP
FROM sys_role role_record
WHERE role_record.deleted = 0
  AND (
    role_record.role_code = 'super_admin'
    OR EXISTS (
      SELECT 1 FROM sys_role_menu parent_grant
      WHERE parent_grant.role_id = role_record.id
        AND parent_grant.menu_id = 'user_manual_dir_001'
    )
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = role_record.id
      AND existing_grant.menu_id = 'user_manual_quick_start_001'
  );
