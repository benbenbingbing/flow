package com.workflow.service;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityUniqueValueService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** 唯一值预留必须把数据库并发冲突转换为稳定业务错误。 */
class EntityUniqueValueServiceTest {

    @Test
    void mapsDatabaseUniqueKeyCollisionToBusinessConflict() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(jdbcTemplate)
                .update(startsWith("INSERT INTO entity_unique_value"), any(Object[].class));
        EntityUniqueValueService service = new EntityUniqueValueService(jdbcTemplate);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.replace("order", "record-2", Map.of("orderNo", "SO-001")));

        assertEquals("ENTITY_UNIQUE_VALUE_CONFLICT", exception.getErrorCode());
    }
}
