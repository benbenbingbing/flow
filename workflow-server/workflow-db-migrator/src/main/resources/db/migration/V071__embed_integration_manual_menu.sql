-- 在用户手册下增加 Embed V1 的独立接入说明，并保持原有手册的显示顺序。
UPDATE sys_menu
   SET sort = 5,
       update_time = CURRENT_TIMESTAMP
 WHERE id = 'user_manual_interface_service_001';

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, query, keep_alive, breadcrumb,
  remark, deleted, create_by, create_time, update_by, update_time,
  entity_code, resource_type, list_key
) VALUES (
  'user_manual_embed_integration_001',
  'user_manual_dir_001',
  '嵌入集成',
  'C',
  'Monitor',
  4,
  '/manual/embed-integration',
  'manual/EmbedIntegrationManual',
  'user-manual:embed-integration:view',
  '0',
  '0',
  '0',
  '0',
  NULL,
  '0',
  '1',
  'Embed View、应用授权、外部用户映射、Launch 与宿主 SDK 使用手册',
  0,
  NULL,
  CURRENT_TIMESTAMP,
  NULL,
  CURRENT_TIMESTAMP,
  NULL,
  NULL,
  NULL
);

-- 手册页面继承“用户手册”目录的既有角色授权，避免只对超级管理员可见。
INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT
  MD5(CONCAT(role_id, ':user_manual_embed_integration_001')),
  role_id,
  'user_manual_embed_integration_001',
  CURRENT_TIMESTAMP
FROM sys_role_menu parent_grant
WHERE parent_grant.menu_id = 'user_manual_dir_001'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing_grant
    WHERE existing_grant.role_id = parent_grant.role_id
      AND existing_grant.menu_id = 'user_manual_embed_integration_001'
  );
