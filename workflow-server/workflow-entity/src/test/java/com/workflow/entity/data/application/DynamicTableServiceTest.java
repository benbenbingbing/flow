package com.workflow.entity.data.application;

import com.workflow.integration.database.dialect.MySqlSchemaDdlDialect;
import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.integration.database.api.SchemaColumnMetadata;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.application.EntityRelationFieldPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class DynamicTableServiceTest {

    /** 两种生命周期的首次发布都只生成 name/code，实际建表与发布预览使用同一契约。 */
    @Test
    void newEntityTablesUseNameAndCodeForBothLifecycles() {
        for (var mode : EntityDefinition.LifecycleMode.values()) {
            EntityDefinition entity = new EntityDefinition();
            entity.setId("entity-1");
            entity.setEntityCode("expense");
            entity.setLifecycleMode(mode);
            var resolver = mock(EntityPhysicalTableResolver.class);
            when(resolver.resolve(entity)).thenReturn("biz_expense");
            var fields = mock(EntityFieldMapper.class);
            List<EntityField> systemFields = List.of("id", "create_time", "update_time", "create_by", "update_by", "deleted")
                    .stream().map(code -> {
                        EntityField field = new EntityField();
                        field.setFieldCode(code);
                        field.setIsSystem(true);
                        return field;
                    }).toList();
            when(fields.findByEntityId("entity-1")).thenReturn(systemFields);
            var jdbc = mock(JdbcTemplate.class);
            var metadata = mock(SchemaMetadataPort.class);
            var service = new DynamicTableService(jdbc, fields,
                    resolver, mock(SchemaDdlExecutor.class), new MySqlSchemaDdlDialect(), metadata, com.workflow.integration.database.api.DatabaseQueryDialects.forDatabaseId("MYSQL"));

            String ddl = service.planEntityTableStructure(entity).get(0);
            assertTrue(ddl.contains("`name` VARCHAR(200)"));
            assertTrue(ddl.contains("`code` VARCHAR(100)"));
            assertFalse(ddl.contains("`data_no`"));
            assertFalse(ddl.contains("`title`"));
            assertEquals(ddl, service.createEntityTable(entity));
            assertFalse(ddl.contains("`created_at`"));
            assertFalse(ddl.contains("`updated_at`"));
            assertEquals(1, ddl.lines().filter(line -> line.startsWith("  `id`")).count());

            // 发布完成后用实际建表列回查，结构校验与指纹不能再要求退役列。
            List<SchemaColumnMetadata> columns = ddl.lines()
                    .filter(line -> line.startsWith("  `"))
                    .map(line -> new SchemaColumnMetadata(line.split("`")[1], "VARCHAR", java.sql.Types.VARCHAR,
                            200L, null, null, true, null, null, false, false, 1)).toList();
            when(metadata.tableExists("biz_expense")).thenReturn(true);
            when(metadata.columns("biz_expense")).thenReturn(columns);
            assertTrue(service.inspectSchemaDrift(entity, systemFields).isEmpty());
            assertEquals(service.targetSchemaFingerprint(entity, systemFields), service.actualSchemaFingerprint(entity));
        }
    }

    @Test
    void relationFieldCompatibilityMatchesAuthoritativeStorageType() {
        for (var type : EntityField.FieldType.values()) {
            EntityField field = new EntityField();
            field.setFieldCode("parentId");
            field.setFieldType(type);
            if (type == EntityField.FieldType.REFERENCE) field.setRefEntityId("parent");
            if (EntityRelationFieldPolicy.violation(field, "parent") == null) {
                assertEquals("VARCHAR(200)", getDbType(field), type.name());
            }
        }
    }

    @Test
    void shouldRenderBooleanDefaultsAsNumericLiterals() {
        assertEquals(" DEFAULT 0", buildDefaultClause(booleanField("false")));
        assertEquals(" DEFAULT 0", buildDefaultClause(booleanField("0")));
        assertEquals(" DEFAULT 1", buildDefaultClause(booleanField("true")));
        assertEquals(" DEFAULT 1", buildDefaultClause(booleanField("1")));
    }

    @Test
    void shouldRejectInvalidBooleanDefaults() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> buildDefaultClause(booleanField("yes")));

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

        assertEquals(" DEFAULT 'O''Reilly'", buildDefaultClause(stringField));
        assertEquals(" DEFAULT NULL", buildDefaultClause(emptyField));
    }

    @Test
    void shouldIgnoreClientSuppliedDatabaseType() {
        EntityField field = new EntityField();
        field.setFieldCode("display_name");
        field.setFieldType(EntityField.FieldType.STRING);
        field.setFieldLength(32);
        field.setDbType("VARCHAR(32)); DROP TABLE sys_user; --");

        String definition = buildColumnDefinition(field);

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
                () -> buildColumnDefinition(field));
        assertThrows(
                IllegalArgumentException.class,
                () -> quoteIdentifier("valid;DROP_TABLE"));
    }

    @Test
    void shouldRejectReservedUnicodeControlAndOversizedIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> quoteIdentifier("select"));
        assertThrows(
                IllegalArgumentException.class,
                () -> quoteIdentifier("na\u00efve"));
        assertThrows(
                IllegalArgumentException.class,
                () -> quoteIdentifier("line\nbreak"));
        assertThrows(
                IllegalArgumentException.class,
                () -> quoteIdentifier("a".repeat(64)));
        assertEquals(
                "`" + "a".repeat(63) + "`",
                quoteIdentifier("a".repeat(63)));
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
                () -> getDbType(oversizedString));
        assertThrows(
                IllegalArgumentException.class,
                () -> getDbType(invalidDecimal));
    }

    private static final MySqlSchemaDdlDialect DIALECT = new MySqlSchemaDdlDialect();
    private String getDbType(EntityField field) { return DIALECT.typeSql(EntityTableDefinitionFactory.fieldType(field)); }
    private String buildDefaultClause(EntityField field) { return DIALECT.defaultClause(EntityTableDefinitionFactory.fieldColumn(field)); }
    private String buildColumnDefinition(EntityField field) { return DIALECT.columnDefinition(EntityTableDefinitionFactory.fieldColumn(field)); }
    private String quoteIdentifier(String identifier) { return DIALECT.quoteIdentifier(SqlIdentifierPolicy.validate(identifier)); }

    private EntityField booleanField(String defaultValue) {
        EntityField field = new EntityField();
        field.setFieldCode("enabled_flag");
        field.setFieldType(EntityField.FieldType.BOOLEAN);
        field.setDefaultValue(defaultValue);
        return field;
    }
}
