package com.workflow.service.entity;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.service.EntityFormService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityFormResolveServiceTest {

    @Test
    void newDataUsesFirstReachableUserTaskForm() {
        EntityDefinitionMapper entityDefinitionMapper = mock(EntityDefinitionMapper.class);
        ProcessDefinitionConfigMapper processConfigMapper = mock(ProcessDefinitionConfigMapper.class);
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityFormResolveService service = new EntityFormResolveService(
                entityDefinitionMapper,
                processConfigMapper,
                nodeFormMapper,
                formService,
                mock(RuntimeService.class),
                mock(TaskService.class));

        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1");
        entity.setEnableProcess(true);
        entity.setProcessDefinitionId("process-1");
        when(entityDefinitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(entity));

        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId("process-1");
        process.setBpmnXml(bpmn());
        when(processConfigMapper.selectById("process-1")).thenReturn(process);

        ProcessNodeForm binding = new ProcessNodeForm();
        binding.setFormId("form-first");
        when(nodeFormMapper.selectListByNodeId("process-1", "Task_First"))
                .thenReturn(List.of(binding));

        EntityForm form = new EntityForm();
        form.setId("form-first");
        when(formService.getById("form-first")).thenReturn(form);

        assertEquals("form-first", service.resolveFormForNewData("expense").getId());
    }

    @Test
    void firstUserTaskParserTraversesGateway() {
        EntityFormResolveService service = new EntityFormResolveService(
                mock(EntityDefinitionMapper.class),
                mock(ProcessDefinitionConfigMapper.class),
                mock(ProcessNodeFormMapper.class),
                mock(EntityFormService.class),
                mock(RuntimeService.class),
                mock(TaskService.class));

        assertEquals("Task_First", service.resolveFirstUserTaskId(bpmn()));
    }

    private String bpmn() {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"test\">"
                + "<startEvent id=\"Start\"/>"
                + "<exclusiveGateway id=\"Gateway\"/>"
                + "<userTask id=\"Task_First\"/>"
                + "<userTask id=\"Task_Later\"/>"
                + "<sequenceFlow id=\"F1\" sourceRef=\"Start\" targetRef=\"Gateway\"/>"
                + "<sequenceFlow id=\"F2\" sourceRef=\"Gateway\" targetRef=\"Task_First\"/>"
                + "<sequenceFlow id=\"F3\" sourceRef=\"Task_First\" targetRef=\"Task_Later\"/>"
                + "</process>"
                + "</definitions>";
    }
}
