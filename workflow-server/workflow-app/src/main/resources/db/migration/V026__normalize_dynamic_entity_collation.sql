-- 统一动态实体业务表排序规则，避免与平台流程表 JOIN 时出现 MySQL 1267。
-- 所有 biz_* 主表、团队表和多值表统一使用 utf8mb4_unicode_ci。

CREATE TABLE IF NOT EXISTS `system_collation_migration_log` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `table_name` varchar(128) NOT NULL COMMENT '迁移表名',
  `source_collation` varchar(64) DEFAULT NULL COMMENT '迁移前排序规则',
  `target_collation` varchar(64) NOT NULL COMMENT '目标排序规则',
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS/FAILED',
  `message` varchar(1000) DEFAULT NULL COMMENT '迁移说明',
  `migrated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collation_migration_table` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='动态实体表排序规则迁移日志';

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_normalize_biz_collation_v026$$
CREATE PROCEDURE workflow_normalize_biz_collation_v026()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table VARCHAR(128);
    DECLARE v_source_collation VARCHAR(64);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name, table_collation
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_type = 'BASE TABLE'
          AND table_name LIKE 'biz\\_%'
          AND COALESCE(table_collation, '') <> 'utf8mb4_unicode_ci'
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN table_cursor;
    migration_loop: LOOP
        FETCH table_cursor INTO v_table, v_source_collation;
        IF v_done = 1 THEN
            LEAVE migration_loop;
        END IF;

        SET @alter_sql = CONCAT(
            'ALTER TABLE `',
            REPLACE(v_table, '`', '``'),
            '` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
        );
        PREPARE alter_stmt FROM @alter_sql;
        EXECUTE alter_stmt;
        DEALLOCATE PREPARE alter_stmt;

        INSERT INTO system_collation_migration_log (
            id,
            table_name,
            source_collation,
            target_collation,
            status,
            message
        ) VALUES (
            SHA2(CONCAT('collation-v026:', v_table), 256),
            v_table,
            v_source_collation,
            'utf8mb4_unicode_ci',
            'SUCCESS',
            'Converted by V026'
        )
        ON DUPLICATE KEY UPDATE
            source_collation = VALUES(source_collation),
            target_collation = VALUES(target_collation),
            status = 'SUCCESS',
            message = 'Converted by V026',
            migrated_at = NOW();
    END LOOP;
    CLOSE table_cursor;
END$$

DELIMITER ;

CALL workflow_normalize_biz_collation_v026();
DROP PROCEDURE IF EXISTS workflow_normalize_biz_collation_v026;

SET @remaining_mixed_biz_collation = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_type = 'BASE TABLE'
    AND table_name LIKE 'biz\\_%'
    AND COALESCE(table_collation, '') <> 'utf8mb4_unicode_ci'
);

SET @collation_assert_sql = IF(
  @remaining_mixed_biz_collation = 0,
  'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V026执行后仍存在排序规则不一致的biz业务表'''
);
PREPARE collation_assert_stmt FROM @collation_assert_sql;
EXECUTE collation_assert_stmt;
DEALLOCATE PREPARE collation_assert_stmt;
