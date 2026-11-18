-- 移除独立引擎任务完成时机，收敛至审批结果已写入、继续路由前的节点离开边界。
-- 不提前到 TASK_ADVANCING：该阶段尚未汇总本次审批结果，可能读到上次审批的变量。
-- 草稿、发布及删除配置统一转换；保留动作身份、排序、依赖、执行方式及失败策略。
-- 已有节点离开动作和迁移动作按统一组内顺序各执行一次；历史审计和入队快照不改写。
UPDATE process_action SET trigger_timing = 'NODE_COMPLETED' WHERE trigger_timing = 'TASK_COMPLETING';
