SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_publish_history` ADD COLUMN `process_definition_id` VARCHAR(64) DEFAULT NULL COMMENT ''发布时绑定流程定义ID'' AFTER `entity_name`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'entity_publish_history'
      AND COLUMN_NAME = 'process_definition_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `entity_publish_history` h
JOIN `entity_definition` e ON h.`entity_id` = e.`id`
SET h.`process_definition_id` = e.`process_definition_id`
WHERE h.`process_definition_id` IS NULL;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_publish_history` ADD INDEX `idx_entity_publish_history_process` (`process_definition_id`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'entity_publish_history'
      AND INDEX_NAME = 'idx_entity_publish_history_process'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
