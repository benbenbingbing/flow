package com.workflow.service;

import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTableServiceColumnDefinitionTest {

    @Test
    void emitsOnlyConfiguredDefaultValueWhenModifyingColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
        when(tableResolver.resolve("acceptance")).thenReturn("biz_acceptance");
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                "biz_acceptance"))
                .thenReturn(1);

        EntityField field = new EntityField();
        field.setFieldCode("priority");
        field.setFieldName("优先级");
        field.setFieldType(EntityField.FieldType.SELECT);
        field.setDefaultValue("normal");

        DynamicTableService service = new DynamicTableService(
                jdbcTemplate,
                fieldMapper,
                tableResolver);

        service.modifyColumn("acceptance", field);

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertTrue(ddl.getValue().contains("DEFAULT 'normal'"));
        assertFalse(ddl.getValue().contains("DEFAULT NULL DEFAULT"));
        assertEquals(1, countOccurrences(ddl.getValue(), " DEFAULT "));
    }

    private int countOccurrences(String value, String token) {
        return value.split(token, -1).length - 1;
    }
}
