-- 为存量列表按钮配置补齐版本化组件模板绑定字段。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v035$$
CREATE PROCEDURE workflow_add_column_if_missing_v035(
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

CALL workflow_add_column_if_missing_v035(
  'entity_list_action',
  'template_id',
  'varchar(64) DEFAULT NULL COMMENT ''来源模板ID'''
);
CALL workflow_add_column_if_missing_v035(
  'entity_list_action',
  'template_version',
  'int DEFAULT NULL COMMENT ''锁定模板版本'''
);
CALL workflow_add_column_if_missing_v035(
  'entity_list_action',
  'local_overrides_document',
  'longtext DEFAULT NULL COMMENT ''模板实例本地覆盖JSON文档'''
);

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v035;
