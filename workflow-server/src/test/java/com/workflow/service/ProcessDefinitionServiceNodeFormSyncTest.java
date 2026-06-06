package com.workflow.service;

import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.ProcessNodeFormMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProcessDefinitionServiceNodeFormSyncTest {

    @Test
    void syncNodeFormsFromBpmnPersistsMultipleEntityFormIdsInOrder() throws Exception {
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        ProcessDefinitionService service = new ProcessDefinitionService(
                null, null, null, null, null, null, null, null, null, null, null,
                null, nodeFormMapper, null);

        Method method = ProcessDefinitionService.class.getDeclaredMethod("syncNodeFormsFromBpmn", String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, "process-1", bpmnWithEntityFormIds());

        verify(nodeFormMapper).deleteByProcessConfigIdAndNodeId("process-1", "task-1");
        ArgumentCaptor<ProcessNodeForm> captor = ArgumentCaptor.forClass(ProcessNodeForm.class);
        verify(nodeFormMapper, org.mockito.Mockito.times(2)).insert(captor.capture());

        List<ProcessNodeForm> inserted = captor.getAllValues();
        assertEquals("form-a", inserted.get(0).getFormId());
        assertEquals(0, inserted.get(0).getSortOrder());
        assertEquals(1, inserted.get(0).getIsReadonly());
        assertEquals("form-b", inserted.get(1).getFormId());
        assertEquals(1, inserted.get(1).getSortOrder());
        assertEquals(1, inserted.get(1).getIsReadonly());
    }

    private static String bpmnWithEntityFormIds() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\">"
                + "<bpmn:process id=\"process_1\">"
                + "<bpmn:userTask id=\"task-1\" name=\"审批\">"
                + "<bpmn:extensionElements>"
                + "<flowable:properties>"
                + "<flowable:property name=\"entityFormIds\" value=\"[&quot;form-a&quot;,&quot;form-b&quot;]\" />"
                + "<flowable:property name=\"entityFormReadonly\" value=\"true\" />"
                + "</flowable:properties>"
                + "</bpmn:extensionElements>"
                + "</bpmn:userTask>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }
}
