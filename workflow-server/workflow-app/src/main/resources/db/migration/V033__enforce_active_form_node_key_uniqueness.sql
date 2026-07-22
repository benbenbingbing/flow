-- 同一表单的活动节点编码必须唯一；软删除历史通过生成列 NULL 保留重复能力。
-- 迁移在建立约束前检测已有活动重复并报告一个确定性样本，不静默改写业务配置。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_enforce_active_form_node_key_uniqueness$$
CREATE PROCEDURE workflow_enforce_active_form_node_key_uniqueness()
BEGIN
    DECLARE duplicate_group_count BIGINT DEFAULT 0;
    DECLARE duplicate_sample VARCHAR(100) DEFAULT '';
    DECLARE migration_error VARCHAR(128);
    DECLARE active_column_count INT DEFAULT 0;
    DECLARE active_index_count INT DEFAULT 0;
    DECLARE active_index_valid_count INT DEFAULT 0;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_form_node'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'V033 requires entity_form_node created by V030';
    END IF;

    SELECT COUNT(*)
      INTO duplicate_group_count
      FROM (
          SELECT form_id, node_key
            FROM entity_form_node
           WHERE deleted = 0
           GROUP BY form_id, node_key
          HAVING COUNT(*) > 1
      ) active_duplicates;

    IF duplicate_group_count > 0 THEN
        SELECT CONCAT(
                   LEFT(form_id, 24),
                   '/',
                   LEFT(node_key, 50),
                   ' x',
                   COUNT(*))
          INTO duplicate_sample
          FROM entity_form_node
         WHERE deleted = 0
         GROUP BY form_id, node_key
        HAVING COUNT(*) > 1
         ORDER BY form_id, node_key
         LIMIT 1;

        SET migration_error = LEFT(
            CONCAT(
                'V033 active node_key duplicates: groups=',
                duplicate_group_count,
                ', sample=',
                duplicate_sample),
            128);
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = migration_error;
    END IF;

    SELECT COUNT(*)
      INTO active_column_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'entity_form_node'
       AND COLUMN_NAME = 'active_node_key';

    IF active_column_count = 0 THEN
        ALTER TABLE entity_form_node
            ADD COLUMN active_node_key varchar(100)
                GENERATED ALWAYS AS (
                    CASE
                        WHEN deleted = 0 THEN node_key
                        ELSE NULL
                    END
                ) STORED
                COMMENT '仅活动节点参与表单内节点编码唯一约束'
                AFTER node_key;
    ELSEIF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'entity_form_node'
           AND COLUMN_NAME = 'active_node_key'
           AND EXTRA LIKE '%STORED GENERATED%'
           AND GENERATION_EXPRESSION LIKE '%deleted%'
           AND GENERATION_EXPRESSION LIKE '%node_key%'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'V033 active_node_key exists with incompatible definition';
    END IF;

    SELECT COUNT(*)
      INTO active_index_count
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'entity_form_node'
       AND INDEX_NAME = 'uk_entity_form_node_active_key';

    IF active_index_count > 0 THEN
        SELECT COUNT(*)
          INTO active_index_valid_count
          FROM (
              SELECT INDEX_NAME
                FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'entity_form_node'
                 AND INDEX_NAME = 'uk_entity_form_node_active_key'
               GROUP BY INDEX_NAME
              HAVING MAX(NON_UNIQUE) = 0
                 AND GROUP_CONCAT(
                         COLUMN_NAME
                         ORDER BY SEQ_IN_INDEX
                         SEPARATOR ','
                     ) = 'form_id,active_node_key'
          ) valid_active_index;

        IF active_index_valid_count = 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT =
                    'V033 active node_key index exists but is incompatible';
        END IF;
    ELSE
        ALTER TABLE entity_form_node
            ADD UNIQUE KEY uk_entity_form_node_active_key
                (form_id, active_node_key);
    END IF;
END$$

DELIMITER ;

CALL workflow_enforce_active_form_node_key_uniqueness();
DROP PROCEDURE IF EXISTS workflow_enforce_active_form_node_key_uniqueness;
