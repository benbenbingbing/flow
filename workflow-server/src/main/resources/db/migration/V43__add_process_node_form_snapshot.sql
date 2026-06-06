SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `process_version_history` ADD COLUMN `node_forms_snapshot` LONGTEXT DEFAULT NULL COMMENT ''节点表单绑定快照JSON'' AFTER `bpmn_xml`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'process_version_history'
      AND COLUMN_NAME = 'node_forms_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
