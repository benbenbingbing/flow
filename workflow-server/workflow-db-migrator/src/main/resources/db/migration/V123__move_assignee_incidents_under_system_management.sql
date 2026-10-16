-- 保留页面 ID、路由及按钮权限，仅将空办理人事件入口收拢到系统管理。
UPDATE sys_menu
SET parent_id = '400',
    sort = 16,
    update_by = 'migration-v123',
    update_time = CURRENT_TIMESTAMP
WHERE id = 'assignee_incident_menu_001'
  AND deleted = 0;

-- 已拥有页面权限的角色需要祖先目录授权，避免菜单移动后入口不可见。
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT DISTINCT
  MD5(CONCAT(source_grant.role_id, ':400')),
  source_grant.role_id,
  '400',
  CURRENT_TIMESTAMP
FROM sys_role_menu source_grant
JOIN sys_menu incident_menu
  ON incident_menu.id = source_grant.menu_id
 AND incident_menu.id = 'assignee_incident_menu_001'
 AND incident_menu.deleted = 0
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_role_menu existing_grant
  WHERE existing_grant.role_id = source_grant.role_id
    AND existing_grant.menu_id = '400'
);
