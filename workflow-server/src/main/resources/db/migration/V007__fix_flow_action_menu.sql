-- 修复流程设置菜单：应为目录（无 path），并确保流程动作子菜单正确挂载

-- 1. 确保“流程设置”目录存在（若 V006 未执行或已被删除）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, is_frame, is_cache, create_time, update_time, deleted)
SELECT 'flow_setting_menu_001',
       COALESCE(
           (SELECT id FROM sys_menu WHERE menu_name = '定制开发' AND menu_type = 'M' AND deleted = 0 LIMIT 1),
           '0'
       ),
       '流程设置',
       'M',
       'Connection',
       1,
       '',
       NULL,
       NULL,
       '0',
       '0',
       '0',
       '0',
       NOW(),
       NOW(),
       0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'flow_setting_menu_001');

-- 2. 将“流程设置”改为目录，清空 path，避免点击时跳转到不存在的路由
UPDATE sys_menu
SET menu_type   = 'M',
    path        = '',
    component   = '',
    perm        = NULL,
    update_time = NOW()
WHERE id = 'flow_setting_menu_001';

-- 3. 若“流程动作”已存在但挂错位置，修正其父菜单为流程设置
UPDATE sys_menu
SET parent_id   = 'flow_setting_menu_001',
    menu_type   = 'C',
    path        = '/system/flow-action-guide',
    component   = 'system/FlowActionGuide',
    perm        = 'system:flowAction:view',
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    update_time = NOW()
WHERE id = 'flow_action_guide_menu_001';

-- 4. 若“流程动作”不存在，则插入
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, is_frame, is_cache, create_time, update_time, deleted)
SELECT 'flow_action_guide_menu_001',
       'flow_setting_menu_001',
       '流程动作',
       'C',
       'Notebook',
       1,
       '/system/flow-action-guide',
       'system/FlowActionGuide',
       'system:flowAction:view',
       '0',
       '0',
       '0',
       '0',
       NOW(),
       NOW(),
       0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'flow_action_guide_menu_001');
