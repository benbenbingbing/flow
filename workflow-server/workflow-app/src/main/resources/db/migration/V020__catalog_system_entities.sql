-- 平台系统表登记模式。系统表只进入实体目录，不由动态实体引擎维护。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_entity_storage_mode$$
CREATE PROCEDURE workflow_add_entity_storage_mode()
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND COLUMN_NAME = 'storage_mode'
    ) THEN
        ALTER TABLE entity_definition
            ADD COLUMN storage_mode VARCHAR(20) NOT NULL DEFAULT 'DYNAMIC'
            COMMENT '存储模式：DYNAMIC/SYSTEM'
            AFTER lifecycle_mode;
    END IF;

    UPDATE entity_definition
       SET storage_mode = 'DYNAMIC'
     WHERE storage_mode IS NULL OR storage_mode = '';

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND INDEX_NAME = 'idx_storage_mode'
    ) THEN
        ALTER TABLE entity_definition
            ADD INDEX idx_storage_mode (storage_mode);
    END IF;
END$$

DELIMITER ;

CALL workflow_add_entity_storage_mode();
DROP PROCEDURE IF EXISTS workflow_add_entity_storage_mode;

INSERT INTO entity_definition (
    id,
    entity_code,
    entity_name,
    description,
    table_name,
    lifecycle_mode,
    storage_mode,
    process_definition_id,
    status,
    created_by
)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT('SYSTEM_ENTITY:', tables.TABLE_NAME), 256), 1, 15), 16, 10),
    tables.TABLE_NAME,
    COALESCE(NULLIF(tables.TABLE_COMMENT, ''), tables.TABLE_NAME),
    CONCAT('平台系统表目录：', tables.TABLE_NAME),
    tables.TABLE_NAME,
    'STANDALONE',
    'SYSTEM',
    NULL,
    'PUBLISHED',
    'system'
FROM information_schema.TABLES tables
WHERE tables.TABLE_SCHEMA = DATABASE()
  AND tables.TABLE_TYPE = 'BASE TABLE'
  AND tables.TABLE_NAME LIKE 'sys\_%'
  AND NOT EXISTS (
      SELECT 1
        FROM entity_definition definition
       WHERE definition.entity_code = tables.TABLE_NAME
  );

INSERT INTO entity_field (
    id,
    entity_id,
    field_code,
    field_name,
    field_type,
    db_type,
    field_length,
    field_precision,
    db_column_name,
    is_required,
    is_unique,
    sort_order,
    is_system,
    editable,
    is_published
)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT('SYSTEM_FIELD:', columns.TABLE_NAME, ':', columns.COLUMN_NAME), 256), 1, 15), 16, 10),
    definition.id,
    columns.COLUMN_NAME,
    COALESCE(NULLIF(columns.COLUMN_COMMENT, ''), columns.COLUMN_NAME),
    CASE
        WHEN columns.DATA_TYPE IN ('tinyint', 'bit', 'boolean') THEN 'BOOLEAN'
        WHEN columns.DATA_TYPE IN ('smallint', 'mediumint', 'int') THEN 'INTEGER'
        WHEN columns.DATA_TYPE = 'bigint' THEN 'LONG'
        WHEN columns.DATA_TYPE IN ('decimal', 'numeric', 'float', 'double') THEN 'DECIMAL'
        WHEN columns.DATA_TYPE = 'date' THEN 'DATE'
        WHEN columns.DATA_TYPE IN ('datetime', 'timestamp') THEN 'DATETIME'
        WHEN columns.DATA_TYPE IN ('text', 'tinytext', 'mediumtext', 'longtext', 'json') THEN 'TEXT'
        ELSE 'STRING'
    END,
    columns.COLUMN_TYPE,
    CASE
        WHEN columns.CHARACTER_MAXIMUM_LENGTH <= 2147483647
            THEN columns.CHARACTER_MAXIMUM_LENGTH
        ELSE NULL
    END,
    columns.NUMERIC_SCALE,
    columns.COLUMN_NAME,
    CASE WHEN columns.IS_NULLABLE = 'NO' THEN 1 ELSE 0 END,
    CASE WHEN columns.COLUMN_KEY IN ('PRI', 'UNI') THEN 1 ELSE 0 END,
    columns.ORDINAL_POSITION,
    1,
    0,
    1
FROM information_schema.COLUMNS columns
JOIN entity_definition definition
  ON definition.entity_code = columns.TABLE_NAME
 AND definition.storage_mode = 'SYSTEM'
WHERE columns.TABLE_SCHEMA = DATABASE()
  AND columns.TABLE_NAME LIKE 'sys\_%'
  AND NOT EXISTS (
      SELECT 1
        FROM entity_field field
       WHERE field.entity_id = definition.id
         AND field.field_code = columns.COLUMN_NAME
  );
