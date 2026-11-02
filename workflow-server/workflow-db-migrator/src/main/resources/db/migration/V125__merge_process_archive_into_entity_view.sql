-- 迁入流程档案纳入实体“查看”，读取时复用实体查看权限和数据范围。
-- 先清理引用授权再移除独立权限节点；保留历史导入管理与执行权限，以及全部档案数据。
DELETE FROM sys_role_menu
WHERE menu_id = 'process_archive_read' OR menu_id IN (
    SELECT id FROM sys_menu
    WHERE id = 'process_archive_read' OR perm = 'process:archive:read'
);
DELETE FROM sys_menu
WHERE id = 'process_archive_read' OR perm = 'process:archive:read';
