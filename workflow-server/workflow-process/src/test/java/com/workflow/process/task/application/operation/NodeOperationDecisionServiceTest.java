package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeOperationDecisionServiceTest {

    private final NodeOperationConditionEvaluator evaluator = new NodeOperationConditionEvaluator();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NodeOperationPolicyParser parser = new NodeOperationPolicyParser(
            objectMapper, evaluator, new NodeOperationConfigReader(objectMapper));

    @Test
    void evaluatesPermissionConditionReasonAndHiddenOperation() {
        NodeOperationDecisionService service = simulationService(parser);
        String json = """
                {
                  "version": 1,
                  "allowedVariables": ["amount"],
                  "operations": {
                    "approve": {
                      "enabled": true,
                      "permissionCode": "task:approve",
                      "condition": "amount >= 100",
                      "reasonRequired": true
                    }
                  }
                }
                """;

        NodeOperationDecisionService.ActionDecision allowed = service.simulate(
                json,
                NodeOperationPolicy.Operation.APPROVE,
                simulation(Map.of("amount", 120), Set.of("task:approve"), "同意"));
        assertTrue(allowed.allowed());

        assertEquals("PERMISSION_DENIED", service.simulate(
                json,
                NodeOperationPolicy.Operation.APPROVE,
                simulation(Map.of("amount", 120), Set.of(), "同意")).reasonCode());
        assertEquals("CONDITION_FALSE", service.simulate(
                json,
                NodeOperationPolicy.Operation.APPROVE,
                simulation(Map.of("amount", 80), Set.of("task:approve"), "同意")).reasonCode());
        assertEquals("REASON_REQUIRED", service.simulate(
                json,
                NodeOperationPolicy.Operation.APPROVE,
                simulation(Map.of("amount", 120), Set.of("task:approve"), null)).reasonCode());
        assertEquals("DISABLED", service.simulate(
                json,
                NodeOperationPolicy.Operation.REJECT,
                simulation(Map.of("amount", 120), Set.of("task:approve"), "驳回")).reasonCode());
    }

    @Test
    void evaluatesTargetAddSignTypeAndWithdrawWindow() {
        NodeOperationDecisionService service = simulationService(parser);
        String json = """
                {
                  "version": 1,
                  "operations": {
                    "transfer": {
                      "targetScope": "FIXED",
                      "targetIds": ["user-1"]
                    },
                    "addSignBefore": {
                      "allowedAddSignTypes": ["BEFORE"]
                    },
                    "withdraw": {"withdrawWithinMinutes": 30}
                  }
                }
                """;
        Instant now = Instant.parse("2026-08-22T03:00:00Z");

        NodeOperationDecisionService.SimulationContext invalidTarget = new NodeOperationDecisionService.SimulationContext(
                Map.of(), Set.of(), "operator", "转办", Set.of("user-2"), null,
                null, Map.of(), now.minus(10, ChronoUnit.MINUTES), now);
        assertEquals("TARGET_OUT_OF_SCOPE", service.simulate(
                json, NodeOperationPolicy.Operation.TRANSFER, invalidTarget).reasonCode());

        NodeOperationDecisionService.SimulationContext validAddSign = new NodeOperationDecisionService.SimulationContext(
                Map.of(), Set.of(), "operator", "加签", Set.of(), null,
                "BEFORE", Map.of(), now.minus(10, ChronoUnit.MINUTES), now);
        assertTrue(service.simulate(
                json, NodeOperationPolicy.Operation.ADD_SIGN_BEFORE, validAddSign).allowed());

        NodeOperationDecisionService.SimulationContext expired = new NodeOperationDecisionService.SimulationContext(
                Map.of(), Set.of(), "operator", "撤回", Set.of(), null,
                null, Map.of(), now.minus(31, ChronoUnit.MINUTES), now);
        assertEquals("WITHDRAW_EXPIRED", service.simulate(
                json, NodeOperationPolicy.Operation.WITHDRAW, expired).reasonCode());
    }

    @Test
    void preservesNullRequestVariablesForOperationConditions() {
        NodeOperationDecisionService service = simulationService(parser);
        String json = """
                {
                  "version": 1,
                  "operations": {
                    "approve": {
                      "condition": "request.reqSingleForm == null"
                    }
                  }
                }
                """;
        LinkedHashMap<String, Object> requestVariables = new LinkedHashMap<>();
        requestVariables.put("reqSingleForm", null);
        Instant now = Instant.parse("2026-08-22T03:00:00Z");
        NodeOperationDecisionService.SimulationContext context =
                new NodeOperationDecisionService.SimulationContext(
                        Map.of(), Set.of(), "operator", null, Set.of(), null,
                        null, requestVariables, now.minus(10, ChronoUnit.MINUTES), now);

        // 构造上下文后修改原始 Map，不应影响操作判断使用的请求变量快照。
        requestVariables.put("reqSingleForm", Map.of("id", "late-change"));

        NodeOperationDecisionService.ActionDecision decision = service.simulate(
                json, NodeOperationPolicy.Operation.APPROVE, context);

        assertTrue(decision.allowed());
        assertTrue(context.requestVariables().containsKey("reqSingleForm"));
        assertNull(context.requestVariables().get("reqSingleForm"));
        assertThrows(UnsupportedOperationException.class,
                () -> context.requestVariables().put("extra", "value"));
    }

    @Test
    void readsPolicyFromTaskBoundDeploymentVersion() {
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        HistoryService historyService = mock(HistoryService.class);
        NodeOperationPolicyParser policyParser = mock(NodeOperationPolicyParser.class);
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        HistoricProcessInstanceQuery historyQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance historic = mock(HistoricProcessInstance.class);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getProcessDefinitionId()).thenReturn("definition-v3");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getTaskDefinitionKey()).thenReturn("approve-node");
        when(runtimeService.getVariables("instance-1")).thenReturn(Map.of());
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId("instance-1")).thenReturn(historyQuery);
        when(historyQuery.singleResult()).thenReturn(historic);
        when(historic.getStartTime()).thenReturn(new Date());

        UserTask userTask = new UserTask();
        userTask.setId("approve-node");
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("process-v3");
        process.addFlowElement(userTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(repositoryService.getBpmnModel("definition-v3")).thenReturn(model);

        EnumMap<NodeOperationPolicy.Operation, NodeOperationPolicy.Rule> rules =
                new EnumMap<>(NodeOperationPolicy.Operation.class);
        rules.put(NodeOperationPolicy.Operation.APPROVE, new NodeOperationPolicy.Rule(
                false, null, null, false, List.of(), false,
                NodeOperationPolicy.TargetScope.ANY, Set.of(), Set.of(), Set.of(), null));
        when(policyParser.parse(userTask)).thenReturn(new NodeOperationPolicy(1, true, Set.of(), rules));

        NodeOperationDecisionService service = new NodeOperationDecisionService(
                taskService, runtimeService, repositoryService, historyService, policyParser, evaluator);
        Map<String, NodeOperationDecisionService.ActionDecision> decisions =
                service.availableActions("task-1");

        assertFalse(decisions.get("approve").allowed());
        assertThrows(ForbiddenException.class, () -> service.requireAllowed(
                "task-1",
                NodeOperationPolicy.Operation.APPROVE,
                NodeOperationDecisionService.CheckContext.ofReason("尝试绕过前端")));
        verify(repositoryService, times(2)).getBpmnModel("definition-v3");
        verify(policyParser, times(2)).parse(userTask);
    }

    private NodeOperationDecisionService simulationService(NodeOperationPolicyParser policyParser) {
        return new NodeOperationDecisionService(
                mock(TaskService.class),
                mock(RuntimeService.class),
                mock(RepositoryService.class),
                mock(HistoryService.class),
                policyParser,
                evaluator);
    }

    private NodeOperationDecisionService.SimulationContext simulation(
            Map<String, Object> variables,
            Set<String> permissions,
            String reason) {
        Instant now = Instant.parse("2026-08-22T03:00:00Z");
        return new NodeOperationDecisionService.SimulationContext(
                variables, permissions, "operator", reason, Set.of(), null,
                null, Map.of(), now.minus(10, ChronoUnit.MINUTES), now);
    }
}
