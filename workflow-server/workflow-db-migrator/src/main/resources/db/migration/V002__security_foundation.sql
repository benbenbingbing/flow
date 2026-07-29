-- Disable the historical public admin/admin credential on both fresh installs
-- and upgrades. Customized administrator credentials are left untouched.
UPDATE sys_user
SET status = '1',
    update_time = CURRENT_TIMESTAMP
WHERE id = '1'
  AND username = 'admin'
  AND password = '$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6'
  AND password_reset_required = 1
  AND deleted = 0;

ALTER TABLE sys_user
  ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'Incremented to revoke all previously issued sessions';

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, sort, perm,
  status, visible, deleted, create_time, update_time
) VALUES
  ('security_perm_user_view', '0', 'View users', 'F', 0, 'system:user:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_user_manage', '0', 'Manage users', 'F', 0, 'system:user:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_user_reset', '0', 'Reset user password', 'F', 0, 'system:user:reset-password', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_role_view', '0', 'View roles', 'F', 0, 'system:role:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_role_manage', '0', 'Manage roles', 'F', 0, 'system:role:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_menu_view', '0', 'View menus', 'F', 0, 'system:menu:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_menu_manage', '0', 'Manage menus', 'F', 0, 'system:menu:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_org_view', '0', 'View organizations', 'F', 0, 'system:organization:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_org_manage', '0', 'Manage organizations', 'F', 0, 'system:organization:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_dict_view', '0', 'View dictionaries', 'F', 0, 'system:dictionary:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_dict_manage', '0', 'Manage dictionaries', 'F', 0, 'system:dictionary:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_process_view', '0', 'View process definitions', 'F', 0, 'process:definition:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_process_manage', '0', 'Manage process definitions', 'F', 0, 'process:definition:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_process_publish', '0', 'Publish process definitions', 'F', 0, 'process:definition:publish', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_entity_view', '0', 'View entity definitions', 'F', 0, 'entity:definition:view', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_entity_manage', '0', 'Manage entity definitions', 'F', 0, 'entity:definition:manage', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_entity_publish', '0', 'Publish entity definitions', 'F', 0, 'entity:definition:publish', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_file_read', '0', 'Read files', 'F', 0, 'storage:file:read', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_file_write', '0', 'Upload files', 'F', 0, 'storage:file:write', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('security_perm_file_delete', '0', 'Delete files', 'F', 0, 'storage:file:delete', '0', '1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT MD5(CONCAT('1:', id)), '1', id, CURRENT_TIMESTAMP
FROM sys_menu
WHERE id LIKE 'security_perm_%';
