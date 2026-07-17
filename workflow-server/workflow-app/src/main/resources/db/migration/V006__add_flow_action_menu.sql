-- 新增菜单：系统管理 > 定制开发 > 流程设置 > 流程动作

-- 1. 查找并补全菜单层级
SET @system_manage_id = (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'M' AND deleted = 0 LIMIT 1);
SET @custom_dev_id = (SELECT id FROM sys_menu WHERE menu_name = '定制开发' AND menu_type = 'M' AND deleted = 0 AND parent_id = @system_manage_id LIMIT 1);

-- 2. 若“定制开发”目录不存在，则创建（挂在系统管理下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, is_frame, is_cache, create_time, update_time, deleted)
SELECT 'custom_dev_menu_001', @system_manage_id, '定制开发', 'M', 'Document', 999, '/system', NULL, NULL, '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @custom_dev_id IS NULL AND @system_manage_id IS NOT NULL;

SET @custom_dev_id = COALESCE(@custom_dev_id, 'custom_dev_menu_001');

-- 3. 创建“流程设置”目录（挂在定制开发下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, is_frame, is_cache, create_time, update_time, deleted)
SELECT 'flow_setting_menu_001', @custom_dev_id, '流程设置', 'M', 'Connection', 1, '/system/flow-setting', NULL, NULL, '0', '0', '0', '0', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'flow_setting_menu_001');

SET @flow_setting_id = 'flow_setting_menu_001';

-- 4. 创建“流程动作”菜单（挂在流程设置下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, is_frame, is_cache, create_time, update_time, deleted)
SELECT 'flow_action_guide_menu_001', @flow_setting_id, '流程动作', 'C', 'Notebook', 1, '/system/flow-action-guide', 'system/FlowActionGuide', 'system:flowAction:view', '0', '0', '0', '0', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'flow_action_guide_menu_001');
