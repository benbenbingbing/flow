package com.workflow.entity.data.application;

import com.workflow.entity.data.application.port.SchemaDdlExecutor;

import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaColumnMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 核对发布预检的整条查询，不把七产品 SQL 契约测试等同于非 MySQL 实库验证。 */
class DynamicTableLargeTextUniqueScanTest {
    @ParameterizedTest
    @EnumSource(DatabaseVendor.class)
    void samplesDuplicateGroupsWithoutGroupingSortingOrTruncatingLob(DatabaseVendor vendor) {
        var mapper = mock(com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueConflictMapper.class);
        var metadata = mock(SchemaMetadataPort.class);
        var resolver = mock(EntityPhysicalTableResolver.class);
        var entity = new EntityDefinition();
        entity.setId("sample"); entity.setEntityCode("sample");
        var field = new EntityField();
        field.setFieldCode("notes"); field.setDbColumnName("memo");
        field.setFieldType(EntityField.FieldType.TEXT); field.setIsUnique(true);
        when(resolver.resolve(entity)).thenReturn("biz_sample");
        when(metadata.tableExists("biz_sample")).thenReturn(true);
        when(metadata.columns("biz_sample")).thenReturn(List.of(new SchemaColumnMetadata(
                "memo", "TEXT", java.sql.Types.LONGVARCHAR, null, null, null,
                true, null, null, false, false, 1)));
        var query = DatabaseQueryDialects.forVendor(vendor);
        var service = new DynamicTableService(mapper, mock(EntityFieldMapper.class), resolver,
                mock(SchemaDdlExecutor.class), DatabaseDialects.forVendor(vendor), metadata, query);

        assertTrue(service.scanUniqueConflicts(entity, List.of(field)).isEmpty());
        verify(mapper).scan("biz_sample", "memo", com.workflow.integration.database.api.schema.SchemaType.Kind.TEXT, query);
        String rendered = com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueConflictMapper.Sql.scan(java.util.Map.of(
                "table", "biz_sample", "column", "memo", "kind", com.workflow.integration.database.api.schema.SchemaType.Kind.TEXT, "dialect", query));
        assertFalse(rendered.contains("GROUP BY"));
        assertFalse(rendered.contains("SUBSTR"));
        assertFalse(rendered.contains("CAST("));
        assertTrue(rendered.contains("COUNT(*)"));
        assertTrue(rendered.contains("NOT EXISTS"));
        assertTrue(rendered.contains("ORDER BY " + query.quoteIdentifier("candidate") + "." + query.quoteIdentifier("id")));
        assertFalse(rendered.contains("ORDER BY " + query.quoteIdentifier("candidate") + "." + query.quoteIdentifier("memo")));
        String deleted = "." + query.quoteIdentifier("deleted") + " = 0";
        assertEquals(4, rendered.split(java.util.regex.Pattern.quote(deleted), -1).length - 1);
        boolean lob = vendor == DatabaseVendor.ORACLE || vendor == DatabaseVendor.DM
                || vendor == DatabaseVendor.OCEANBASE_ORACLE;
        assertEquals(lob, rendered.contains("DBMS_LOB.COMPARE"));
        assertFalse(rendered.contains("notes"), "必须使用可信物理列名");
    }

    @Test
    void skipsUniqueConflictQueryForColumnNotYetCreatedByPublishPlan() {
        var mapper = mock(com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueConflictMapper.class);
        var metadata = mock(SchemaMetadataPort.class);
        var resolver = mock(EntityPhysicalTableResolver.class);
        var entity = new EntityDefinition();
        entity.setId("sample"); entity.setEntityCode("sample");
        var field = new EntityField();
        field.setFieldCode("new_unique"); field.setDbColumnName("new_unique");
        field.setFieldType(EntityField.FieldType.STRING); field.setIsUnique(true);
        when(resolver.resolve(entity)).thenReturn("biz_sample");
        when(metadata.tableExists("biz_sample")).thenReturn(true);
        when(metadata.columns("biz_sample")).thenReturn(List.of(new SchemaColumnMetadata(
                "id", "VARCHAR", java.sql.Types.VARCHAR, 64L, null, null,
                false, null, null, true, true, 1)));
        var service = new DynamicTableService(mapper, mock(EntityFieldMapper.class), resolver,
                mock(SchemaDdlExecutor.class), DatabaseDialects.forVendor(DatabaseVendor.MYSQL),
                metadata, DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));

        assertTrue(service.scanUniqueConflicts(entity, List.of(field)).isEmpty());
        verifyNoInteractions(mapper);
    }
}
