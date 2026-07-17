package com.workflow.process.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessPublishedSnapshotServiceTest {

    @Test
    void getNodeFormsReadsLatestPublishedSnapshotByProcessKey() {
        ProcessVersionHistoryMapper mapper = mock(ProcessVersionHistoryMapper.class);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("version-1");
        history.setProcessKey("expense_flow");
        history.setNodeFormsSnapshot("""
                [{"nodeId":"task-1","nodeName":"审批","formId":"form-1","isReadonly":1,"sortOrder":0},
                 {"nodeId":"task-2","nodeName":"复核","formId":"form-2","isReadonly":0,"sortOrder":0}]
                """);
        when(mapper.findLatestByProcessKey("expense_flow")).thenReturn(history);

        ProcessPublishedSnapshotService service = new ProcessPublishedSnapshotService(mapper, new ObjectMapper());

        List<ProcessNodeForm> nodeForms = service.getNodeForms("expense_flow", "task-1");

        assertEquals(1, nodeForms.size());
        assertEquals("task-1", nodeForms.get(0).getNodeId());
        assertEquals("form-1", nodeForms.get(0).getFormId());
        assertEquals(1, nodeForms.get(0).getIsReadonly());
    }

    @Test
    void getNodeFormsFailsWhenProcessHasNoPublishedSnapshot() {
        ProcessVersionHistoryMapper mapper = mock(ProcessVersionHistoryMapper.class);
        ProcessPublishedSnapshotService service = new ProcessPublishedSnapshotService(mapper, new ObjectMapper());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.getNodeForms("expense_flow", "task-1"));

        assertEquals("流程未发布: expense_flow", exception.getMessage());
    }
}
