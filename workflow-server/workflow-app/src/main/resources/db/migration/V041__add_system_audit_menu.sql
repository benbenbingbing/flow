-- V040 仅创建了 F 类型权限节点，侧栏会过滤按钮资源。
-- 本迁移补充真正的系统日志菜单，并将既有查询、详情、导出权限归入该菜单。

SET @system_manage_id = (
    SELECT id
    FROM sys_menu
    WHERE menu_name = '系统管理'
      AND menu_type = 'M'
      AND deleted = 0
    ORDER BY create_time
    LIMIT 1
);

SET @system_audit_menu_id = (
    SELECT id
    FROM sys_menu
    WHERE id = 'system_audit_menu_001'
       OR path = '/system/audit-logs'
       OR (
            menu_name = '系统日志'
            AND menu_type = 'C'
            AND deleted = 0
       )
    ORDER BY CASE WHEN id = 'system_audit_menu_001' THEN 0 ELSE 1 END,
             deleted,
             create_time
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
    'system_audit_menu_001', COALESCE(@system_manage_id, '0'), '系统日志', 'C',
    'Document', 80, '/system/audit-logs', 'system/SystemAudit', NULL,
    '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @system_audit_menu_id IS NULL;

SET @system_audit_menu_id = COALESCE(@system_audit_menu_id, 'system_audit_menu_001');

UPDATE sys_menu
SET parent_id = COALESCE(@system_manage_id, '0'),
    menu_name = '系统日志',
    menu_type = 'C',
    icon = 'Document',
    sort = 80,
    path = '/system/audit-logs',
    component = 'system/SystemAudit',
    perm = NULL,
    status = '0',
    visible = '0',
    is_frame = '0',
    is_cache = '0',
    deleted = 0,
    update_time = NOW()
WHERE id COLLATE utf8mb4_unicode_ci
    = @system_audit_menu_id COLLATE utf8mb4_unicode_ci;

UPDATE sys_menu
SET parent_id = @system_audit_menu_id,
    menu_type = 'F',
    path = '',
    component = '',
    status = '0',
    visible = '0',
    update_time = NOW()
WHERE perm IN ('system:audit:list', 'system:audit:detail', 'system:audit:export')
  AND deleted = 0;

-- 保留已经被授予任一审计权限的自定义角色，并为默认管理员补齐菜单节点。
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, @system_audit_menu_id, NOW()
FROM sys_role role
WHERE role.deleted = 0
  AND (
      role.role_code IN ('super_admin', 'admin')
      OR EXISTS (
          SELECT 1
          FROM sys_role_menu role_menu
          JOIN sys_menu permission_menu
            ON permission_menu.id COLLATE utf8mb4_unicode_ci
             = role_menu.menu_id COLLATE utf8mb4_unicode_ci
          WHERE role_menu.role_id COLLATE utf8mb4_unicode_ci
              = role.id COLLATE utf8mb4_unicode_ci
            AND permission_menu.perm IN (
                'system:audit:list',
                'system:audit:detail',
                'system:audit:export'
            )
            AND permission_menu.deleted = 0
      )
  );

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.id COLLATE utf8mb4_unicode_ci
       = @system_audit_menu_id COLLATE utf8mb4_unicode_ci
  OR menu.perm IN ('system:audit:list', 'system:audit:detail', 'system:audit:export')
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0
  AND menu.deleted = 0;
