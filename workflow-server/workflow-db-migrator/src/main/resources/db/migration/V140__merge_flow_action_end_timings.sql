-- 结束动作统一等待外层事务的同步处理完成，取消原生事件和业务执行后两个公开出口。
-- 保留每个动作的身份、执行方式、失败策略、排序和依赖；合并后按统一时机的配置排序执行。
-- 审计及已经入队的执行快照保留原文，工作线程继续按入队快照执行，不改写历史事实。
UPDATE process_action SET trigger_timing = 'PROCESS_WITHDRAWN' WHERE trigger_timing = 'PROCESS_WITHDRAW_EXECUTED';
UPDATE process_action SET trigger_timing = 'PROCESS_TERMINATED' WHERE trigger_timing = 'PROCESS_TERMINATE_EXECUTED';
