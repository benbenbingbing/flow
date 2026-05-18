-- ============================================
-- 手动修复：添加 custom_component 字段 + 菜单
-- 在 MySQL 中直接执行此脚本（支持 MySQL 5.7+）
-- ============================================

-- 1. 列表配置表添加自定义组件字段
-- 如果已存在会报错 Duplicate column，可忽略
ALTER TABLE entity_list_config ADD COLUMN custom_component VARCHAR(100) NULL COMMENT '自定义列表组件注册名';

-- 2. 表单定义表添加自定义组件字段
ALTER TABLE entity_form ADD COLUMN custom_component VARCHAR(100) NULL COMMENT '自定义表单组件注册名';

-- 3. 添加「自定义列表组件」菜单
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted
)
SELECT 'custom_list_guide', id, '自定义列表组件', 'C', 'Document', 2, '/system/custom-list-guide', '/views/system/CustomListGuide.vue', 'system:dev:list', '0', '0', 0
FROM sys_menu WHERE menu_name = '定制开发' AND deleted = 0;

-- 4. 添加「自定义表单组件」菜单
INSERT IGNORE INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted
)
SELECT 'custom_form_guide', id, '自定义表单组件', 'C', 'Document', 3, '/system/custom-form-guide', '/views/system/CustomFormGuide.vue', 'system:dev:list', '0', '0', 0
FROM sys_menu WHERE menu_name = '定制开发' AND deleted = 0;
