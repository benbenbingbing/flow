package com.workflow.process.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.history.EntityHistoryImportPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.archive.infrastructure.persistence.ProcessArchiveMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessArchiveListTest {
    @Test void summaryChecksRecordAccessWithoutExpandingEntity() {
        var entities = mock(EntityHistoryImportPort.class);
        var mapper = mock(ProcessArchiveMapper.class);
        var service = new ProcessArchiveService(mock(ProcessVersionHistoryMapper.class), entities, mapper, new ObjectMapper());
        when(mapper.list("expense", "record")).thenReturn(List.of(
                new ProcessArchiveMapper.Header("archive", "历史流程", "APPROVED", "source", "source-id")));
        assertEquals("archive", service.list("expense", "record").get(0).get("id"));
        var order = inOrder(entities, mapper);
        order.verify(entities).requireReadable("expense", "record");
        order.verify(mapper).list("expense", "record");
        verify(entities, never()).readable(anyString(), anyString());
    }

    @Test void deniedRecordCannotProbeArchiveExistence() {
        var entities = mock(EntityHistoryImportPort.class);
        var mapper = mock(ProcessArchiveMapper.class);
        doThrow(new ForbiddenException("无权访问")).when(entities).requireReadable("expense", "record");
        var service = new ProcessArchiveService(mock(ProcessVersionHistoryMapper.class), entities, mapper, new ObjectMapper());
        assertThrows(ForbiddenException.class, () -> service.list("expense", "record"));
        verifyNoInteractions(mapper);
    }
}
