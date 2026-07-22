-- 独立业务实体与流程实体生命周期模式。
-- 该迁移采用幂等过程，便于 MySQL DDL 中断后修复 Flyway 记录并安全重试。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_lifecycle_mode$$
CREATE PROCEDURE workflow_add_lifecycle_mode()
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND COLUMN_NAME = 'lifecycle_mode'
    ) THEN
        ALTER TABLE entity_definition
            ADD COLUMN lifecycle_mode VARCHAR(20) NULL COMMENT '实体生命周期模式：STANDALONE/WORKFLOW'
            AFTER table_name;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND COLUMN_NAME = 'enable_process'
    ) THEN
        UPDATE entity_definition
           SET lifecycle_mode = CASE
               WHEN process_definition_id IS NOT NULL
                    OR COALESCE(enable_process, 0) = 1
                   THEN 'WORKFLOW'
               ELSE 'STANDALONE'
           END
         WHERE lifecycle_mode IS NULL OR lifecycle_mode = '';
    ELSE
        UPDATE entity_definition
           SET lifecycle_mode = CASE
               WHEN process_definition_id IS NOT NULL THEN 'WORKFLOW'
               ELSE 'STANDALONE'
           END
         WHERE lifecycle_mode IS NULL OR lifecycle_mode = '';
    END IF;

    ALTER TABLE entity_definition
        MODIFY COLUMN lifecycle_mode VARCHAR(20) NOT NULL DEFAULT 'STANDALONE'
        COMMENT '实体生命周期模式：STANDALONE/WORKFLOW';

    IF EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND INDEX_NAME = 'idx_enable_process'
    ) THEN
        ALTER TABLE entity_definition DROP INDEX idx_enable_process;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND COLUMN_NAME = 'enable_process'
    ) THEN
        ALTER TABLE entity_definition DROP COLUMN enable_process;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_definition'
           AND INDEX_NAME = 'idx_lifecycle_mode'
    ) THEN
        ALTER TABLE entity_definition
            ADD INDEX idx_lifecycle_mode (lifecycle_mode);
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_publish_history'
           AND COLUMN_NAME = 'lifecycle_mode'
    ) THEN
        ALTER TABLE entity_publish_history
            ADD COLUMN lifecycle_mode VARCHAR(20) NOT NULL DEFAULT 'STANDALONE'
            COMMENT '发布时实体生命周期模式'
            AFTER process_definition_id;
    END IF;

    UPDATE entity_publish_history
       SET lifecycle_mode = CASE
           WHEN process_definition_id IS NOT NULL THEN 'WORKFLOW'
           ELSE 'STANDALONE'
       END
     WHERE lifecycle_mode IS NULL OR lifecycle_mode = '';
END$$

DELIMITER ;

CALL workflow_add_lifecycle_mode();
DROP PROCEDURE IF EXISTS workflow_add_lifecycle_mode;
