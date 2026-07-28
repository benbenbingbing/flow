package com.workflow.entity.data.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private EntityField booleanField(String defaultValue) {
        EntityField field = new EntityField();
        field.setFieldCode("enabled_flag");
        field.setFieldType(EntityField.FieldType.BOOLEAN);
        field.setDefaultValue(defaultValue);
        return field;
    }
}
