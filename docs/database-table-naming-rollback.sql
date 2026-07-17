-- 数据库命名隔离的人工应急回滚脚本。
-- 仅用于 V017 上线验证失败并且新版本应用已经停止的场景。
-- 动态 biz_* 表必须根据 entity_table_migration_log 逐实体人工确认后回滚。

RENAME TABLE `runtime_entity_record` TO `entity_data`;
RENAME TABLE `process_node_config` TO `node_config`;
RENAME TABLE `process_node_assignee` TO `assignee_config`;
RENAME TABLE `process_form_config` TO `form_config`;
RENAME TABLE `process_form_field_config` TO `form_field_config`;
RENAME TABLE `process_action` TO `flow_action`;
RENAME TABLE `process_action_definition` TO `flow_action_definition`;
RENAME TABLE `process_action_execution` TO `flow_action_execution`;
RENAME TABLE `process_entity_status_mapping` TO `entity_flow_status_mapping`;

-- 动态表回滚示例：
-- RENAME TABLE `biz_expense` TO `entity_data_expense`;
-- UPDATE entity_definition
-- SET table_name = 'entity_data_expense'
-- WHERE entity_code = 'expense';
