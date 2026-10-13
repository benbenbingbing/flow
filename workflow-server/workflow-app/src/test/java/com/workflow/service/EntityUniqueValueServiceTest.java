package com.workflow.service;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityUniqueValueService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueValueMapper;
import com.workflow.core.database.mybatis.ParameterizedSql;
import static org.mockito.ArgumentMatchers.*;

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
        EntityUniqueValueMapper businessMapper = mock(EntityUniqueValueMapper.class);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(businessMapper)
                .insertClaim(argThat(statement -> statement.sql().startsWith("INSERT INTO entity_unique_value")));
        EntityUniqueValueService service = new EntityUniqueValueService(businessMapper);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.replace("order", "record-2", Map.of("orderNo", "SO-001")));

        assertEquals("ENTITY_UNIQUE_VALUE_CONFLICT", exception.getErrorCode());
    }
}
