package com.workflow.process.definition;

import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.task.infrastructure.flowable.ReceiveTaskTimeoutDelegate;
import org.flowable.common.engine.impl.el.DefaultExpressionManager;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接收任务超时运行时集成测试。
 */
class ReceiveTaskTimeoutRuntimeTest {

    @Test
    void continueTimeoutExecutesTimerAndMovesToOriginalTarget() {
        ProcessBpmnPublishSanitizer sanitizer =
                new ProcessBpmnPublishSanitizer(new ObjectMapper());
        String runtimeXml = sanitizer.sanitize(sourceBpmn(), "receive_timeout_runtime");
        ProcessEngine engine = buildEngine();
        try {
            engine.getRepositoryService()
                    .createDeployment()
                    .addString("receive-timeout.bpmn20.xml", runtimeXml)
                    .deploy();
            RuntimeService runtimeService = engine.getRuntimeService();
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    "receive_timeout_runtime");

            assertNotNull(runtimeService.createExecutionQuery()
                    .processInstanceId(instance.getId())
                    .activityId("receive")
                    .singleResult());
            ManagementService managementService = engine.getManagementService();
            Job timer = managementService.createTimerJobQuery()
                    .processInstanceId(instance.getId())
                    .singleResult();
            assertNotNull(timer);

            Job executable = managementService.moveTimerToExecutableJob(timer.getId());
            managementService.executeJob(executable.getId());

            assertNotNull(engine.getTaskService()
                    .createTaskQuery()
                    .processInstanceId(instance.getId())
                    .taskDefinitionKey("after-timeout")
                    .singleResult());
            assertTrue((Boolean) runtimeService.getVariable(
                    instance.getId(),
                    "receiveTaskTimedOut"));
            assertEquals(
                    "receive",
                    runtimeService.getVariable(
                            instance.getId(),
                            "receiveTaskTimeoutActivityId"));
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
                "receiveTaskTimeoutDelegate",
                new ReceiveTaskTimeoutDelegate());
        configuration.setExpressionManager(
                new DefaultExpressionManager(beans));
        configuration.setJdbcUrl(
                "jdbc:h2:mem:receive_timeout_"
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
                    <bpmn:receiveTask id="receive">
                      <bpmn:extensionElements>
                        <flowable:properties>
                          <flowable:property name="receiveConfig"
                            value="{&quot;messageRef&quot;:&quot;paymentCallback&quot;,&quot;hasTimeout&quot;:true,&quot;timeout&quot;:1,&quot;timeoutUnit&quot;:&quot;MINUTE&quot;,&quot;timeoutAction&quot;:&quot;continue&quot;}" />
                        </flowable:properties>
                      </bpmn:extensionElements>
                    </bpmn:receiveTask>
                    <bpmn:userTask id="after-timeout" name="超时后确认" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="flow-start" sourceRef="start" targetRef="receive" />
                    <bpmn:sequenceFlow id="flow-receive" sourceRef="receive" targetRef="after-timeout" />
                    <bpmn:sequenceFlow id="flow-end" sourceRef="after-timeout" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
