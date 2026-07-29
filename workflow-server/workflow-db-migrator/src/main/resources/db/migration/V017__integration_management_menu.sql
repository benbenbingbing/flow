INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, keep_alive, breadcrumb, deleted,
  create_time, update_time
) VALUES (
  'integration_management_menu_001', '0', '开放集成', 'C', 'Connection', 75,
  '/system/open-integration', 'system/OpenIntegration',
  'system:integration:view', '0', '0', '0', '0', '0', '1', 0,
  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

UPDATE sys_menu
SET parent_id = 'integration_management_menu_001',
    menu_name = CASE id
      WHEN 'integration_perm_view' THEN '查看开放集成'
      WHEN 'integration_perm_manage' THEN '维护接入应用'
      WHEN 'integration_perm_secret_rotate' THEN '轮换集成密钥'
      WHEN 'integration_perm_delivery_replay' THEN '重放 Webhook 投递'
    END,
    sort = CASE id
      WHEN 'integration_perm_view' THEN 1
      WHEN 'integration_perm_manage' THEN 2
      WHEN 'integration_perm_secret_rotate' THEN 3
      WHEN 'integration_perm_delivery_replay' THEN 4
    END,
    update_time = CURRENT_TIMESTAMP
WHERE id IN (
  'integration_perm_view',
  'integration_perm_manage',
  'integration_perm_secret_rotate',
  'integration_perm_delivery_replay'
);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
VALUES (
  MD5('1:integration_management_menu_001'),
  '1',
  'integration_management_menu_001',
  CURRENT_TIMESTAMP
);
