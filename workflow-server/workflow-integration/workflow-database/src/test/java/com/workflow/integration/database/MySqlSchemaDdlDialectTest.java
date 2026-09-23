package com.workflow.integration.database;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.dialect.MySqlSchemaDdlDialect;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MySqlSchemaDdlDialectTest {
    private final MySqlSchemaDdlDialect dialect = new MySqlSchemaDdlDialect();

    @Test
    void rendersAtomicIndexesAndRequiredPrimaryKeyWithoutNullDefault() {
        var table = new SchemaTable("biz_orders", List.of(
                new SchemaColumn("id", SchemaType.string(64), false, SchemaDefault.none(), "主键", false),
                new SchemaColumn("update_time", SchemaType.of(SchemaType.Kind.TIMESTAMP), false,
                        SchemaDefault.currentTimestamp(), "更新时间", true)), List.of("id"),
                List.of(new SchemaIndex("idx_updated", List.of("update_time"), false)), "订单", true);
        List<String> plan = dialect.createTable(table);
        assertEquals(1, plan.size());
        String sql = plan.get(0);
        assertTrue(sql.contains("`id` VARCHAR(64) NOT NULL COMMENT '主键'"));
        assertTrue(sql.contains("ON UPDATE CURRENT_TIMESTAMP"));
        assertTrue(sql.contains("KEY `idx_updated` (`update_time`)"));
        assertTrue(sql.contains("COLLATE=utf8mb4_unicode_ci"));
        assertDoesNotThrow(() -> dialect.validateStatement(sql));
    }

    @Test
    void literalSqlPayloadStaysInsideDefaultAndComment() {
        String value = "O'Reilly\\路径'; DROP TABLE sys_user; --";
        var column = new SchemaColumn("label", SchemaType.string(200), true, SchemaDefault.literal(value), value, false);
        String sql = dialect.addColumn("biz_orders", column).get(0);
        assertTrue(sql.contains("O''Reilly\\\\路径''; DROP TABLE sys_user; --"));
        assertDoesNotThrow(() -> dialect.validateStatement(sql));
        assertThrows(IllegalArgumentException.class, () -> dialect.quoteIdentifier("x`; DROP TABLE sys_user"));
        assertThrows(IllegalArgumentException.class, () -> dialect.validateStatement(sql + "; DROP TABLE sys_user"));
        assertThrows(IllegalArgumentException.class, () -> dialect.validateStatement("CREATE TRIGGER arbitrary BEFORE UPDATE ON x SET @x = 1"));
    }

    @Test
    void structureComparisonIncludesDecimalPrecisionAndScale() {
        var type = SchemaType.decimal(18, 2);
        assertTrue(dialect.columnTypeMatches(type, "decimal", null, 18, 2));
        assertFalse(dialect.columnTypeMatches(type, "decimal", null, 18, 3));
        assertFalse(dialect.columnTypeMatches(type, "decimal", null, 16, 2));
        assertTrue(dialect.columnTypeMatches(SchemaType.of(SchemaType.Kind.BOOLEAN), "tinyint", null, 3, 0));
        assertFalse(dialect.columnTypeMatches(SchemaType.string(200), "varchar", 100L, null, null));
    }

    @Test
    void selectionRequiresExplicitOceanbaseModeAndRejectsTypos() {
        assertEquals(DatabaseVendor.MYSQL, DatabaseDialects.resolve("auto", "jdbc:mysql://localhost/workflow"));
        assertEquals(DatabaseVendor.OCEANBASE_MYSQL, DatabaseDialects.resolve("ob-mysql", "jdbc:oceanbase://localhost/workflow"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseDialects.resolve("auto", "jdbc:oceanbase://localhost/workflow"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseDialects.resolve("myslq", "jdbc:mysql://localhost/workflow"));
    }
}
