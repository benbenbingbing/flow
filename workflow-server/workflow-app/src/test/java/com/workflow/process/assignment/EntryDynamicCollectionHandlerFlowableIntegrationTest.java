package com.workflow.process.assignment;

import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.delegate.FlowableCollectionHandler;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 Flowable 7.2 引擎验证节点进入期 collection handler 的安全字面量契约。
 */
class EntryDynamicCollectionHandlerFlowableIntegrationTest {

    private static final String COLLECTION_LITERAL =
            "__wfEntryDynamicCollectionSeed";
    private static final String ORIGINAL_COLLECTION_VARIABLE = "reviewers";
    private static final List<String> FORGED_REVIEWERS =
            List.of("forged-reviewer");
    private static final List<String> AUTHORITATIVE_REVIEWERS =
            List.of("authoritative-alice", "authoritative-bob");

    @Test
    void safeLiteralInvokesHandlerAndAuthoritativeUsersCreateTasks() {
        RecordingCollectionHandler handler =
                new RecordingCollectionHandler(AUTHORITATIVE_REVIEWERS);
        ProcessEngine engine = buildEngine(handler);
        try {
            deploy(engine);

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey(
                            "entry_dynamic_handler",
                            Map.of(ORIGINAL_COLLECTION_VARIABLE,
                                    FORGED_REVIEWERS));

            List<Task> tasks = assertAuthoritativeTasks(engine, instance);
            assertFalse(tasks.stream()
                    .anyMatch(task -> FORGED_REVIEWERS.contains(
                            task.getAssignee())));

            assertSafeLiteralInvocation(handler);
            assertTrue(handler.reviewersVariablePresence().stream()
                    .allMatch(Boolean::booleanValue));
            assertTrue(handler.reviewersSeen().stream()
                    .allMatch(FORGED_REVIEWERS::equals));

            UserTask deployedTask = (UserTask) engine
                    .getRepositoryService()
                    .getBpmnModel(instance.getProcessDefinitionId())
                    .getMainProcess()
                    .getFlowElement("review");
            assertNotNull(deployedTask);
            assertEquals(
                    ORIGINAL_COLLECTION_VARIABLE,
                    ConfiguredTaskPropertyReader.read(
                            deployedTask,
                            "entryDynamicCollectionVariable"));
            MultiInstanceLoopCharacteristics loop =
                    (MultiInstanceLoopCharacteristics)
                            deployedTask.getLoopCharacteristics();
            assertEquals(COLLECTION_LITERAL, loop.getInputDataItem());
        } finally {
            engine.close();
        }
    }

    @Test
    void safeLiteralInvokesHandlerWithoutCallerCollectionVariables() {
        RecordingCollectionHandler handler =
                new RecordingCollectionHandler(AUTHORITATIVE_REVIEWERS);
        ProcessEngine engine = buildEngine(handler);
        try {
            deploy(engine);

            ProcessInstance instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("entry_dynamic_handler");

            assertAuthoritativeTasks(engine, instance);
            assertSafeLiteralInvocation(handler);
            assertTrue(handler.reviewersVariablePresence().stream()
                    .noneMatch(Boolean::booleanValue));
            assertTrue(handler.reviewersSeen().stream()
                    .allMatch(value -> value == null));
        } finally {
            engine.close();
        }
    }

    /** 部署每个测试独享的流程定义，避免测试间共享引擎状态。 */
    private void deploy(ProcessEngine engine) {
        engine.getRepositoryService()
                .createDeployment()
                .addString("entry-dynamic-handler.bpmn20.xml", bpmn())
                .deploy();
    }

    /** 验证 handler 返回的权威用户完整成为活动任务办理人。 */
    private List<Task> assertAuthoritativeTasks(
            ProcessEngine engine,
            ProcessInstance instance) {
        List<Task> tasks = engine.getTaskService()
                .createTaskQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey("review")
                .list();
        assertEquals(AUTHORITATIVE_REVIEWERS.size(), tasks.size());
        assertEquals(
                Set.copyOf(AUTHORITATIVE_REVIEWERS),
                tasks.stream()
                        .map(Task::getAssignee)
                        .collect(Collectors.toSet()));
        return tasks;
    }

