-- 普通审批、加签原任务提交及加签子任务办理共用审批办理前/后；以任务上下文区分类型。
-- 草稿、发布及删除配置统一转换，保留动作身份、执行方式、失败策略、排序和依赖关系。
-- 同一时机统一按配置顺序执行；历史审计和已入队快照不改写。
UPDATE process_action SET trigger_timing = 'TASK_SUBMITTING' WHERE trigger_timing IN ('ADD_SIGN_SOURCE_SUBMITTING', 'ADD_SIGN_TASK_COMPLETING');
UPDATE process_action SET trigger_timing = 'TASK_SUBMITTED' WHERE trigger_timing IN ('ADD_SIGN_SOURCE_SUBMITTED', 'ADD_SIGN_TASK_COMPLETED');
