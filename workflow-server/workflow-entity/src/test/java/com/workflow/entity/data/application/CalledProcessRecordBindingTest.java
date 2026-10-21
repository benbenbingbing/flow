package com.workflow.entity.data.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalledProcessRecordBindingTest {
    @Mock EntityDataDynamicMapper mapper;
    @Mock DynamicTableService tables;
    @Mock EntityRecordTeamService team;
    @InjectMocks EntityDataMutationService service;

    @Test
    void bindsUnderRecordLockAndPreservesBusinessFieldsAndTransitionStatus() {
        when(tables.getTableName("child")).thenReturn("wf_child");
        when(mapper.selectByIdForUpdate("wf_child", "record-b")).thenReturn(Map.of("id", "record-b", "status", "DRAFT", "amount", 120));
        service.bindCalledProcess("child", "record-b", "child-instance", "alice", "Alice", null);
        var updates = ArgumentCaptor.forClass(Map.class);
        var order = inOrder(mapper);
        order.verify(mapper).selectByIdForUpdate("wf_child", "record-b");
        order.verify(mapper).update(eq("wf_child"), updates.capture());
        assertEquals("child-instance", updates.getValue().get("process_instance_id"));
        assertEquals("RUNNING", updates.getValue().get("process_status"));
        assertEquals("alice", updates.getValue().get("submitter_id"));
        assertFalse(updates.getValue().containsKey("status"));
        assertFalse(updates.getValue().containsKey("amount"));
    }

    @Test
    void refusesActiveAndCompletedInstancesAndMissingRecordsBeforeAnyWrite() {
        when(tables.getTableName("child")).thenReturn("wf_child");
        for (String status : new String[] {"RUNNING", "COMPLETED"}) {
            when(mapper.selectByIdForUpdate("wf_child", "record-b")).thenReturn(Map.of(
                    "id", "record-b", "process_instance_id", "previous-instance", "process_status", status));
            assertThrows(BusinessConflictException.class, () -> service.bindCalledProcess(
                    "child", "record-b", "new-instance", "alice", "Alice", null));
        }
        when(mapper.selectByIdForUpdate("wf_child", "record-b")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.bindCalledProcess(
                "child", "record-b", "new-instance", "alice", "Alice", null));
        verify(mapper, never()).update(anyString(), anyMap());
        verifyNoInteractions(team);
    }
}
