package com.workflow.process.definition;

import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.FlowActionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        when(versionHistoryMapper.findMaxVersionByProcessConfigId("process-1")).thenReturn(2);

        ProcessPublishHistoryService service = new ProcessPublishHistoryService(versionHistoryMapper, flowActionService, nodeFormMapper);

        assertEquals(3, service.nextVersion("process-1"));
    }

    @Test
    void recordPublishCreatesActiveHistoryAndPublishesActions() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionService flowActionService = mock(FlowActionService.class);
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        doAnswer(invocation -> {
            ProcessVersionHistory history = invocation.getArgument(0);
            history.setId("version-1");
            return 1;
        }).when(versionHistoryMapper).insert(org.mockito.Mockito.any(ProcessVersionHistory.class));
        when(nodeFormMapper.selectByProcessConfigId("process-1")).thenReturn(List.of(nodeForm("task-1", "form-1", 0)));

        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setId("process-1");
        config.setProcessKey("expense_flow");
        config.setProcessName("费用流程");
        ProcessPublishHistoryService service = new ProcessPublishHistoryService(versionHistoryMapper, flowActionService, nodeFormMapper);

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
        assertEquals("[{\"nodeId\":\"task-1\",\"nodeName\":\"审批\",\"formId\":\"form-1\",\"isReadonly\":0,\"sortOrder\":0}]",
                history.getNodeFormsSnapshot());
        assertEquals("deployment-1", history.getDeploymentId());
        assertEquals(ProcessVersionHistory.Status.ACTIVE.name(), history.getStatus());
        verify(flowActionService).publishActions("process-1", "version-1");
    }

    @Test
    void nextVersionStartsAtOneWhenNoHistoryExists() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionService flowActionService = mock(FlowActionService.class);
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);

        ProcessPublishHistoryService service = new ProcessPublishHistoryService(versionHistoryMapper, flowActionService, nodeFormMapper);

        assertEquals(1, service.nextVersion("process-1"));
    }

    private static ProcessNodeForm nodeForm(String nodeId, String formId, int sortOrder) {
        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setProcessConfigId("process-1");
        nodeForm.setNodeId(nodeId);
        nodeForm.setNodeName("审批");
        nodeForm.setFormId(formId);
        nodeForm.setIsReadonly(0);
        nodeForm.setSortOrder(sortOrder);
        return nodeForm;
    }
}
