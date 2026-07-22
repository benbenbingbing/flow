-- V027 已在既有数据库执行，禁止继续修改其内容。
-- 视图与服务编排模块未实现，作为新的独立迁移清理遗留表。

-- 视图引擎（先删子表，再删主表）
DROP TABLE IF EXISTS `view_button_config`;
DROP TABLE IF EXISTS `view_field_config`;
DROP TABLE IF EXISTS `view_query_config`;
DROP TABLE IF EXISTS `view_definition`;

-- 服务编排
DROP TABLE IF EXISTS `service_execution_log`;
DROP TABLE IF EXISTS `service_node`;
DROP TABLE IF EXISTS `service_definition`;
DROP TABLE IF EXISTS `service_category`;
