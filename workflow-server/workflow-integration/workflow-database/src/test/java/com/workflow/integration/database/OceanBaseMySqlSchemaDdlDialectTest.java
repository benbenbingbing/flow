package com.workflow.integration.database;

import com.workflow.integration.database.api.schema.SchemaColumn;
import com.workflow.integration.database.api.schema.SchemaDefault;
import com.workflow.integration.database.api.schema.SchemaTable;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import com.workflow.integration.database.schema.dialect.OceanBaseMySqlSchemaDdlDialect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OceanBaseMySqlSchemaDdlDialectTest {
    private final OceanBaseMySqlSchemaDdlDialect dialect = new OceanBaseMySqlSchemaDdlDialect();

    @Test
    void rejectsUnsupportedDefaultsBeforeCreateAddOrModifyWithoutLosingTheConfiguredValue() {
        for (SchemaType.Kind type : List.of(SchemaType.Kind.TEXT, SchemaType.Kind.LARGE_TEXT)) {
            for (String value : List.of("默认值 O'Reilly\\中文", "")) {
                var column = new SchemaColumn("description", SchemaType.of(type), true,
                        SchemaDefault.literal(value), "业务说明", false);
                var table = new SchemaTable("biz_ob_defaults", List.of(column), List.of(), List.of(), null, false);
                var error = assertThrows(IllegalArgumentException.class, () -> dialect.createTable(table));
                assertTrue(error.getMessage().contains("description"));
                assertTrue(error.getMessage().contains("请清除字段默认值"));
                assertThrows(IllegalArgumentException.class, () -> dialect.addColumn(table.name(), column));
                assertThrows(IllegalArgumentException.class, () -> dialect.modifyColumn(table.name(), column));
                assertEquals(value, column.defaultValue().value());
                // MySQL 8 仍支持表达式默认值，OB 的限制不能泄漏到原方言。
                assertTrue(new MySqlSchemaDdlDialect().columnDefinition(column).contains(" DEFAULT ("));
            }
        }
    }

    @Test
    void retainsLargeTextCapacityAndOrdinaryStringDefaults() {
        for (SchemaType.Kind type : List.of(SchemaType.Kind.TEXT, SchemaType.Kind.LARGE_TEXT)) {
            var column = new SchemaColumn("description", SchemaType.of(type), true, SchemaDefault.none(), null, false);
            assertEquals("`description` " + (type == SchemaType.Kind.TEXT ? "TEXT" : "LONGTEXT") + " DEFAULT NULL",
                    dialect.columnDefinition(column));
        }
        var column = new SchemaColumn("label", SchemaType.string(200), true,
                SchemaDefault.literal("O'Reilly\\中文"), null, false);
        assertEquals(new MySqlSchemaDdlDialect().columnDefinition(column), dialect.columnDefinition(column));
    }
}
