-- ========================================================
-- V20: 添加定制开发菜单
-- 说明: 在系统管理下增加「定制开发」目录及「列表字段扩展」子菜单
-- ========================================================

-- 添加「定制开发」目录（parent 为系统管理）
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted
)
SELECT 'dev_guide_dir', id, '定制开发', 'M', 'Document', 99, '/dev', NULL, NULL, '0', '0', 0
FROM sys_menu WHERE menu_name = '系统管理' AND deleted = 0 AND parent_id = '0';

-- 添加「列表字段扩展」子菜单（parent 为定制开发目录）
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted
)
SELECT 'dev_guide_list', id, '列表字段扩展', 'C', 'Document', 1, '/system/dev-guide', '/views/system/DevGuide.vue', 'system:dev:list', '0', '0', 0
FROM sys_menu WHERE menu_name = '定制开发' AND deleted = 0;
