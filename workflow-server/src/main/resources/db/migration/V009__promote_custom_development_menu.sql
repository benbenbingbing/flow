-- 将“定制开发”提升为与“系统管理”平级的顶级目录，并统一“流程配置”菜单名称

SET @system_parent_id = COALESCE(
    (SELECT parent_id
     FROM sys_menu
     WHERE menu_name = '系统管理'
       AND menu_type = 'M'
       AND deleted = 0
     LIMIT 1),
    '0'
);

SET @system_sort = COALESCE(
    (SELECT sort
     FROM sys_menu
     WHERE menu_name = '系统管理'
       AND menu_type = 'M'
       AND deleted = 0
     LIMIT 1),
    4
);

SET @custom_dev_id = (
    SELECT id
    FROM sys_menu
    WHERE menu_name = '定制开发'
      AND menu_type = 'M'
      AND deleted = 0
    ORDER BY
        CASE
            WHEN id = 'dev_guide_dir' THEN 0
            WHEN id = 'custom_dev_menu_001' THEN 1
            ELSE 2
        END,
        create_time
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
    'dev_guide_dir', @system_parent_id, '定制开发', 'M', 'Document',
    @system_sort + 1, '/dev', NULL, NULL,
    '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @custom_dev_id IS NULL;

SET @custom_dev_id = COALESCE(@custom_dev_id, 'dev_guide_dir');

UPDATE sys_menu
SET parent_id   = @system_parent_id,
    menu_name   = '定制开发',
    menu_type   = 'M',
    icon        = 'Document',
    sort        = @system_sort + 1,
    path        = '/dev',
    component   = NULL,
    perm        = NULL,
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    update_time = NOW()
WHERE id = @custom_dev_id;

UPDATE sys_menu child
JOIN sys_menu duplicate
  ON child.parent_id = duplicate.id
SET child.parent_id = @custom_dev_id,
    child.update_time = NOW()
WHERE duplicate.menu_name = '定制开发'
  AND duplicate.menu_type = 'M'
  AND duplicate.deleted = 0
  AND duplicate.id <> @custom_dev_id;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role_menu.role_id, @custom_dev_id, NOW()
FROM sys_role_menu role_menu
JOIN sys_menu duplicate
  ON role_menu.menu_id = duplicate.id
WHERE duplicate.menu_name = '定制开发'
  AND duplicate.menu_type = 'M'
  AND duplicate.deleted = 0
  AND duplicate.id <> @custom_dev_id;

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu duplicate
  ON role_menu.menu_id = duplicate.id
WHERE duplicate.menu_name = '定制开发'
  AND duplicate.menu_type = 'M'
  AND duplicate.deleted = 0
  AND duplicate.id <> @custom_dev_id;

UPDATE sys_menu
SET deleted     = 1,
    update_time = NOW()
WHERE menu_name = '定制开发'
  AND menu_type = 'M'
  AND deleted = 0
  AND id <> @custom_dev_id;

UPDATE sys_menu
SET parent_id   = @custom_dev_id,
    menu_name   = '流程配置',
    menu_type   = 'M',
    icon        = 'Connection',
    sort        = 1,
    path        = '',
    component   = '',
    perm        = NULL,
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    update_time = NOW()
WHERE id = 'flow_setting_menu_001';

UPDATE sys_menu
SET parent_id   = 'flow_setting_menu_001',
    menu_name   = '流程动作',
    menu_type   = 'C',
    icon        = 'Notebook',
    sort        = 1,
    path        = '/system/flow-action-guide',
    component   = 'system/FlowActionGuide',
    perm        = 'system:flowAction:view',
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    update_time = NOW()
WHERE id = 'flow_action_guide_menu_001';
