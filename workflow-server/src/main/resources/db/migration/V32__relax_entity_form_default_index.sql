SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE `entity_form` DROP INDEX `uk_entity_default`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'entity_form'
      AND INDEX_NAME = 'uk_entity_default'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
