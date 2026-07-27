package com.workflow.process.definition;

import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredScriptTaskDelegate;
import org.flowable.common.engine.impl.el.DefaultExpressionManager;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 配置化 Groovy 脚本任务运行时集成测试。
 */
class ConfiguredScriptTaskRuntimeTest {

    @Test
    void groovyScriptPublishesAndExecutesWithResultAndAutoStoredVariables() {
        ProcessBpmnPublishSanitizer sanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String runtimeXml = sanitizer.sanitize(sourceBpmn(), "script_runtime");
        ProcessEngine engine = buildEngine();
        try {
            engine.getRepositoryService()
                    .createDeployment()
                    .addString("script-runtime.bpmn20.xml", runtimeXml)
                    .deploy();
            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    "script_runtime",
                    Map.of("price", 10));

            assertNotNull(engine.getTaskService()
                    .createTaskQuery()
                    .processInstanceId(instance.getId())
                    .taskDefinitionKey("after-script")
                    .singleResult());
            assertEquals(
                    20,
                    ((Number) runtimeService.getVariable(
                            instance.getId(),
                            "scriptResult")).intValue());
            assertEquals(
                    20,
                    ((Number) runtimeService.getVariable(
                            instance.getId(),
                            "computed")).intValue());
            assertEquals(
                    "ok",
                    runtimeService.getVariable(
                            instance.getId(),
                            "scriptSideEffect"));
        } finally {
            engine.close();
        }
    }

    private ProcessEngine buildEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        Map<Object, Object> beans = new HashMap<>();
        beans.put(
                "configuredScriptTaskDelegate",
                new ConfiguredScriptTaskDelegate(new ObjectMapper()));
        configuration.setExpressionManager(
                new DefaultExpressionManager(beans));
        configuration.setJdbcUrl(
                "jdbc:h2:mem:script_runtime_"
                        + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        return configuration.buildProcessEngine();
    }

    private String sourceBpmn() {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn"
                    targetNamespace="http://workflow.test/process">
                  <bpmn:process id="draft_process" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:scriptTask id="script">
                      <bpmn:extensionElements>
                        <flowable:properties>
                          <flowable:property name="scriptConfig"
                            value="{&quot;scriptFormat&quot;:&quot;groovy&quot;,&quot;script&quot;:&quot;def price = execution.getVariable('price')\\ncomputed = price * 2\\nexecution.setVariable('scriptSideEffect', 'ok')\\ncomputed&quot;,&quot;resultVariable&quot;:&quot;scriptResult&quot;,&quot;autoStoreVariables&quot;:true}" />
                        </flowable:properties>
                      </bpmn:extensionElements>
                    </bpmn:scriptTask>
                    <bpmn:userTask id="after-script" name="脚本后确认" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-start" sourceRef="start" targetRef="script" />
                    <bpmn:sequenceFlow id="flow-script" sourceRef="script" targetRef="after-script" />
                    <bpmn:sequenceFlow id="flow-end" sourceRef="after-script" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
