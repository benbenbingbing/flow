package com.workflow.process.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewResponse;
import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import com.workflow.process.task.application.nextapproval.FlowableConditionEvaluator;
import com.workflow.process.task.application.nextapproval.NextApprovalPreviewService;
import com.workflow.process.task.application.nextapproval.NextApprovalRouteService;
import com.workflow.process.task.application.nextapproval.NextApproverCandidateService;
import com.workflow.process.task.application.nextapproval.NextApproverSelectionPolicyReader;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NextApprovalFlowableConsistencyTest {

    @Test
    void previewedExclusiveTargetMatchesTheTaskCreatedByFlowable() {
        ProcessEngine engine = buildEngine();
        try {
            engine.getRepositoryService()
                    .createDeployment()
                    .addString("next-approval-consistency.bpmn20.xml", bpmn())
                    .deploy();

            assertPreviewMatchesRuntime(engine, "approve", 2000,
                    Set.of("high-value-review"));
            assertPreviewMatchesRuntime(engine, "approve", 200,
                    Set.of("normal-review"));
            assertPreviewMatchesRuntime(engine, "reject", 2000,
                    Set.of("normal-review"));
        } finally {
            engine.close();
        }
    }

    private void assertPreviewMatchesRuntime(
            ProcessEngine engine,
            String action,
            int amount,
            Set<String> expectedTargets) {
        ProcessInstance instance = engine.getRuntimeService()
                .startProcessInstanceByKey(
                        "next_approval_consistency",
                        Map.of("amount", 100));
        Task current = engine.getTaskService()
                .createTaskQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey("current-review")
                .singleResult();

        NextApprovalPreviewRequest request = new NextApprovalPreviewRequest();
        request.setAction(action);
        request.setComment("");
        request.setFormData(Map.of("amount", amount));
        NextApprovalPreviewResponse preview = previewService(engine)
                .preview(current.getId(), request);

        assertEquals(NextApprovalPreviewStatus.READY, preview.getStatus());
        assertFalse(preview.getScopeKey().isBlank());
        Set<String> previewedTargets = preview.getNodes().stream()
                .map(node -> node.getNodeId())
                .collect(Collectors.toSet());
        assertEquals(expectedTargets, previewedTargets);

        engine.getTaskService().complete(current.getId(), Map.of(
                "approved", action,
                "action", action,
                "comment", "",
                "amount", amount));
        Set<String> activeTargets = engine.getTaskService()
                .createTaskQuery()
                .processInstanceId(instance.getId())
                .active()
                .list()
                .stream()
                .map(Task::getTaskDefinitionKey)
                .collect(Collectors.toSet());

        assertEquals(
                previewedTargets,
                activeTargets,
                "预览节点必须与 Flowable 完成当前任务后实际创建的任务一致");
    }

    private NextApprovalPreviewService previewService(ProcessEngine engine) {
        ObjectMapper objectMapper = new ObjectMapper();
        NodeFormSubmissionService formService =
                mock(NodeFormSubmissionService.class);
        when(formService.projectEditableData(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        NextApprovalRouteService routeService = new NextApprovalRouteService(
                engine.getTaskService(),
                engine.getRuntimeService(),
                engine.getRepositoryService(),
                new FlowableConditionEvaluator(engine),
                new NextApproverSelectionPolicyReader(objectMapper),
                formService,
                objectMapper);
        NextApproverCandidateService candidateService =
                mock(NextApproverCandidateService.class);
        when(candidateService.defaultAssignees(any(), any()))
                .thenReturn(List.of(new NextApproverCandidateDTO(
                        "user-1", "alice", "Alice")));
        return new NextApprovalPreviewService(
                routeService, candidateService);
    }

    private ProcessEngine buildEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl(
                "jdbc:h2:mem:next_approval_"
                        + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        return configuration.buildProcessEngine();
    }

    private String bpmn() {
        String assigneeConfig = escape("""
                {"assigneeType":"user","assigneeValue":"alice",
                 "nextApproverSelection":{"version":1,
                 "visible":true,"editable":true,
                 "source":{"type":"SCOPE","rules":[
                 {"type":"ALL_USERS","values":[]}]}}}
                """.replaceAll("\\s+", ""));
        return """
                <bpmn:definitions
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  targetNamespace="http://workflow.test/process">
                  <bpmn:process id="next_approval_consistency" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:userTask id="current-review" name="当前审批" />
                    <bpmn:exclusiveGateway id="route" default="to-normal" />
                    %s
                    %s
                    <bpmn:endEvent id="high-end" />
                    <bpmn:endEvent id="normal-end" />
                    <bpmn:sequenceFlow id="to-current" sourceRef="start" targetRef="current-review" />
                    <bpmn:sequenceFlow id="to-route" sourceRef="current-review" targetRef="route" />
                    <bpmn:sequenceFlow id="to-high" sourceRef="route" targetRef="high-value-review">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${approved == 'approve' &amp;&amp; amount >= 1000}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="to-normal" sourceRef="route" targetRef="normal-review" />
                    <bpmn:sequenceFlow id="high-to-end" sourceRef="high-value-review" targetRef="high-end" />
                    <bpmn:sequenceFlow id="normal-to-end" sourceRef="normal-review" targetRef="normal-end" />
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(
                userTask("high-value-review", "高额审批", assigneeConfig),
                userTask("normal-review", "普通审批", assigneeConfig));
    }

    private String userTask(
            String id,
            String name,
            String assigneeConfig) {
        return """
                <bpmn:userTask id="%s" name="%s" flowable:assignee="alice">
                  <bpmn:extensionElements>
                    <flowable:properties>
                      <flowable:property name="assigneeConfig" value="%s" />
                    </flowable:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """.formatted(id, name, assigneeConfig);
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
