-- UI HOTFIX 改为预检后直接发布，撤除独立复核权限。
-- V051 已进入主分支且属于不可变历史，本迁移仅做前向状态收敛与权限清理。

-- 未发布的旧申请绑定的是旧草稿、旧影响范围和旧发布窗口，不能静默转为可发布状态。
-- 统一取消后，申请人必须基于当前草稿重新预检并直接发布；CANCELLED 会自动释放 open_slot。
UPDATE ui_config_hotfix_request
   SET status = 'CANCELLED',
       cancelled_by = 'flyway:V065',
       cancelled_at = CURRENT_TIMESTAMP,
       cancel_reason = '升级移除独立复核，原未发布申请已取消，请重新预检后直接发布',
       update_time = CURRENT_TIMESTAMP
 WHERE status IN ('PENDING_REVIEW', 'APPROVED')
   AND release_id IS NULL;

-- sys_role_menu 没有外键级联，必须先撤角色授权，再删除功能权限节点。
DELETE role_grant
FROM sys_role_menu role_grant
JOIN sys_menu menu ON menu.id = role_grant.menu_id
WHERE menu.id = 'entity_ui_hotfix_review_permission'
   OR menu.perm = 'entity:ui-config:hotfix:review';

DELETE FROM sys_menu
WHERE id = 'entity_ui_hotfix_review_permission'
   OR perm = 'entity:ui-config:hotfix:review';

-- review_required、reviewer_id/name/comment/reviewed_at 保留为历史审计证据。
-- PUBLISHING 以及已发布、观察中、已回滚等历史记录均保持原状。
