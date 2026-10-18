package com.workflow.entity.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.*;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.history.infrastructure.persistence.EntityHistorySourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryWriteGuardTest {
    @Test void readonlyRecordsCannotBeUpdatedOrOverwrittenThroughCreatePayload() {
        var sources = mock(EntityHistorySourceMapper.class);
        when(sources.readonlyCount("expense", "historic")).thenReturn(1);
        var guard = new EntityHistoryWriteGuard(sources);
        var mutation = mock(EntityDataMutationService.class);
        var writer = new EntityAggregateWriter(mock(EntityDataDynamicService.class), mutation, mock(DynamicTableService.class),
                mock(EntityDataDynamicMapper.class), mock(EntityRelationRuntimeService.class), new ObjectMapper());
        ReflectionTestUtils.setField(writer, "historyWriteGuard", guard);
        var context = EntityMutationContext.builder(EntityMutationSourceType.IMPORT, "TEST", "测试").build();
        assertThrows(BusinessConflictException.class, () -> writer.apply(EntityMutationCommand.update("expense", "historic", Map.of("name", "修改"), context)));
        assertThrows(BusinessConflictException.class, () -> writer.apply(EntityMutationCommand.create("expense", Map.of("id", "historic", "name", "改写"), context)));
        verifyNoInteractions(mutation);
        assertDoesNotThrow(() -> guard.requireWritable("expense", "shared"));
    }
}