    /** 验证字面量直接传给 handler，且调用方从未提供同名流程变量。 */
    private void assertSafeLiteralInvocation(
            RecordingCollectionHandler handler) {
        assertFalse(handler.originalCollections().isEmpty());
        assertTrue(handler.originalCollections().stream()
                .allMatch(COLLECTION_LITERAL::equals));
        assertTrue(handler.seedVariablePresence().stream()
                .noneMatch(Boolean::booleanValue));
    }

    /** 注册记录型 bean，并创建隔离的 H2 内存流程引擎。 */
    private ProcessEngine buildEngine(RecordingCollectionHandler handler) {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl(
                "jdbc:h2:mem:entry_dynamic_handler_"
                        + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setBeans(Map.<Object, Object>of(
                "recordingCollectionHandler", handler));
        return configuration.buildProcessEngine();
    }

    /**
     * BPMN 保留原业务 collection 属性，同时用无需调用方变量的安全字面量触发 handler。
     */
    private String bpmn() {
        return """
                <bpmn:definitions
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow.test/process">
                  <bpmn:process id="entry_dynamic_handler" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:userTask id="review" name="权威审批"
                      flowable:assignee="${reviewer}">
                      <bpmn:extensionElements>
                        <flowable:properties>
                          <flowable:property
                            name="entryDynamicCollectionVariable"
                            value="reviewers" />
                        </flowable:properties>
                      </bpmn:extensionElements>
                      <bpmn:multiInstanceLoopCharacteristics
                        isSequential="false"
                        flowable:collection="__wfEntryDynamicCollectionSeed"
                        flowable:elementVariable="reviewer">
                        <bpmn:extensionElements>
                          <flowable:collection
                            flowable:delegateExpression="${recordingCollectionHandler}" />
                        </bpmn:extensionElements>
                      </bpmn:multiInstanceLoopCharacteristics>
                    </bpmn:userTask>
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="to-review"
                      sourceRef="start" targetRef="review" />
                    <bpmn:sequenceFlow id="to-end"
                      sourceRef="review" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }

    /** 记录 Flowable 传入的原始 collection 与调用时变量，并返回权威用户。 */
    private static final class RecordingCollectionHandler
            implements FlowableCollectionHandler {

        @Serial
        private static final long serialVersionUID = 1L;

        private final List<String> authoritativeReviewers;
        private final List<Object> originalCollections = new ArrayList<>();
        private final List<Boolean> seedVariablePresence = new ArrayList<>();
        private final List<Boolean> reviewersVariablePresence =
                new ArrayList<>();
        private final List<Object> reviewersSeen = new ArrayList<>();

        private RecordingCollectionHandler(
                List<String> authoritativeReviewers) {
            this.authoritativeReviewers = List.copyOf(
                    authoritativeReviewers);
        }

        /** 忽略可伪造输入，模拟从权威业务来源解析节点进入期用户。 */
        @Override
        public Collection<?> resolveCollection(
                Object originalCollection,
                DelegateExecution execution) {
            originalCollections.add(originalCollection);
            seedVariablePresence.add(
                    execution.hasVariable(COLLECTION_LITERAL));
            reviewersVariablePresence.add(
                    execution.hasVariable(ORIGINAL_COLLECTION_VARIABLE));
            reviewersSeen.add(
                    execution.getVariable(ORIGINAL_COLLECTION_VARIABLE));
            return authoritativeReviewers;
        }

        private List<Object> originalCollections() {
            return List.copyOf(originalCollections);
        }

        private List<Boolean> seedVariablePresence() {
            return List.copyOf(seedVariablePresence);
        }

        private List<Boolean> reviewersVariablePresence() {
            return List.copyOf(reviewersVariablePresence);
        }

        private List<Object> reviewersSeen() {
            return new ArrayList<>(reviewersSeen);
        }
    }
}
