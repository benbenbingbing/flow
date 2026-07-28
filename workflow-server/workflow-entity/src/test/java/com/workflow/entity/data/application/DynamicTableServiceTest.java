package com.workflow.entity.data.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicTableServiceTest {

    @Test
    void shouldRenderBooleanDefaultsAsNumericLiterals() {
        assertEquals(" DEFAULT 0", DynamicTableService.buildDefaultClause(booleanField("false")));
        assertEquals(" DEFAULT 0", DynamicTableService.buildDefaultClause(booleanField("0")));
        assertEquals(" DEFAULT 1", DynamicTableService.buildDefaultClause(booleanField("true")));
        assertEquals(" DEFAULT 1", DynamicTableService.buildDefaultClause(booleanField("1")));
    }

    @Test
    void shouldRejectInvalidBooleanDefaults() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DynamicTableService.buildDefaultClause(booleanField("yes")));

        assertEquals(
                "布尔字段 enabled_flag 的默认值必须是 true、false、1 或 0",
                error.getMessage());
    }

    @Test
    void shouldKeepEscapedStringDefaultsAndNullDefaultsCompatible() {
        EntityField stringField = new EntityField();
        stringField.setFieldCode("display_name");
        stringField.setFieldType(EntityField.FieldType.STRING);
        stringField.setDefaultValue("O'Reilly");

        EntityField emptyField = new EntityField();
        emptyField.setFieldCode("optional_value");
        emptyField.setFieldType(EntityField.FieldType.STRING);

        assertEquals(" DEFAULT 'O''Reilly'", DynamicTableService.buildDefaultClause(stringField));
        assertEquals(" DEFAULT NULL", DynamicTableService.buildDefaultClause(emptyField));
    }

    @Test
    void shouldIgnoreClientSuppliedDatabaseType() {
        EntityField field = new EntityField();
        field.setFieldCode("display_name");
        field.setFieldType(EntityField.FieldType.STRING);
        field.setFieldLength(32);
        field.setDbType("VARCHAR(32)); DROP TABLE sys_user; --");

        String definition = DynamicTableService.buildColumnDefinition(field);

        assertEquals("`display_name` VARCHAR(32) DEFAULT NULL COMMENT 'display_name'", definition);
        assertFalse(definition.contains("DROP TABLE"));
    }

    @Test
    void shouldRejectMaliciousColumnIdentifiers() {
        EntityField field = new EntityField();
        field.setFieldCode("safe_name");
        field.setDbColumnName("name` VARCHAR(1); DROP TABLE sys_user; --");
        field.setFieldType(EntityField.FieldType.STRING);

        assertThrows(
                IllegalArgumentException.class,
                () -> DynamicTableService.buildColumnDefinition(field));
        assertThrows(
                IllegalArgumentException.class,
                () -> DynamicTableService.quoteIdentifier("valid;DROP_TABLE"));
    }

    @Test
    void shouldEnforceTypeDimensionLimits() {
        EntityField oversizedString = new EntityField();
        oversizedString.setFieldCode("payload");
        oversizedString.setFieldType(EntityField.FieldType.STRING);
        oversizedString.setFieldLength(4097);

        EntityField invalidDecimal = new EntityField();
        invalidDecimal.setFieldCode("amount");
        invalidDecimal.setFieldType(EntityField.FieldType.DECIMAL);
        invalidDecimal.setFieldLength(2);
        invalidDecimal.setFieldPrecision(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> DynamicTableService.getDbType(oversizedString));
        assertThrows(
                IllegalArgumentException.class,
                () -> DynamicTableService.getDbType(invalidDecimal));
    }

    private EntityField booleanField(String defaultValue) {
        EntityField field = new EntityField();
        field.setFieldCode("enabled_flag");
        field.setFieldType(EntityField.FieldType.BOOLEAN);
        field.setDefaultValue(defaultValue);
        return field;
    }
}
