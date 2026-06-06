-- Align older entity_field schemas with the columns used by EntityField.java.
-- Keep legacy columns in place because older local databases may still contain data there.

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE `entity_field` MODIFY COLUMN `field_id` VARCHAR(100) NULL COMMENT ''旧字段编码（兼容保留）''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'field_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `field_code` VARCHAR(100) NULL COMMENT ''字段编码'' AFTER `field_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'field_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `db_type` VARCHAR(100) NULL COMMENT ''数据库字段类型'' AFTER `field_type`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'db_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `field_length` INT NULL COMMENT ''字段长度'' AFTER `db_type`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'field_length'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `options_json` TEXT NULL COMMENT ''选项配置JSON'' AFTER `default_value`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'options_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `validate_rules` TEXT NULL COMMENT ''验证规则JSON'' AFTER `options_json`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'validate_rules'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `is_system` TINYINT(1) DEFAULT 0 COMMENT ''是否系统字段'' AFTER `sort_order`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'is_system'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `editable` TINYINT(1) DEFAULT 1 COMMENT ''是否可编辑'' AFTER `is_system`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'editable'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `entity_field` ADD COLUMN `is_published` TINYINT(1) DEFAULT 0 COMMENT ''是否已发布到数据表'' AFTER `editable`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'is_published'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'UPDATE `entity_field` SET `field_code` = COALESCE(NULLIF(`field_code`, ''''), `field_id`) WHERE `field_code` IS NULL OR `field_code` = ''''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'field_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'UPDATE `entity_field` SET `field_length` = COALESCE(`field_length`, `length`) WHERE `field_length` IS NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'length'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'UPDATE `entity_field` SET `field_precision` = COALESCE(`field_precision`, `precision`) WHERE `field_precision` IS NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'precision'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) > 0,
        'UPDATE `entity_field` SET `options_json` = COALESCE(NULLIF(`options_json`, ''''), `dict_type`) WHERE (`options_json` IS NULL OR `options_json` = '''') AND `dict_type` IS NOT NULL AND `dict_type` <> ''''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entity_field' AND COLUMN_NAME = 'dict_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
