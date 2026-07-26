-- MySQL does not roll back DDL migrations atomically. The first V041 version could
-- leave a failed history row after creating part of the menu data. V041 is
-- idempotent, so remove only that exact failed entry before validation and retry it.

SET @repair_failed_v041_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'flyway_schema_history'
    ),
    'DELETE FROM `flyway_schema_history`'
        ' WHERE `version` = ''041'''
        ' AND `success` = 0'
        ' AND `script` = ''V041__add_system_audit_menu.sql''',
    'SELECT 1'
);

PREPARE repair_failed_v041_statement FROM @repair_failed_v041_sql;
EXECUTE repair_failed_v041_statement;
DEALLOCATE PREPARE repair_failed_v041_statement;
