package com.workflow.entity.runtime;

import com.workflow.entity.data.application.*;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.data.domain.policy.StaleProcessEventException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class EntityProcessLifecycleMutationTest {
    @Mock EntityDataDynamicMapper mapper;
    @Mock DynamicTableService tables;
    @Mock EntityStatusMapper statuses;
    @InjectMocks EntityDataMutationService service;

    @Test void normalEndOnlyUpdatesLifecycleWithoutBusinessStateLookup() {
        when(tables.getTableName("expense")).thenReturn("biz_expense");
        when(mapper.selectByIdForUpdate("biz_expense", "1"))
                .thenReturn(Map.of("process_instance_id", "p1", "status", "REJECTED"));
        service.markProcessEnded("p1", "expense", "1", "COMPLETED", null);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> update = ArgumentCaptor.forClass(Map.class);
        verify(mapper).update(eq("biz_expense"), update.capture());
        assertEquals("COMPLETED", update.getValue().get("process_status"));
        assertFalse(update.getValue().containsKey("status"));
        verifyNoInteractions(statuses);
        verify(mapper).updateCurrentTask("biz_expense", "1", null, null, null);
    }

    @Test void oldGenerationCannotCompleteNewProcessOrClearItsTasks() {
        when(tables.getTableName("expense")).thenReturn("biz_expense");
        when(mapper.selectByIdForUpdate("biz_expense", "1"))
                .thenReturn(Map.of("process_instance_id", "p2", "process_status", "RUNNING"));
        assertThrows(StaleProcessEventException.class,
                () -> service.markProcessEnded("p1", "expense", "1", "TERMINATED", "TERMINATED"));
        verify(mapper, never()).update(anyString(), anyMap());
        verify(mapper, never()).updateCurrentTask(any(), any(), any(), any(), any());
        verifyNoInteractions(statuses);
    }
}
