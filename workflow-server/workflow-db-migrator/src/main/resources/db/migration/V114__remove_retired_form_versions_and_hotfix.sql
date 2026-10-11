-- V113 已完成当前表单配置迁移；旧 FORM 发布记录不再参与运行、导出或历史选择。
-- 保留 LIST 发布历史以及独立发布审计，删除表单版本数据不会改变流程的稳定 formId 绑定。
DELETE FROM ui_config_release WHERE config_type = 'FORM';

-- 观察指标通过外键引用申请表，必须先删除子表。对应运行代码与 Mapper 同步退役，
-- 不保留会在列表历史、提交或任务办理过程中查询这些表的隐藏路径。
DROP TABLE ui_hotfix_observation_metric;
DROP TABLE ui_config_hotfix_request;
DROP TABLE ui_config_hotfix_target;

-- 清理热修复专属授权，避免角色仍持有已删除功能的权限节点。
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT id FROM sys_menu WHERE perm LIKE 'entity:ui-config:hotfix%');
DELETE FROM sys_menu WHERE perm LIKE 'entity:ui-config:hotfix%';
