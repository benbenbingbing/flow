-- 动态列表与表单扩展配置
-- 公共属性继续使用结构化列，高级配置统一存 JSON，便于后续兼容扩展。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_json_column$$
CREATE PROCEDURE workflow_add_json_column(
    IN p_table VARCHAR(128),
    IN p_column VARCHAR(128),
    IN p_comment VARCHAR(512),
    IN p_after_modify TINYINT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @sql = CONCAT(
            'ALTER TABLE `', p_table, '`',
            ' ADD COLUMN `', p_column, '` json DEFAULT NULL COMMENT ''', p_comment, ''''
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF p_after_modify = 1 THEN
        SET @sql = CONCAT(
            'ALTER TABLE `', p_table, '`',
            ' MODIFY COLUMN `', p_column, '` json DEFAULT NULL COMMENT ''', p_comment, ''''
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL workflow_add_json_column('entity_list_config', 'view_config', '列表视图配置JSON：查询区、表格、分页、自定义组件参数', 0);

CALL workflow_add_json_column('entity_list_field', 'column_config', '列展示配置JSON', 0);
CALL workflow_add_json_column('entity_list_field', 'query_config', '查询组件配置JSON', 0);
CALL workflow_add_json_column('entity_list_field', 'render_config', '单元格渲染配置JSON', 0);

CALL workflow_add_json_column('entity_form', 'view_config', '表单视图配置JSON：布局、自定义组件参数', 0);

-- entity_form_field.validation_rules 可能已存在（旧版本为 text），需要修改类型
CALL workflow_add_json_column('entity_form_field', 'validation_rules', '结构化校验规则JSON', 1);
CALL workflow_add_json_column('entity_form_field', 'extension_config', '字段模式权限及扩展配置JSON', 0);

DROP PROCEDURE IF EXISTS workflow_add_json_column;
