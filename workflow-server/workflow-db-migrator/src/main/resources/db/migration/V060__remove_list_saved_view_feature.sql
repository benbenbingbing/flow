-- 前向撤除已下线的列表保存视图与聚合能力；V058 已执行，必须保持不可变。
-- 保留平台增强中心、索引向导和配置引用相关结构及权限。
DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT id
    FROM sys_menu
    WHERE id IN (
        '2075800000000000002',
        '2075800000000000003',
        '2075800000000000004'
    )
       OR perm IN ('list-view:manage', 'list-view:share', 'list-view:aggregate')
);

DELETE FROM sys_menu
WHERE id IN (
    '2075800000000000002',
    '2075800000000000003',
    '2075800000000000004'
)
   OR perm IN ('list-view:manage', 'list-view:share', 'list-view:aggregate');

DROP TABLE IF EXISTS entity_list_saved_view_audit;
DROP TABLE IF EXISTS entity_list_saved_view;
