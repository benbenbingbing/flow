-- 引擎流程启动动作统一合并到发起后，不再公开独立的 PROCESS_STARTED 时机。
-- 按产品决定全部后移到流程关联/待办同步完成后，不按处理器用途提前到发起前。
-- 草稿、发布、禁用及删除配置统一转换；保留动作身份、版本、策略、参数、排序及依赖。
-- 原有发起后动作和迁入动作按同一时机排序各执行一次；审计和已入队快照保持原文。
UPDATE process_action SET trigger_timing = 'PROCESS_START_EXECUTED' WHERE trigger_timing = 'PROCESS_STARTED';
