INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, sort, perm,
  status, visible, deleted, create_time, update_time
) VALUES (
  'security_perm_process_signal', '0', 'Trigger process receive tasks',
  'F', 0, 'process:instance:signal',
  '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT MD5(CONCAT('1:', id)), '1', id, CURRENT_TIMESTAMP
FROM sys_menu
WHERE id = 'security_perm_process_signal';
