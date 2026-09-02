-- 统一当前数据库、已有表及字符列的字符集和排序规则。
-- 过程会保留，供 Flowable 建表完成后再次调用；不要在此 DROP，避免要求 ALTER ROUTINE 权限。
-- 使用 v2 名称避开先前失败执行可能已经留下的旧过程。

ALTER DATABASE
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DELIMITER $$

CREATE PROCEDURE `workflow_v074_unify_database_collation_v2`()
    MODIFIES SQL DATA
    SQL SECURITY INVOKER
BEGIN
    DECLARE v_done BOOLEAN DEFAULT FALSE;
    DECLARE v_schema_name VARCHAR(64);
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_old_foreign_key_checks BIGINT DEFAULT 1;
    DECLARE v_old_lock_wait_timeout BIGINT DEFAULT 31536000;
    DECLARE v_statement_prepared BOOLEAN DEFAULT FALSE;
    DECLARE v_remaining_tables BIGINT DEFAULT 0;
    DECLARE v_remaining_columns BIGINT DEFAULT 0;

    DECLARE table_cursor CURSOR FOR
        SELECT t.TABLE_NAME
          FROM information_schema.TABLES t
         WHERE t.TABLE_SCHEMA = v_schema_name
           AND t.TABLE_TYPE = 'BASE TABLE'
           AND t.TABLE_NAME <> 'flyway_schema_history'
           AND (
                NOT (t.TABLE_COLLATION <=> 'utf8mb4_unicode_ci')
                OR EXISTS (
                    SELECT 1
                      FROM information_schema.COLUMNS c
                     WHERE c.TABLE_SCHEMA = t.TABLE_SCHEMA
                       AND c.TABLE_NAME = t.TABLE_NAME
                       AND c.CHARACTER_SET_NAME IS NOT NULL
                       AND (
                            NOT (c.CHARACTER_SET_NAME <=> 'utf8mb4')
                            OR NOT (c.COLLATION_NAME <=> 'utf8mb4_unicode_ci')
                       )
                )
           )
         ORDER BY t.TABLE_NAME;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF v_statement_prepared THEN
            DEALLOCATE PREPARE v074_statement;
        END IF;
        SET @@SESSION.foreign_key_checks = v_old_foreign_key_checks;
        SET @@SESSION.lock_wait_timeout = v_old_lock_wait_timeout;
        RESIGNAL;
    END;

    SET v_schema_name = DATABASE();
    IF v_schema_name IS NULL OR v_schema_name = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V074 requires a selected database';
    END IF;

    SET v_old_foreign_key_checks = @@SESSION.foreign_key_checks;
    SET v_old_lock_wait_timeout = @@SESSION.lock_wait_timeout;
    SET @@SESSION.lock_wait_timeout = 60;

    SET @@SESSION.foreign_key_checks = 0;
    OPEN table_cursor;

    table_loop: LOOP
        FETCH table_cursor INTO v_table_name;
        IF v_done THEN
            LEAVE table_loop;
        END IF;

        SET @v074_sql = CONCAT(
            'ALTER TABLE `',
            REPLACE(v_schema_name, '`', '``'),
            '`.`',
            REPLACE(v_table_name, '`', '``'),
            '` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
        );
        PREPARE v074_statement FROM @v074_sql;
        SET v_statement_prepared = TRUE;
        EXECUTE v074_statement;
        DEALLOCATE PREPARE v074_statement;
        SET v_statement_prepared = FALSE;
    END LOOP;

    CLOSE table_cursor;
    SET @@SESSION.foreign_key_checks = v_old_foreign_key_checks;
    SET @@SESSION.lock_wait_timeout = v_old_lock_wait_timeout;

    SELECT COUNT(*)
      INTO v_remaining_tables
      FROM information_schema.TABLES t
     WHERE t.TABLE_SCHEMA = v_schema_name
       AND t.TABLE_TYPE = 'BASE TABLE'
       AND t.TABLE_NAME <> 'flyway_schema_history'
       AND NOT (t.TABLE_COLLATION <=> 'utf8mb4_unicode_ci');

    SELECT COUNT(*)
      INTO v_remaining_columns
      FROM information_schema.COLUMNS c
     WHERE c.TABLE_SCHEMA = v_schema_name
       AND c.TABLE_NAME <> 'flyway_schema_history'
       AND c.CHARACTER_SET_NAME IS NOT NULL
       AND (
            NOT (c.CHARACTER_SET_NAME <=> 'utf8mb4')
            OR NOT (c.COLLATION_NAME <=> 'utf8mb4_unicode_ci')
       );

    IF v_remaining_tables <> 0 OR v_remaining_columns <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V074 collation normalization incomplete';
    END IF;
END$$

DELIMITER ;

CALL `workflow_v074_unify_database_collation_v2`();
