package com.workflow.process.form.application;

import com.workflow.contracts.entity.EntityFormBinding;
import com.workflow.contracts.entity.EntityFormRuntimeContext;
import com.workflow.contracts.entity.EntityFormRuntimePort;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体表单解析服务测试。
 */
class EntityFormResolveServiceTest {

    @Test
    void newDataUsesPublishedDefaultFormBeforeFirstUserTaskForm() {
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

        assertEquals(
                "form-default",
                service.resolveFormForNewData("expense").get("id"));
        verify(processConfigMapper, never()).selectById("process-1");
    }

    @Test
    void newDataFallsBackToFirstReachableUserTaskFormWithoutDefaultForm() {
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
                        null)));

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

    @Test
    void completedDataFallsBackToDefaultFormWhenHistoricalBindingIsMissing() {
        EntityFormRuntimePort entityFormRuntimePort =
                mock(EntityFormRuntimePort.class);
        ProcessPublishedSnapshotService snapshotService =
                mock(ProcessPublishedSnapshotService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        HistoryService historyService = mock(HistoryService.class);
        EntityFormResolveService service = new EntityFormResolveService(
                entityFormRuntimePort,
                mock(ProcessDefinitionConfigMapper.class),
                snapshotService,
                runtimeService,
                mock(TaskService.class),
                historyService);

        Map<String, Object> defaultForm = Map.of(
                "id", "form-default",
                "customComponent", "ProjectMemberChangeForm");
        when(entityFormRuntimePort.findContext("member-change")).thenReturn(
                Optional.of(new EntityFormRuntimeContext(
                        "entity-1",
                        "member-change",
                        "process-1",
                        true,
                        defaultForm)));

        ProcessInstanceQuery processQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(processQuery);
        when(processQuery.processInstanceBusinessKey("data-1"))
                .thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery historicProcessQuery =
                mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance historicProcess =
                mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery())
                .thenReturn(historicProcessQuery);
        when(historicProcessQuery.processInstanceBusinessKey("data-1"))
                .thenReturn(historicProcessQuery);
        when(historicProcessQuery.list()).thenReturn(List.of(historicProcess));
        when(historicProcess.getId()).thenReturn("pi-1");
        when(historicProcess.getProcessDefinitionId()).thenReturn("pd-1");
        when(historicProcess.getStartTime()).thenReturn(new Date(1000));

        HistoricTaskInstanceQuery historicTaskQuery =
                mock(HistoricTaskInstanceQuery.class);
        HistoricTaskInstance historicTask =
                mock(HistoricTaskInstance.class);
        when(historyService.createHistoricTaskInstanceQuery())
                .thenReturn(historicTaskQuery);
        when(historicTaskQuery.processInstanceId("pi-1"))
                .thenReturn(historicTaskQuery);
        when(historicTaskQuery.list()).thenReturn(List.of(historicTask));
        when(historicTask.getTaskDefinitionKey()).thenReturn("pmo_review");
        when(historicTask.getEndTime()).thenReturn(new Date(2000));

        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("history-1");
        when(snapshotService.getNodeFormsContextByProcessDefinitionId(
                "pd-1",
                "pmo_review"))
                .thenReturn(
                        new ProcessPublishedSnapshotService.PublishedNodeForms(
                                history,
                                List.of()));

        assertEquals(
                defaultForm,
                service.resolveFormForViewData(
                        "member-change",
                        "data-1"));
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
