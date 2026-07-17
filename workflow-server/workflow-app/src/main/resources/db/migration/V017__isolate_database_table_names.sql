-- 数据库表命名隔离：
-- entity_* 配置表、runtime_* 跨实体运行表、process_* 平台流程表、biz_* 动态业务表。

CREATE TABLE IF NOT EXISTS `entity_table_migration_log` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `source_table` varchar(100) DEFAULT NULL COMMENT '迁移前物理表',
  `target_table` varchar(100) DEFAULT NULL COMMENT '目标物理表',
  `status` varchar(20) NOT NULL COMMENT 'PENDING/SUCCESS/FAILED/CONFLICT/MISSING',
  `source_row_count` bigint DEFAULT NULL COMMENT '迁移前行数',
  `target_row_count` bigint DEFAULT NULL COMMENT '迁移后行数',
  `error_message` text COMMENT '失败原因',
  `retry_count` int DEFAULT '0' COMMENT '重复处理次数',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_table_migration` (`entity_code`),
  KEY `idx_entity_table_migration_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体物理表命名迁移日志';

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_rename_table_if_needed$$
CREATE PROCEDURE workflow_rename_table_if_needed(
    IN p_source VARCHAR(128),
    IN p_target VARCHAR(128)
)
BEGIN
    DECLARE v_source_exists INT DEFAULT 0;
    DECLARE v_target_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_source_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = p_source;

    SELECT COUNT(*) INTO v_target_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = p_target;

    IF v_source_exists = 1 AND v_target_exists = 0 THEN
        SET @rename_sql = CONCAT(
            'RENAME TABLE `', p_source, '` TO `', p_target, '`'
        );
        PREPARE rename_stmt FROM @rename_sql;
        EXECUTE rename_stmt;
        DEALLOCATE PREPARE rename_stmt;
    ELSEIF v_source_exists = 1 AND v_target_exists = 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '数据库命名迁移发现源表和目标表同时存在';
    ELSEIF v_source_exists = 0 AND v_target_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '数据库命名迁移缺少源表和目标表';
    END IF;
END$$

DELIMITER ;

CALL workflow_rename_table_if_needed('entity_data', 'runtime_entity_record');
CALL workflow_rename_table_if_needed('node_config', 'process_node_config');
CALL workflow_rename_table_if_needed('assignee_config', 'process_node_assignee');
CALL workflow_rename_table_if_needed('form_config', 'process_form_config');
CALL workflow_rename_table_if_needed('form_field_config', 'process_form_field_config');
CALL workflow_rename_table_if_needed('flow_action', 'process_action');
CALL workflow_rename_table_if_needed('flow_action_definition', 'process_action_definition');
CALL workflow_rename_table_if_needed('flow_action_execution', 'process_action_execution');
CALL workflow_rename_table_if_needed('entity_flow_status_mapping', 'process_entity_status_mapping');

DROP PROCEDURE IF EXISTS workflow_rename_table_if_needed;
