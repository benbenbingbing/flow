package com.workflow.migration.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SchemaChangeWorkerTest {

    @Test
    void acceptsSupportedBusinessTableOperations() {
        assertDoesNotThrow(
                () -> SchemaChangeWorker.validate(
                        "CREATE TABLE IF NOT EXISTS `biz_order` "
                                + "(`id` VARCHAR(64) PRIMARY KEY)"));
        assertDoesNotThrow(
                () -> SchemaChangeWorker.validate(
                        "ALTER TABLE `biz_order` "
                                + "ADD COLUMN `note` VARCHAR(100)"));
        assertDoesNotThrow(
                () -> SchemaChangeWorker.validate(
                        "CREATE INDEX `idx_biz_order_status` "
                                + "ON `biz_order` (`status`)"));
        assertDoesNotThrow(
                () -> SchemaChangeWorker.validate(
                        "DROP TABLE IF EXISTS `biz_order_multi`;"));
    }

    @Test
    void rejectsNonBusinessTablesAndMultipleStatements() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaChangeWorker.validate(
                        "DROP TABLE `sys_user`"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaChangeWorker.validate(
                        "ALTER TABLE `biz_order` DROP COLUMN `x`; "
                                + "DROP TABLE `sys_user`"));
    }

    @Test
    void rejectsDmlAndSqlComments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaChangeWorker.validate(
                        "UPDATE `biz_order` SET `status` = 'x'"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaChangeWorker.validate(
                        "ALTER TABLE `biz_order` ADD COLUMN `x` INT --"));
    }
}
