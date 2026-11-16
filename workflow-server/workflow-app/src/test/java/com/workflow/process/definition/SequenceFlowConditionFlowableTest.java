package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.condition.model.SequenceFlowConditionRequest;
import com.workflow.contracts.process.condition.spi.SequenceFlowConditionProvider;
import com.workflow.process.condition.application.SequenceFlowConditionBpmnCompiler;
import com.workflow.process.condition.application.SequenceFlowConditionConfig;
import com.workflow.process.condition.application.SequenceFlowConditionEvaluator;
import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;
import com.workflow.process.task.infrastructure.flowable.FlowableConditionEvaluator;
import org.flowable.engine.impl.el.ProcessExpressionManager;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 用真实 Flowable 证明接口选路、默认流、异常回滚，以及发布和预览的边界。 */
class SequenceFlowConditionFlowableTest {
    private final ObjectMapper json = new ObjectMapper();
    private ProcessEngine currentEngine;

    @AfterEach
    void closeEngine() {
        if (currentEngine != null) currentEngine.close();
    }
    private final AtomicReference<SequenceFlowConditionRequest> request = new AtomicReference<>();
    private final SequenceFlowConditionProvider provider = new SequenceFlowConditionProvider() {
        public String getCode() { return "testDecision"; }
        public String getDisplayName() { return "测试分支判断"; }
        public boolean evaluate(SequenceFlowConditionRequest value) {
            request.set(value);
            if (Boolean.TRUE.equals(value.variables().get("fail"))) throw new IllegalStateException("业务查询失败");
            assertThrows(UnsupportedOperationException.class, () -> value.extraParams().put("injected", true));
            return Boolean.TRUE.equals(value.variables().get("allow"));
        }
    };

    @Test
    void trueSelectsBranchAndFalseSelectsDefaultUsingPublishedContext() {
        var evaluator = new SequenceFlowConditionEvaluator(List.of(provider), json);
        {
            ProcessEngine engine = engine(evaluator);
            deploy(engine, sanitize(xml("exclusiveGateway", "testDecision", "{}"), evaluator));
            var allowed = engine.getRuntimeService().startProcessInstanceByKey("condition_test",
                    Map.of("allow", true, "entityCode", "expense", "entityDataId", "r1", "_wf_secret", "hidden"));
            assertNotNull(task(engine, allowed.getId(), "accepted"));
            assertNull(task(engine, allowed.getId(), "fallback"));
            assertEquals("route_yes", request.get().sequenceFlowId());
            assertEquals("expense", request.get().entityCode());
            assertEquals("r1", request.get().entityDataId());
            assertEquals(allowed.getId(), request.get().processInstanceId());
            assertFalse(request.get().variables().containsKey("_wf_secret"));
            var rejected = engine.getRuntimeService().startProcessInstanceByKey("condition_test", Map.of("allow", false));
            assertNull(task(engine, rejected.getId(), "accepted"));
            assertNotNull(task(engine, rejected.getId(), "fallback"));
        }
    }

    @Test
    void inclusiveGatewayEvaluatesEachBranchWhileDefaultIsOnlyFallback() {
        var evaluator = new SequenceFlowConditionEvaluator(List.of(provider), json);
        {
            ProcessEngine engine = engine(evaluator);
            String source = xml("inclusiveGateway", "testDecision", "{}").replace(
                    "<bpmn:sequenceFlow id=\"route_default\"", "<bpmn:sequenceFlow id=\"also_yes\" sourceRef=\"route\" targetRef=\"accepted2\">"
                            + "<bpmn:conditionExpression>${allow == true}</bpmn:conditionExpression></bpmn:sequenceFlow>"
                            + "<bpmn:userTask id=\"accepted2\"/><bpmn:sequenceFlow id=\"done2\" sourceRef=\"accepted2\" targetRef=\"end\"/>"
                            + "<bpmn:sequenceFlow id=\"route_default\"");
            deploy(engine, sanitize(source, evaluator));
            var instance = engine.getRuntimeService().startProcessInstanceByKey("condition_test", Map.of("allow", true));
            assertNotNull(task(engine, instance.getId(), "accepted"));
            assertNotNull(task(engine, instance.getId(), "accepted2"));
            assertNull(task(engine, instance.getId(), "fallback"));
        }
    }

