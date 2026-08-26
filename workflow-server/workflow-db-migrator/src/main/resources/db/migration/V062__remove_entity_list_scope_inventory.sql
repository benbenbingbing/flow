-- “数据范围盘点”是一次性存量治理页面，移除其专属菜单、授权和盘点数据。
-- EXPLICIT_ALL 仍是列表数据范围配置的通用高风险能力，因此保留权限码及既有角色授权，
-- 仅将其从即将删除的盘点菜单下移出，避免形成孤儿菜单节点。
UPDATE sys_menu
SET parent_id = '0',
    visible = '1',
    sort = 0,
    remark = '允许在列表数据范围配置中显式确认全量可见的高风险权限',
    update_time = CURRENT_TIMESTAMP
WHERE perm = 'entity:list-scope:explicit-all';

-- 先按功能特征记住全部盘点根菜单 ID。不能只依赖历史固定 ID：存量环境可能复制过菜单
-- 或重新生成过主键，直接删除根菜单会留下仍可被角色权限查询命中的孤儿子菜单授权。
CREATE TEMPORARY TABLE tmp_scope_inventory_menu_root (
  menu_id VARCHAR(64) NOT NULL PRIMARY KEY
);

INSERT INTO tmp_scope_inventory_menu_root (menu_id)
SELECT id
FROM sys_menu
WHERE (perm IS NULL OR perm <> 'entity:list-scope:explicit-all')
  AND (id = 'entity_scope_inventory_menu_001'
       OR perm = 'entity:list-scope:inventory'
       OR path = '/system/entity-scope-inventory'
       OR component = 'system/EntityListScopeInventory');

-- V052 的盘点权限项只有一层子菜单；显式全量权限已在上方移到根节点。先删根及其
-- 直接子项的角色授权，再删子菜单和根菜单，兼容根 ID 被复制或改写的环境。
DELETE role_grant
FROM sys_role_menu role_grant
JOIN sys_menu menu ON menu.id = role_grant.menu_id
LEFT JOIN tmp_scope_inventory_menu_root own_root ON own_root.menu_id = menu.id
LEFT JOIN tmp_scope_inventory_menu_root parent_root ON parent_root.menu_id = menu.parent_id
WHERE own_root.menu_id IS NOT NULL OR parent_root.menu_id IS NOT NULL;

DELETE child_menu
FROM sys_menu child_menu
JOIN tmp_scope_inventory_menu_root root_menu ON root_menu.menu_id = child_menu.parent_id;

DELETE root_menu
FROM sys_menu root_menu
JOIN tmp_scope_inventory_menu_root removed_root ON removed_root.menu_id = root_menu.id;

DROP TEMPORARY TABLE tmp_scope_inventory_menu_root;

DROP TABLE IF EXISTS entity_list_scope_inventory;
