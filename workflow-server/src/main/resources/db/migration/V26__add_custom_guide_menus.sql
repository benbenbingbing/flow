-- ========================================================
-- V26: 添加自定义组件开发指南菜单
-- 说明: 在「定制开发」目录下增加「自定义列表组件」和「自定义表单组件」子菜单
-- ========================================================

-- 添加「自定义列表组件」子菜单（parent 为定制开发目录）
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted
)
SELECT 'custom_list_guide', id, '自定义列表组件', 'C', 'Document', 2, '/system/custom-list-guide', '/views/system/CustomListGuide.vue', 'system:dev:list', '0', '0', 0
FROM sys_menu WHERE menu_name = '定制开发' AND deleted = 0;

-- 添加「自定义表单组件」子菜单（parent 为定制开发目录）
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted
)
SELECT 'custom_form_guide', id, '自定义表单组件', 'C', 'Document', 3, '/system/custom-form-guide', '/views/system/CustomFormGuide.vue', 'system:dev:list', '0', '0', 0
FROM sys_menu WHERE menu_name = '定制开发' AND deleted = 0;