    @Test
    void providerFailureRollsBackTaskCompletionInsteadOfSelectingDefault() {
        var evaluator = new SequenceFlowConditionEvaluator(List.of(provider), json);
        {
            ProcessEngine engine = engine(evaluator);
            String source = xml("exclusiveGateway", "testDecision", "{}")
                    .replace("targetRef=\"route\"/>", "targetRef=\"review\"/><bpmn:userTask id=\"review\"/>"
                            + "<bpmn:sequenceFlow id=\"review_route\" sourceRef=\"review\" targetRef=\"route\"/>");
            deploy(engine, sanitize(source, evaluator));
            var instance = engine.getRuntimeService().startProcessInstanceByKey("condition_test");
            Task review = task(engine, instance.getId(), "review");
            assertThrows(RuntimeException.class, () -> engine.getTaskService().complete(review.getId(), Map.of("fail", true)));
            assertNotNull(task(engine, instance.getId(), "review"));
            assertNull(task(engine, instance.getId(), "fallback"));
            assertNull(engine.getRuntimeService().getVariable(instance.getId(), "fail"));
            engine.getTaskService().complete(review.getId(), Map.of("allow", true));
            assertNotNull(task(engine, instance.getId(), "accepted"));
        }
    }

    @Test
    void publishRejectsMissingDuplicateProvidersInvalidParamsAndIgnoredConditions() {
        var evaluator = new SequenceFlowConditionEvaluator(List.of(provider), json);
        assertThrows(IllegalArgumentException.class, () -> sanitize(xml("exclusiveGateway", "missing", "{}"), evaluator));
        assertThrows(IllegalArgumentException.class, () -> sanitize(xml("parallelGateway", "testDecision", "{}"), evaluator));
        assertThrows(IllegalArgumentException.class, () -> sanitize(xml("exclusiveGateway", "testDecision", "[]"), evaluator));
        assertThrows(IllegalArgumentException.class, () -> sanitize(
                xml("exclusiveGateway", "testDecision", "{}").replace("default=\"route_default\"", "default=\"route_yes\""), evaluator));
        assertThrows(IllegalArgumentException.class, () -> sanitize(xml("exclusiveGateway", "testDecision", "{}"),
                new SequenceFlowConditionEvaluator(List.of(provider, provider), json)));
    }

    @Test
    void arbitraryMethodExpressionsStayRejectedAndPreviewDoesNotInvokeProvider() {
        var evaluator = new SequenceFlowConditionEvaluator(List.of(provider), json);
        String source = xml("exclusiveGateway", "testDecision", "{}");
        assertThrows(IllegalArgumentException.class, () -> new ProcessBpmnPublishSanitizer(json).sanitize(
                source.replace("<bpmn:extensionElements>", "<bpmn:conditionExpression>${evil.run()}</bpmn:conditionExpression><bpmn:extensionElements>")
                        .replace("sequenceFlowCondition", "otherProperty"), "condition_test"));
        {
            ProcessEngine engine = engine(evaluator);
            assertThrows(FlowableConditionEvaluator.UnsafePreviewExpressionException.class,
                    () -> new FlowableConditionEvaluator(engine).evaluate(SequenceFlowConditionConfig.expression("route_yes"), Map.of()));
            assertNull(request.get());
        }
    }

    private String sanitize(String xml, SequenceFlowConditionEvaluator evaluator) {
        var sanitizer = new ProcessBpmnPublishSanitizer(json);
        ReflectionTestUtils.setField(sanitizer, "sequenceFlowConditionBpmnCompiler", new SequenceFlowConditionBpmnCompiler(evaluator, json));
        return sanitizer.sanitize(xml, "condition_test");
    }

    private ProcessEngine engine(SequenceFlowConditionEvaluator evaluator) {
        var configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        configuration.setExpressionManager(new ProcessExpressionManager(new HashMap<>(Map.of("sequenceFlowConditionEvaluator", evaluator))));
        configuration.setJdbcUrl("jdbc:h2:mem:sequence_condition_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        currentEngine = configuration.buildProcessEngine();
        return currentEngine;
    }

    private void deploy(ProcessEngine engine, String xml) {
        engine.getRepositoryService().createDeployment().addString("condition.bpmn20.xml", xml).deploy();
    }

    private Task task(ProcessEngine engine, String instance, String node) {
        return engine.getTaskService().createTaskQuery().processInstanceId(instance).taskDefinitionKey(node).singleResult();
    }

    private String xml(String gateway, String code, String params) {
        String config = ("{\"providerCode\":\"" + code + "\",\"extraParams\":" + params + "}").replace("\"", "&quot;");
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" targetNamespace="test">
                  <bpmn:process id="condition_test" isExecutable="true">
                    <bpmn:startEvent id="start"/><bpmn:%s id="route" default="route_default"/>
                    <bpmn:userTask id="accepted"/><bpmn:userTask id="fallback"/><bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="start_route" sourceRef="start" targetRef="route"/>
                    <bpmn:sequenceFlow id="route_yes" sourceRef="route" targetRef="accepted">
                      <bpmn:extensionElements><flowable:properties>
                        <flowable:property name="sequenceFlowCondition" value="%s"/>
                      </flowable:properties></bpmn:extensionElements>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="route_default" sourceRef="route" targetRef="fallback"/>
                    <bpmn:sequenceFlow id="done_yes" sourceRef="accepted" targetRef="end"/>
                    <bpmn:sequenceFlow id="done_default" sourceRef="fallback" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(gateway, config);
    }
}
