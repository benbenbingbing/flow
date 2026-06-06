SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `flow_action` ADD COLUMN `sequence_flow_id` VARCHAR(100) DEFAULT NULL COMMENT ''顺序流ID'' AFTER `process_config_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'sequence_flow_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `flow_action` ADD COLUMN `description` VARCHAR(500) DEFAULT NULL COMMENT ''动作描述'' AFTER `action_name`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'description'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `flow_action` ADD COLUMN `interface_name` VARCHAR(200) DEFAULT NULL COMMENT ''接口名称'' AFTER `description`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'interface_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `flow_action` ADD COLUMN `params_json` TEXT DEFAULT NULL COMMENT ''参数JSON'' AFTER `method_name`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'params_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `flow_action` ADD COLUMN `enabled` TINYINT(1) DEFAULT 1 COMMENT ''是否启用'' AFTER `sort_order`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'enabled'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `flow_action` ADD COLUMN `version_id` VARCHAR(64) DEFAULT NULL COMMENT ''所属版本ID'' AFTER `status`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'version_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(DATA_TYPE <> 'varchar',
        'ALTER TABLE `flow_action` MODIFY COLUMN `status` VARCHAR(20) DEFAULT ''DRAFT'' COMMENT ''状态''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'status'
    LIMIT 1
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'UPDATE `flow_action` SET `sequence_flow_id` = COALESCE(NULLIF(`sequence_flow_id`, ''''), `node_id`) WHERE `sequence_flow_id` IS NULL OR `sequence_flow_id` = ''''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'node_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'UPDATE `flow_action` SET `version_id` = COALESCE(NULLIF(`version_id`, ''''), `process_version_id`) WHERE `version_id` IS NULL OR `version_id` = ''''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_action'
      AND COLUMN_NAME = 'process_version_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
