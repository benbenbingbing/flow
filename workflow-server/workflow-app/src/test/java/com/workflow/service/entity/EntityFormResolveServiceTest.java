package com.workflow.service.entity;

import com.workflow.contracts.entity.EntityFormBinding;
import com.workflow.contracts.entity.EntityFormRuntimeContext;
import com.workflow.contracts.entity.EntityFormRuntimePort;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体表单解析服务测试。
 */
class EntityFormResolveServiceTest {

    @Test
    void newDataUsesFirstReachableUserTaskForm() {
        EntityFormRuntimePort entityFormRuntimePort =
                mock(EntityFormRuntimePort.class);
        ProcessDefinitionConfigMapper processConfigMapper =
                mock(ProcessDefinitionConfigMapper.class);
        ProcessPublishedSnapshotService snapshotService =
                mock(ProcessPublishedSnapshotService.class);
        EntityFormResolveService service = new EntityFormResolveService(
                entityFormRuntimePort,
                processConfigMapper,
                snapshotService,
                mock(RuntimeService.class),
                mock(TaskService.class),
                mock(HistoryService.class));

        when(entityFormRuntimePort.findContext("expense")).thenReturn(
                Optional.of(new EntityFormRuntimeContext(
                        "entity-1",
                        "expense",
                        "process-1",
                        true,
                        Map.of("id", "form-default"))));

        ProcessDefinitionConfig process = new ProcessDefinitionConfig();
        process.setId("process-1");
        process.setProcessKey("expense-flow");
        process.setBpmnXml(bpmn());
        when(processConfigMapper.selectById("process-1"))
                .thenReturn(process);

        ProcessNodeForm binding = new ProcessNodeForm();
        binding.setFormId("form-first");
        binding.setFormReleaseId("release-2");
        binding.setFormReleaseVersion(2);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("history-1");
        when(snapshotService.getNodeFormsContext(
                "expense-flow",
                "Task_First"))
                .thenReturn(
                        new ProcessPublishedSnapshotService.PublishedNodeForms(
                                history,
                                List.of(binding)));

        EntityFormBinding runtimeBinding = new EntityFormBinding(
                null,
                "form-first",
                "release-2",
                2);
        when(entityFormRuntimePort.findFormByBinding(
                runtimeBinding,
                "history-1",
                UiRuntimePurpose.NEW_INSTANCE))
                .thenReturn(Map.of("id", "form-first"));

        assertEquals(
                "form-first",
                service.resolveFormForNewData("expense").get("id"));
        verify(entityFormRuntimePort).requireCurrentBindingForNewData(
                runtimeBinding,
                "history-1");
    }

    @Test
    void firstUserTaskParserTraversesGateway() {
        EntityFormResolveService service = new EntityFormResolveService(
                mock(EntityFormRuntimePort.class),
                mock(ProcessDefinitionConfigMapper.class),
                mock(ProcessPublishedSnapshotService.class),
                mock(RuntimeService.class),
                mock(TaskService.class),
                mock(HistoryService.class));

        assertEquals(
                "Task_First",
                service.resolveFirstUserTaskId(bpmn()));
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
