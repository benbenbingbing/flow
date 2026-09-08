-- 移除未接入当前业务功能的历史表。
-- 任务使用 process_task 和 Flowable，流程设计草稿使用 process_definition_config；
-- 状态更新及同步不依赖旧状态历史表，首页也不读取旧工作台布局和快捷入口。
-- 已有环境执行前应备份这六张表；其中可能保留默认工作台预置配置。
DROP TABLE IF EXISTS
    entity_status_history,
    process_common_opinion,
    process_draft,
    process_task_instance,
    workbench_config,
    workbench_shortcut;
