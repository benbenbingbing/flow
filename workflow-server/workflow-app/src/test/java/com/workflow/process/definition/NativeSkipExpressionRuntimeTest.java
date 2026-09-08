package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import com.workflow.process.task.application.WorkflowAutoSkipService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flowable 原生用户任务 skipExpression 运行时集成测试。 */
class NativeSkipExpressionRuntimeTest {

    @Test
    void inFlightCompatibilityListenerEnablesSkipBeforeTaskCreation() {
        ProcessEngine engine = buildEngine();
        try {
            engine.getRuntimeService().addEventListener(
                    new WorkflowAutoSkipService(
                            engine.getRuntimeService(),
                            engine.getRepositoryService()));
            deploy(engine, "conditional-skip.bpmn20.xml",
                    conditionalSkipBpmn());
            Map<String, Object> variables = new HashMap<>();
            variables.put("shouldSkip", true);
            // 模拟升级前已在途实例可能遗留的优先级更高的旧开关。
            variables.put(
                    WorkflowReservedVariables
                            .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE,
                    false);

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "conditional_skip", variables);

            assertNull(task(engine, instance.getId(), "conditional-review"));
            assertNotNull(task(engine, instance.getId(), "after-review"));
            assertEquals(
                    true,
                    engine.getRuntimeService().getVariable(
                            instance.getId(),
                            WorkflowReservedVariables
                                    .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(), "approved"));
        } finally {
            engine.close();
        }
    }

    @Test
    void falseConditionKeepsTheFallbackUserTask() {
        ProcessEngine engine = buildEngine();
        try {
            engine.getRuntimeService().addEventListener(
                    new WorkflowAutoSkipService(
                            engine.getRuntimeService(),
                            engine.getRepositoryService()));
            deploy(engine, "conditional-fallback.bpmn20.xml",
                    conditionalSkipBpmn());

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "conditional_skip",
                            Map.of("shouldSkip", false));

            assertNotNull(task(
                    engine, instance.getId(), "conditional-review"));
            assertNull(task(engine, instance.getId(), "after-review"));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(), "approved"));
        } finally {
            engine.close();
        }
    }

    @Test
    void inFlightLegacyExpressionReceivesItsCompatibilityVariable() {
        ProcessEngine engine = buildEngine();
        try {
            engine.getRuntimeService().addEventListener(
                    new WorkflowAutoSkipService(
                            engine.getRuntimeService(),
                            engine.getRepositoryService()));
            deploy(engine, "legacy-in-flight.bpmn20.xml",
                    legacyDeployedExpressionBpmn());

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("legacy_in_flight");

            assertNull(task(engine, instance.getId(), "legacy-review"));
            assertNotNull(task(engine, instance.getId(), "after-review"));
            assertEquals(
                    true,
                    engine.getRuntimeService().getVariable(
                            instance.getId(),
                            WorkflowReservedVariables
                                    .LEGACY_SKIP_NODE_ENABLED_VARIABLE));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(), "approved"));
        } finally {
            engine.close();
        }
    }

    @Test
    void legacyAlwaysMarkerPublishesAndRunsAsNativeTrueSkip() {
        ProcessEngine engine = buildEngine();
        try {
            String runtimeXml = new ProcessBpmnPublishSanitizer(
                    new ObjectMapper()).sanitize(
                    legacyAlwaysSkipBpmn(), "always_skip");
            assertTrue(runtimeXml.contains(
                    "flowable:skipExpression=\"${true}\""));
            deploy(engine, "always-skip.bpmn20.xml", runtimeXml);
            Map<String, Object> variables = new HashMap<>();
            WorkflowReservedVariables.enableNativeSkipExpressions(
                    variables);

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "always_skip", variables);

            assertNull(task(engine, instance.getId(), "always-review"));
            assertNotNull(task(engine, instance.getId(), "after-review"));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(), "approved"));
        } finally {
            engine.close();
        }
    }

    @Test
    void historicalMethodExpressionIsDisabledBeforeItCanExecute() {
        ProcessEngine engine = buildEngine();
        try {
            engine.getRuntimeService().addEventListener(
                    new WorkflowAutoSkipService(
                            engine.getRuntimeService(),
                            engine.getRepositoryService()));
            deploy(engine, "unsafe-historical-skip.bpmn20.xml",
                    unsafeHistoricalSkipBpmn());
            Map<String, Object> variables = new HashMap<>();
            variables.put(
                    WorkflowReservedVariables
                            .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE,
                    true);

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "unsafe_historical_skip", variables);

            assertNotNull(task(
                    engine, instance.getId(), "unsafe-review"));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(),
                    WorkflowReservedVariables
                            .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(), "approved"));
        } finally {
            engine.close();
        }
    }

    @Test
    void historicalMultiInstanceCannotEnableUnsafeSkipWithLocalVariable() {
        ProcessEngine engine = buildEngine();
        try {
            engine.getRuntimeService().addEventListener(
                    new WorkflowAutoSkipService(
                            engine.getRuntimeService(),
                            engine.getRepositoryService()));
            deploy(engine, "unsafe-mi-historical-skip.bpmn20.xml",
                    unsafeHistoricalMultiInstanceSkipBpmn());
            Map<String, Object> variables = new HashMap<>();
            variables.put("maliciousSwitches", List.of(true));
            variables.put(
                    WorkflowReservedVariables
                            .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE,
                    true);

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "unsafe_historical_mi_skip", variables);

            Task reviewTask = task(
                    engine, instance.getId(), "unsafe-mi-review");
            assertNotNull(reviewTask);
            assertEquals(
                    false,
                    engine.getRuntimeService().getVariableLocal(
                            reviewTask.getExecutionId(),
                            WorkflowReservedVariables
                                    .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(),
                    WorkflowReservedVariables
                            .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
        } finally {
            engine.close();
        }
    }

    @Test
    void historicalClassListenerLoadsAsNoOpCompatibilityShim() {
        ProcessEngine engine = buildEngine();
        try {
            deploy(engine, "legacy-auto-complete-listener.bpmn20.xml",
                    legacyAutoCompleteListenerBpmn());

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("legacy_listener_compat");

            assertNotNull(task(
                    engine, instance.getId(), "legacy-listener-review"));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(), "approved"));
            assertNull(engine.getRuntimeService().getVariable(
                    instance.getId(),
                    "skipReason_legacy-listener-review"));
        } finally {
            engine.close();
        }
    }

    private ProcessEngine buildEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl(
                "jdbc:h2:mem:native_skip_" + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        return configuration.buildProcessEngine();
    }

    private void deploy(
            ProcessEngine engine,
            String resourceName,
            String bpmnXml) {
        engine.getRepositoryService()
                .createDeployment()
                .addString(resourceName, bpmnXml)
                .deploy();
    }

    private Task task(
            ProcessEngine engine,
            String processInstanceId,
            String taskDefinitionKey) {
        return engine.getTaskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
    }

    private String conditionalSkipBpmn() {
        return definitions("""
                <bpmn:process id="conditional_skip" isExecutable="true">
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="conditional-review"
                    flowable:assignee="reviewer"
                    flowable:skipExpression="${shouldSkip}" />
                  <bpmn:userTask id="after-review"
                    flowable:assignee="reviewer" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="conditional-review" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="conditional-review" targetRef="after-review" />
                  <bpmn:sequenceFlow id="flow-3" sourceRef="after-review" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String legacyAlwaysSkipBpmn() {
        return definitions("""
                <bpmn:process id="legacy_always_skip" isExecutable="true">
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="always-review">
                    <bpmn:extensionElements>
                      <flowable:properties>
                        <flowable:property name="skipNode" value="true" />
                      </flowable:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                  <bpmn:userTask id="after-review"
                    flowable:assignee="reviewer" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="always-review" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="always-review" targetRef="after-review" />
                  <bpmn:sequenceFlow id="flow-3" sourceRef="after-review" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String legacyDeployedExpressionBpmn() {
        return definitions("""
                <bpmn:process id="legacy_in_flight" isExecutable="true">
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="legacy-review"
                    flowable:skipExpression="${skipNodeEnabled}" />
                  <bpmn:userTask id="after-review"
                    flowable:assignee="reviewer" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="legacy-review" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="legacy-review" targetRef="after-review" />
                  <bpmn:sequenceFlow id="flow-3" sourceRef="after-review" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String unsafeHistoricalSkipBpmn() {
        return definitions("""
                <bpmn:process id="unsafe_historical_skip" isExecutable="true">
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="unsafe-review"
                    flowable:assignee="reviewer"
                    flowable:skipExpression="${dangerousService.execute()}" />
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="unsafe-review" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="unsafe-review" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String unsafeHistoricalMultiInstanceSkipBpmn() {
        return definitions("""
                <bpmn:process id="unsafe_historical_mi_skip" isExecutable="true">
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="unsafe-mi-review"
                    flowable:assignee="reviewer"
                    flowable:skipExpression="${dangerousService.execute()}">
                    <bpmn:multiInstanceLoopCharacteristics
                      flowable:collection="${maliciousSwitches}"
                      flowable:elementVariable="_ACTIVITI_SKIP_EXPRESSION_ENABLED" />
                  </bpmn:userTask>
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="unsafe-mi-review" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="unsafe-mi-review" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String legacyAutoCompleteListenerBpmn() {
        return definitions("""
                <bpmn:process id="legacy_listener_compat" isExecutable="true">
                  <bpmn:startEvent id="start" />
                  <bpmn:userTask id="legacy-listener-review"
                    flowable:assignee="reviewer">
                    <bpmn:extensionElements>
                      <flowable:taskListener event="create"
                        class="com.workflow.process.task.infrastructure.flowable.AutoCompleteTaskListener" />
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                  <bpmn:endEvent id="end" />
                  <bpmn:sequenceFlow id="flow-1" sourceRef="start" targetRef="legacy-listener-review" />
                  <bpmn:sequenceFlow id="flow-2" sourceRef="legacy-listener-review" targetRef="end" />
                </bpmn:process>
                """);
    }

    private String definitions(String process) {
        return """
                <bpmn:definitions
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow.test/process">
                  %s
                </bpmn:definitions>
                """.formatted(process);
    }
}
