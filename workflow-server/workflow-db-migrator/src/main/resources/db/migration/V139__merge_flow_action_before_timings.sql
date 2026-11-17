-- 同一业务边界不再区分“校验”和“处理”，处理器按配置顺序决定具体行为。
-- 同时迁移草稿、已发布和已删除配置，保留动作 ID、发布版本、启用状态、排序及依赖关系。
-- 执行日志和历史载荷保持原文，不改写已经发生的执行事实；无运行时旧编码分支。
UPDATE process_action SET trigger_timing = 'PROCESS_STARTING' WHERE trigger_timing = 'PROCESS_START_VALIDATING';
UPDATE process_action SET trigger_timing = 'PROCESS_WITHDRAWING' WHERE trigger_timing = 'PROCESS_WITHDRAW_VALIDATING';
UPDATE process_action SET trigger_timing = 'PROCESS_TERMINATING' WHERE trigger_timing = 'PROCESS_TERMINATE_VALIDATING';
UPDATE process_action SET trigger_timing = 'TASK_FORM_SAVING' WHERE trigger_timing = 'TASK_SUBMIT_VALIDATING';
UPDATE process_action SET trigger_timing = 'TASK_TRANSFERRING' WHERE trigger_timing = 'TASK_TRANSFER_VALIDATING';
UPDATE process_action SET trigger_timing = 'TASK_ADD_SIGNING' WHERE trigger_timing = 'TASK_ADD_SIGN_VALIDATING';
UPDATE process_action SET trigger_timing = 'ADD_SIGN_SOURCE_SUBMITTING' WHERE trigger_timing = 'ADD_SIGN_SOURCE_VALIDATING';
UPDATE process_action SET trigger_timing = 'ADD_SIGN_TASK_COMPLETING' WHERE trigger_timing = 'ADD_SIGN_TASK_VALIDATING';
