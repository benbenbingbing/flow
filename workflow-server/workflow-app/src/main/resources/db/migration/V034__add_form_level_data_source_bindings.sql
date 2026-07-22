-- 为已升级环境补齐表单级统一数据源绑定文档。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v034$$
CREATE PROCEDURE workflow_add_column_if_missing_v034(
    IN p_table VARCHAR(128),
    IN p_column VARCHAR(128),
    IN p_definition VARCHAR(2000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @add_column_sql = CONCAT(
            'ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition
        );
        PREPARE add_column_stmt FROM @add_column_sql;
        EXECUTE add_column_stmt;
        DEALLOCATE PREPARE add_column_stmt;
    END IF;
END$$

DELIMITER ;

CALL workflow_add_column_if_missing_v034(
  'entity_form',
  'data_source_bindings_document',
  'longtext DEFAULT NULL COMMENT ''表单级统一数据源绑定JSON文档'''
);

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v034;
