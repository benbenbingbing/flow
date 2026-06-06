package com.workflow.process.definition;

import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.FlowActionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessPublishHistoryServiceTest {

    @Test
    void nextVersionIncrementsLatestVersion() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionService flowActionService = mock(FlowActionService.class);
        when(versionHistoryMapper.findMaxVersionByProcessConfigId("process-1")).thenReturn(2);

        ProcessPublishHistoryService service = new ProcessPublishHistoryService(versionHistoryMapper, flowActionService);

        assertEquals(3, service.nextVersion("process-1"));
    }

    @Test
    void recordPublishCreatesActiveHistoryAndPublishesActions() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionService flowActionService = mock(FlowActionService.class);
        doAnswer(invocation -> {
            ProcessVersionHistory history = invocation.getArgument(0);
            history.setId("version-1");
            return 1;
        }).when(versionHistoryMapper).insert(org.mockito.Mockito.any(ProcessVersionHistory.class));

        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setId("process-1");
        config.setProcessKey("expense_flow");
        config.setProcessName("费用流程");
        ProcessPublishHistoryService service = new ProcessPublishHistoryService(versionHistoryMapper, flowActionService);

        ProcessVersionHistory result = service.recordPublish(config, "<xml />", "deployment-1", 3, "首版");

        ArgumentCaptor<ProcessVersionHistory> captor = ArgumentCaptor.forClass(ProcessVersionHistory.class);
        verify(versionHistoryMapper).insert(captor.capture());
        ProcessVersionHistory history = captor.getValue();
        assertSame(history, result);
        assertEquals("process-1", history.getProcessConfigId());
        assertEquals("expense_flow", history.getProcessKey());
        assertEquals("费用流程", history.getProcessName());
        assertEquals(3, history.getVersion());
        assertEquals("首版", history.getVersionDescription());
        assertEquals("<xml />", history.getBpmnXml());
        assertEquals("deployment-1", history.getDeploymentId());
        assertEquals(ProcessVersionHistory.Status.ACTIVE.name(), history.getStatus());
        verify(flowActionService).publishActions("process-1", "version-1");
    }

    @Test
    void nextVersionStartsAtOneWhenNoHistoryExists() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionService flowActionService = mock(FlowActionService.class);

        ProcessPublishHistoryService service = new ProcessPublishHistoryService(versionHistoryMapper, flowActionService);

        assertEquals(1, service.nextVersion("process-1"));
    }
}
