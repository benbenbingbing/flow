-- 新增与“定制开发”平级的顶级目录“用户手册”，并授权 super_admin、admin。
-- 固定 ID、名称和路径均参与查重，重复执行不会产生重复菜单或角色授权。

SET @manual_parent_id = COALESCE(
    (
        SELECT parent_id
        FROM sys_menu
        WHERE menu_name = '定制开发'
          AND menu_type = 'M'
          AND deleted = 0
        ORDER BY CASE WHEN id = 'dev_guide_dir' THEN 0 ELSE 1 END, create_time
        LIMIT 1
    ),
    (
        SELECT parent_id
        FROM sys_menu
        WHERE menu_name = '系统管理'
          AND menu_type = 'M'
          AND deleted = 0
        LIMIT 1
    ),
    '0'
);

SET @manual_sort = COALESCE(
    (
        SELECT sort + 1
        FROM sys_menu
        WHERE menu_name = '定制开发'
          AND menu_type = 'M'
          AND deleted = 0
        ORDER BY CASE WHEN id = 'dev_guide_dir' THEN 0 ELSE 1 END, create_time
        LIMIT 1
    ),
    6
);

SET @manual_dir_id = (
    SELECT id
    FROM sys_menu
    WHERE id = 'user_manual_dir_001'
       OR (menu_name = '用户手册' AND menu_type = 'M')
    ORDER BY CASE WHEN id = 'user_manual_dir_001' THEN 0 ELSE 1 END, deleted, create_time
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
    'user_manual_dir_001', @manual_parent_id, '用户手册', 'M', 'Notebook', @manual_sort,
    '/manual', NULL, NULL, '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @manual_dir_id IS NULL;

SET @manual_dir_id = COALESCE(@manual_dir_id, 'user_manual_dir_001');

UPDATE sys_menu
SET parent_id   = @manual_parent_id,
    menu_name   = '用户手册',
    menu_type   = 'M',
    icon        = 'Notebook',
    sort        = @manual_sort,
    path        = '/manual',
    component   = NULL,
    perm        = NULL,
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    deleted     = 0,
    update_time = NOW()
WHERE id COLLATE utf8mb4_unicode_ci
    = @manual_dir_id COLLATE utf8mb4_unicode_ci;

SET @entity_manual_id = (
    SELECT id
    FROM sys_menu
    WHERE id = 'user_manual_entity_001'
       OR path = '/manual/entity'
       OR (
            parent_id COLLATE utf8mb4_unicode_ci
                = @manual_dir_id COLLATE utf8mb4_unicode_ci
            AND menu_name = '实体配置'
            AND menu_type = 'C'
       )
    ORDER BY CASE WHEN id = 'user_manual_entity_001' THEN 0 ELSE 1 END, deleted, create_time
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
    'user_manual_entity_001', @manual_dir_id, '实体配置', 'C', 'Document', 1,
    '/manual/entity', 'manual/EntityManual', 'user-manual:entity:view',
    '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @entity_manual_id IS NULL;

SET @entity_manual_id = COALESCE(@entity_manual_id, 'user_manual_entity_001');

UPDATE sys_menu
SET parent_id   = @manual_dir_id,
    menu_name   = '实体配置',
    menu_type   = 'C',
    icon        = 'Document',
    sort        = 1,
    path        = '/manual/entity',
    component   = 'manual/EntityManual',
    perm        = 'user-manual:entity:view',
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    deleted     = 0,
    update_time = NOW()
WHERE id COLLATE utf8mb4_unicode_ci
    = @entity_manual_id COLLATE utf8mb4_unicode_ci;

SET @process_manual_id = (
    SELECT id
    FROM sys_menu
    WHERE id = 'user_manual_process_001'
       OR path = '/manual/process'
       OR (
            parent_id COLLATE utf8mb4_unicode_ci
                = @manual_dir_id COLLATE utf8mb4_unicode_ci
            AND menu_name = '流程管理'
            AND menu_type = 'C'
       )
    ORDER BY CASE WHEN id = 'user_manual_process_001' THEN 0 ELSE 1 END, deleted, create_time
    LIMIT 1
);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
    status, visible, is_frame, is_cache, create_time, update_time, deleted
)
SELECT
    'user_manual_process_001', @manual_dir_id, '流程管理', 'C', 'Connection', 2,
    '/manual/process', 'manual/ProcessManual', 'user-manual:process:view',
    '0', '0', '0', '0', NOW(), NOW(), 0
WHERE @process_manual_id IS NULL;

SET @process_manual_id = COALESCE(@process_manual_id, 'user_manual_process_001');

UPDATE sys_menu
SET parent_id   = @manual_dir_id,
    menu_name   = '流程管理',
    menu_type   = 'C',
    icon        = 'Connection',
    sort        = 2,
    path        = '/manual/process',
    component   = 'manual/ProcessManual',
    perm        = 'user-manual:process:view',
    status      = '0',
    visible     = '0',
    is_frame    = '0',
    is_cache    = '0',
    deleted     = 0,
    update_time = NOW()
WHERE id COLLATE utf8mb4_unicode_ci
    = @process_manual_id COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT REPLACE(UUID(), '-', ''), role.id, menu.id, NOW()
FROM sys_role role
JOIN sys_menu menu
  ON menu.id COLLATE utf8mb4_unicode_ci IN (
      @manual_dir_id COLLATE utf8mb4_unicode_ci,
      @entity_manual_id COLLATE utf8mb4_unicode_ci,
      @process_manual_id COLLATE utf8mb4_unicode_ci
  )
WHERE role.role_code IN ('super_admin', 'admin')
  AND role.deleted = 0
  AND menu.deleted = 0;
