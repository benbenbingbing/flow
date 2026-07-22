package com.workflow.service;

import com.workflow.mapper.EntityFieldMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicTableServiceMultiValueTest {

    @Test
    void createsOneNeutralMultiTablePerBusinessEntity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
        when(tableResolver.resolve("expense")).thenReturn("biz_expense");
        DynamicTableService service = new DynamicTableService(
                jdbcTemplate,
                fieldMapper,
                tableResolver);

        service.ensureEntityMultiValueTable("expense");

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertTrue(ddl.getValue().contains("CREATE TABLE IF NOT EXISTS biz_expense_multi"));
        assertTrue(ddl.getValue().contains("target_entity_id"));
        assertTrue(ddl.getValue().contains("target_record_id"));
        assertTrue(ddl.getValue().contains("COLLATE=utf8mb4_unicode_ci"));
        assertFalse(ddl.getValue().contains("value_type"));
        assertFalse(ddl.getValue().contains("dict_code"));
    }
}
