SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `process_version_history` ADD COLUMN `published_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''发布时间'' AFTER `bpmn_xml`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'process_version_history'
      AND COLUMN_NAME = 'published_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `process_version_history` ADD COLUMN `published_by` VARCHAR(64) DEFAULT NULL COMMENT ''发布人ID'' AFTER `published_at`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'process_version_history'
      AND COLUMN_NAME = 'published_by'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `process_version_history`
SET `published_at` = COALESCE(`published_at`, `created_at`)
WHERE `published_at` IS NULL;
