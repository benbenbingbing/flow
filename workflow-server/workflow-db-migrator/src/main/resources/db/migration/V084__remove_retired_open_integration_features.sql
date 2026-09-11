-- 开放集成只保留应用、机器凭据及 Embed/OAuth 共用的安全底座。
-- 本迁移有意删除开放流程、场景、Webhook、Connector 和集成 Secret 的历史数据；
-- 必须在所有旧版本 Pod 停止访问这些表后执行，回退只能依赖数据库备份。

DELETE FROM `sys_role_menu`
 WHERE `menu_id` = 'integration_perm_delivery_replay';

DELETE FROM `sys_menu`
 WHERE `id` = 'integration_perm_delivery_replay';

-- 该权限自 V013 起即用于应用 Client Credential 轮换，仍属核心能力；
-- 仅更新展示名称，技术权限编码保持不变。
UPDATE `sys_menu`
   SET `menu_name` = '轮换应用凭据',
       `update_time` = CURRENT_TIMESTAMP
 WHERE `id` = 'integration_perm_secret_rotate';

-- 手册入口继续承载保留的应用管理、OAuth Client Credential 与 Embed 接入说明。
UPDATE `sys_menu`
   SET `menu_name` = '集成应用与 Embed',
       `remark` = '集成应用、Client Credential、OAuth 与 Embed 接入说明',
       `update_time` = CURRENT_TIMESTAMP
 WHERE `id` = 'user_manual_open_integration_001';

-- 接口服务目录不再提供 Connector 类型。先清理能通过关系列精确定位的引用，
-- 再删除定义，避免留下指向已退役实现的活动列表配置。
UPDATE `entity_list_field` AS `field_config`
JOIN `ui_data_source_definition` AS `source_definition`
  ON `source_definition`.`id` = `field_config`.`data_source_id`
   SET `field_config`.`data_source_id` = NULL,
       `field_config`.`data_source_operation_code` = NULL
 WHERE `source_definition`.`source_type` = 'INTEGRATION_CONNECTOR';

UPDATE `entity_list_config` AS `list_config`
JOIN `ui_data_source_definition` AS `source_definition`
  ON `source_definition`.`id` = `list_config`.`query_data_source_id`
   SET `list_config`.`query_data_source_id` = NULL,
       `list_config`.`query_operation_code` = NULL
 WHERE `source_definition`.`source_type` = 'INTEGRATION_CONNECTOR';

DELETE FROM `ui_data_source_definition`
 WHERE `source_type` = 'INTEGRATION_CONNECTOR';

ALTER TABLE `ui_data_source_definition`
  MODIFY COLUMN `source_type` varchar(30)
    COLLATE utf8mb4_unicode_ci NOT NULL
    COMMENT 'DICTIONARY/STATIC_OPTIONS/REGISTERED_PROVIDER/RUNTIME_CONTEXT/STRUCTURED_COMPUTE',
  MODIFY COLUMN `provider_code` varchar(100)
    COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'Provider注册编码';

-- ui_config_release 等不可变发布快照可能保存历史 dataSourceId。其 JSON 结构随
-- 配置类型和版本变化，无法在 SQL 中无歧义定位 Connector 引用，因此不改写历史快照；
-- 应用层必须拒绝已退役类型，重新发布配置后自然生成不含该引用的新快照。

-- 幂等表由 Embed 继续复用，只清理能通过 operation 精确归属旧开放流程的数据。
DELETE FROM `integration_idempotency_record`
 WHERE `operation` IN (
   'PROCESS_START',
   'PROCESS_CANCEL',
   'MESSAGE_CORRELATE'
 );

-- Webhook handler 已退役，清除通用 Outbox 中不会再有消费者的历史物化任务。
DELETE FROM `workflow_outbox_event`
 WHERE `topic` = 'INTEGRATION_DOMAIN_EVENT';

-- 先删除持有外键的子表，再删除父表。
DROP TABLE `webhook_delivery`;
DROP TABLE `webhook_subscription`;
DROP TABLE `webhook_event`;
DROP TABLE `webhook_endpoint`;

DROP TABLE `integration_process_binding`;
DROP TABLE `integration_workflow_scenario_revision`;
DROP TABLE `integration_workflow_scenario`;

DROP TABLE `integration_connector_config`;
DROP TABLE `integration_secret`;
DROP TABLE `integration_process_grant`;
DROP TABLE `integration_application_scope`;
