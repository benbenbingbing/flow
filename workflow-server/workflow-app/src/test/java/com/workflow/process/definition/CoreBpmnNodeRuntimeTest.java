package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.delegate.DemoExpressionService;
import org.flowable.common.engine.impl.el.DefaultExpressionManager;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoreBpmnNodeRuntimeTest {

    @Test
    void manualTaskAndEmbeddedSubProcessReachExpectedUserTasks() {
        ProcessEngine engine = buildEngine(Map.of());
        try {
            deploy(engine, "manual-subprocess.bpmn20.xml", manualAndSubProcessBpmn());
            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("manual_subprocess");
            Task inside = task(engine, instance.getId(), "inside-subprocess");
            assertNotNull(inside);

            engine.getTaskService().complete(inside.getId());

            assertNotNull(task(engine, instance.getId(), "after-subprocess"));
        } finally {
            engine.close();
        }
    }

    @Test
    void exclusiveGatewayUsesConditionAndDefaultFlow() {
        ProcessEngine engine = buildEngine(Map.of());
        try {
            deploy(engine, "exclusive.bpmn20.xml", exclusiveGatewayBpmn());

            ProcessInstance high = engine.getRuntimeService()
                    .startProcessInstanceByKey("exclusive_gateway", Map.of("amount", 150));
            assertNotNull(task(engine, high.getId(), "high-review"));
            assertNull(task(engine, high.getId(), "normal-review"));

            ProcessInstance normal = engine.getRuntimeService()
                    .startProcessInstanceByKey("exclusive_gateway", Map.of("amount", 50));
            assertNotNull(task(engine, normal.getId(), "normal-review"));
            assertNull(task(engine, normal.getId(), "high-review"));
        } finally {
            engine.close();
        }
    }

    @Test
    void parallelGatewayWaitsForEveryActiveBranchBeforeJoining() {
        ProcessEngine engine = buildEngine(Map.of());
        try {
            deploy(engine, "parallel.bpmn20.xml", parallelGatewayBpmn());
            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("parallel_gateway");
            Task branchA = task(engine, instance.getId(), "parallel-a");
            Task branchB = task(engine, instance.getId(), "parallel-b");
            assertNotNull(branchA);
            assertNotNull(branchB);

            engine.getTaskService().complete(branchA.getId());
            assertNull(task(engine, instance.getId(), "parallel-after"));

            engine.getTaskService().complete(branchB.getId());
            assertNotNull(task(engine, instance.getId(), "parallel-after"));
        } finally {
            engine.close();
        }
    }

    @Test
    void inclusiveGatewayActivatesMatchingBranchesAndJoinsThem() {
        ProcessEngine engine = buildEngine(Map.of());
        try {
            deploy(engine, "inclusive.bpmn20.xml", inclusiveGatewayBpmn());
            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "inclusive_gateway",
                            Map.of("needsFinance", true, "needsLegal", true));
            Task finance = task(engine, instance.getId(), "finance-review");
            Task legal = task(engine, instance.getId(), "legal-review");
            assertNotNull(finance);
            assertNotNull(legal);

            engine.getTaskService().complete(finance.getId());
            assertNull(task(engine, instance.getId(), "inclusive-after"));

            engine.getTaskService().complete(legal.getId());
            assertNotNull(task(engine, instance.getId(), "inclusive-after"));
        } finally {
            engine.close();
        }
    }

    @Test
    void eventBasedGatewaySelectsTriggeredReceiveBranchAndCancelsTimerBranch() {
        ProcessEngine engine = buildEngine(Map.of());
        try {
            deploy(engine, "event-based.bpmn20.xml", eventBasedGatewayBpmn());
            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance instance = runtimeService
                    .startProcessInstanceByKey("event_based_gateway");
            Execution waiting = runtimeService.createExecutionQuery()
                    .processInstanceId(instance.getId())
                    .activityId("callback-received")
                    .singleResult();
            assertNotNull(waiting);
            assertEquals(
                    1,
                    engine.getManagementService().createTimerJobQuery()
                            .processInstanceId(instance.getId())
                            .count());

            runtimeService.messageEventReceived("callback-message", waiting.getId());

            assertNotNull(task(engine, instance.getId(), "callback-path"));
            assertNull(task(engine, instance.getId(), "timeout-path"));
            assertEquals(
                    0,
                    engine.getManagementService().createTimerJobQuery()
                            .processInstanceId(instance.getId())
                            .count());
        } finally {
            engine.close();
        }
    }

    @Test
    void callActivityMapsInputBusinessKeyAndOutputVariables() {
        ProcessEngine engine = buildEngine(Map.of());
        try {
            deploy(engine, "child.bpmn20.xml", childProcessBpmn());
            String runtimeParent = new ProcessBpmnPublishSanitizer(new ObjectMapper())
                    .sanitize(parentCallActivityBpmn(), "parent_call");
            deploy(engine, "parent.bpmn20.xml", runtimeParent);
            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance parent = runtimeService.startProcessInstanceByKey(
                    "parent_call",
                    "PARENT-BUSINESS-KEY",
                    Map.of("amount", 1200, "businessKey", "CHILD-BUSINESS-KEY"));
            ProcessInstance child = runtimeService.createProcessInstanceQuery()
                    .superProcessInstanceId(parent.getId())
                    .singleResult();
            assertNotNull(child);
            assertEquals("CHILD-BUSINESS-KEY", child.getBusinessKey());
            assertEquals(1200, runtimeService.getVariable(child.getId(), "childAmount"));

            Task childTask = task(engine, child.getId(), "child-review");
            engine.getTaskService().complete(
                    childTask.getId(),
                    Map.of("subProcessResult", "approved"));

            assertNotNull(task(engine, parent.getId(), "after-call"));
            assertEquals("approved", runtimeService.getVariable(parent.getId(), "parentResult"));
        } finally {
            engine.close();
        }
    }

    @Test
    void serviceTaskSupportsClassExpressionAndDelegateExpressionModes() {
        Map<Object, Object> beans = new HashMap<>();
        beans.put("demoExpressionService", new DemoExpressionService());
        beans.put("runtimeDelegate", (JavaDelegate) execution ->
                execution.setVariable("delegateExpressionExecuted", true));
        ProcessEngine engine = buildEngine(beans);
        try {
            deploy(engine, "service-task-modes.bpmn20.xml", serviceTaskModesBpmn());
            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("service_task_modes");

            assertNotNull(task(engine, instance.getId(), "after-services"));
            assertEquals(
                    true,
                    engine.getRuntimeService().getVariable(
                            instance.getId(),
                            "classDelegateExecuted"));
            assertEquals(
                    "completed",
                    engine.getRuntimeService().getVariable(
                            instance.getId(),
                            "expressionResult"));
            assertEquals(
                    true,
                    engine.getRuntimeService().getVariable(
                            instance.getId(),
                            "delegateExpressionExecuted"));
        } finally {
            engine.close();
        }
    }

    private ProcessEngine buildEngine(Map<Object, Object> beans) {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setExpressionManager(new DefaultExpressionManager(new HashMap<>(beans)));
        configuration.setJdbcUrl(
                "jdbc:h2:mem:core_nodes_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        return configuration.buildProcessEngine();
    }

    private void deploy(ProcessEngine engine, String resourceName, String bpmnXml) {
        engine.getRepositoryService()
                .createDeployment()
                .addString(resourceName, bpmnXml)
                .deploy();
    }

    private Task task(ProcessEngine engine, String processInstanceId, String taskDefinitionKey) {
        return engine.getTaskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
    }

    private String definitions(String process) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    targetNamespace="http://workflow.test/process">
                %s
                </bpmn:definitions>
                """.formatted(process);
    }

    private String manualAndSubProcessBpmn() {
        return definitions("""
                  <bpmn:process id="manual_subprocess" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:manualTask id="manual-check" />
                    <bpmn:subProcess id="embedded-subprocess">
                      <bpmn:startEvent id="sub-start" />
                      <bpmn:userTask id="inside-subprocess" />
                      <bpmn:endEvent id="sub-end" />
                      <bpmn:sequenceFlow id="sub-flow-1" sourceRef="sub-start" targetRef="inside-subprocess" />
                      <bpmn:sequenceFlow id="sub-flow-2" sourceRef="inside-subprocess" targetRef="sub-end" />
                    </bpmn:subProcess>
                    <bpmn:userTask id="after-subprocess" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="manual-check" />
                    <bpmn:sequenceFlow id="flow-2" sourceRef="manual-check" targetRef="embedded-subprocess" />
                    <bpmn:sequenceFlow id="flow-3" sourceRef="embedded-subprocess" targetRef="after-subprocess" />
                    <bpmn:sequenceFlow id="flow-4" sourceRef="after-subprocess" targetRef="end" />
                  </bpmn:process>
                """);
    }

    private String exclusiveGatewayBpmn() {
        return definitions("""
                  <bpmn:process id="exclusive_gateway" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:exclusiveGateway id="route" default="to-normal" />
                    <bpmn:userTask id="high-review" />
                    <bpmn:userTask id="normal-review" />
                    <bpmn:endEvent id="end-high" />
                    <bpmn:endEvent id="end-normal" />
                    <bpmn:sequenceFlow id="to-route" sourceRef="start" targetRef="route" />
                    <bpmn:sequenceFlow id="to-high" sourceRef="route" targetRef="high-review">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${amount >= 100}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="to-normal" sourceRef="route" targetRef="normal-review" />
                    <bpmn:sequenceFlow id="high-end" sourceRef="high-review" targetRef="end-high" />
                    <bpmn:sequenceFlow id="normal-end" sourceRef="normal-review" targetRef="end-normal" />
                  </bpmn:process>
                """);
    }

    private String parallelGatewayBpmn() {
        return definitions("""
                  <bpmn:process id="parallel_gateway" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:parallelGateway id="split" />
                    <bpmn:userTask id="parallel-a" />
                    <bpmn:userTask id="parallel-b" />
                    <bpmn:parallelGateway id="join" />
                    <bpmn:userTask id="parallel-after" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="split" />
                    <bpmn:sequenceFlow id="flow-a" sourceRef="split" targetRef="parallel-a" />
                    <bpmn:sequenceFlow id="flow-b" sourceRef="split" targetRef="parallel-b" />
                    <bpmn:sequenceFlow id="flow-a-join" sourceRef="parallel-a" targetRef="join" />
                    <bpmn:sequenceFlow id="flow-b-join" sourceRef="parallel-b" targetRef="join" />
                    <bpmn:sequenceFlow id="flow-after" sourceRef="join" targetRef="parallel-after" />
                    <bpmn:sequenceFlow id="flow-end" sourceRef="parallel-after" targetRef="end" />
                  </bpmn:process>
                """);
    }

    private String inclusiveGatewayBpmn() {
        return definitions("""
                  <bpmn:process id="inclusive_gateway" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:inclusiveGateway id="split" />
                    <bpmn:userTask id="finance-review" />
                    <bpmn:userTask id="legal-review" />
                    <bpmn:inclusiveGateway id="join" />
                    <bpmn:userTask id="inclusive-after" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="split" />
                    <bpmn:sequenceFlow id="flow-finance" sourceRef="split" targetRef="finance-review">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${needsFinance}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="flow-legal" sourceRef="split" targetRef="legal-review">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${needsLegal}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="flow-finance-join" sourceRef="finance-review" targetRef="join" />
                    <bpmn:sequenceFlow id="flow-legal-join" sourceRef="legal-review" targetRef="join" />
                    <bpmn:sequenceFlow id="flow-after" sourceRef="join" targetRef="inclusive-after" />
                    <bpmn:sequenceFlow id="flow-end" sourceRef="inclusive-after" targetRef="end" />
                  </bpmn:process>
                """);
    }

    private String eventBasedGatewayBpmn() {
        return definitions("""
                  <bpmn:message id="callbackMessage" name="callback-message" />
                  <bpmn:process id="event_based_gateway" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:eventBasedGateway id="wait-for-event" />
                    <bpmn:intermediateCatchEvent id="callback-received">
                      <bpmn:messageEventDefinition messageRef="callbackMessage" />
                    </bpmn:intermediateCatchEvent>
                    <bpmn:intermediateCatchEvent id="callback-timeout">
                      <bpmn:timerEventDefinition>
                        <bpmn:timeDuration>PT1H</bpmn:timeDuration>
                      </bpmn:timerEventDefinition>
                    </bpmn:intermediateCatchEvent>
                    <bpmn:userTask id="callback-path" />
                    <bpmn:userTask id="timeout-path" />
                    <bpmn:endEvent id="callback-end" />
                    <bpmn:endEvent id="timeout-end" />
                    <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="wait-for-event" />
                    <bpmn:sequenceFlow id="flow-callback" sourceRef="wait-for-event" targetRef="callback-received" />
                    <bpmn:sequenceFlow id="flow-timeout" sourceRef="wait-for-event" targetRef="callback-timeout" />
                    <bpmn:sequenceFlow id="flow-callback-path" sourceRef="callback-received" targetRef="callback-path" />
                    <bpmn:sequenceFlow id="flow-timeout-path" sourceRef="callback-timeout" targetRef="timeout-path" />
                    <bpmn:sequenceFlow id="flow-callback-end" sourceRef="callback-path" targetRef="callback-end" />
                    <bpmn:sequenceFlow id="flow-timeout-end" sourceRef="timeout-path" targetRef="timeout-end" />
                  </bpmn:process>
                """);
    }

    private String childProcessBpmn() {
        return definitions("""
                  <bpmn:process id="child_process" isExecutable="true">
                    <bpmn:startEvent id="child-start" />
                    <bpmn:userTask id="child-review" />
                    <bpmn:endEvent id="child-end" />
                    <bpmn:sequenceFlow id="child-flow-1" sourceRef="child-start" targetRef="child-review" />
                    <bpmn:sequenceFlow id="child-flow-2" sourceRef="child-review" targetRef="child-end" />
                  </bpmn:process>
                """);
    }

    private String parentCallActivityBpmn() {
        return definitions("""
                  <bpmn:process id="draft_parent" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:callActivity id="call-child">
                      <bpmn:extensionElements>
                        <flowable:properties>
                          <flowable:property name="callConfig"
                            value="{&quot;calledElement&quot;:&quot;child_process&quot;,&quot;callActivityType&quot;:&quot;bpmn&quot;,&quot;inputParameters&quot;:&quot;{\\&quot;childAmount\\&quot;:\\&quot;${amount}\\&quot;}&quot;,&quot;outputParameters&quot;:&quot;{\\&quot;parentResult\\&quot;:\\&quot;${subProcessResult}\\&quot;}&quot;,&quot;businessKey&quot;:&quot;${businessKey}&quot;}" />
                        </flowable:properties>
                      </bpmn:extensionElements>
                    </bpmn:callActivity>
                    <bpmn:userTask id="after-call" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="call-child" />
                    <bpmn:sequenceFlow id="flow-2" sourceRef="call-child" targetRef="after-call" />
                    <bpmn:sequenceFlow id="flow-3" sourceRef="after-call" targetRef="end" />
                  </bpmn:process>
                """);
    }

    private String serviceTaskModesBpmn() {
        return definitions("""
                  <bpmn:process id="service_task_modes" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:serviceTask id="class-task"
                        flowable:class="com.workflow.process.definition.CoreBpmnNodeRuntimeTest$RuntimeClassDelegate" />
                    <bpmn:serviceTask id="expression-task"
                        flowable:expression="${demoExpressionService.execute('approved')}"
                        flowable:resultVariableName="expressionResult" />
                    <bpmn:serviceTask id="delegate-task"
                        flowable:delegateExpression="${runtimeDelegate}" />
                    <bpmn:userTask id="after-services" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="class-task" />
                    <bpmn:sequenceFlow id="flow-2" sourceRef="class-task" targetRef="expression-task" />
                    <bpmn:sequenceFlow id="flow-3" sourceRef="expression-task" targetRef="delegate-task" />
                    <bpmn:sequenceFlow id="flow-4" sourceRef="delegate-task" targetRef="after-services" />
                    <bpmn:sequenceFlow id="flow-5" sourceRef="after-services" targetRef="end" />
                  </bpmn:process>
                """);
    }

    public static class RuntimeClassDelegate implements JavaDelegate {
        @Override
        public void execute(DelegateExecution execution) {
            execution.setVariable("classDelegateExecuted", true);
        }
    }

}
