-- 配置迁移列表权限需要作为 F 类型资源，运行时权限集合只加载按钮权限。

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, sort, path, component, perm,
  status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
  'config_migration_list_001', 'config_migration_menu_001', '查看配置迁移', 'F', 0,
  '', '', 'config-migration:list', '0', '0', '0', '0', NOW(), NOW(), 0
WHERE EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 'config_migration_menu_001' AND deleted = 0
)
AND NOT EXISTS (
  SELECT 1 FROM sys_menu
  WHERE id = 'config_migration_list_001'
     OR (parent_id = 'config_migration_menu_001' AND menu_type = 'F' AND perm = 'config-migration:list')
);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.parent_id = 'config_migration_menu_001'
 AND menu.menu_type = 'F'
 AND menu.perm IN (
   'config-migration:list',
   'config-migration:export',
   'config-migration:download',
   'config-migration:import',
   'config-migration:analyze',
   'config-migration:publish',
   'config-migration:rollback'
 )
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0
  AND menu.deleted = 0;
