package com.workflow.process.nextapproval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.infrastructure.flowable.PersonResolverTaskAssignmentListener;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverSelectionRequest;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import com.workflow.process.task.application.nextapproval.FlowableConditionEvaluator;
import com.workflow.process.task.application.nextapproval.NextApprovalResolution;
import com.workflow.process.task.application.nextapproval.NextApprovalRouteService;
import com.workflow.process.task.application.nextapproval.NextApproverCandidateService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import com.workflow.process.task.application.nextapproval.NextApproverSelectionPolicyReader;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 使用真实 Flowable 事件链验证下一审批人覆盖最终落到活动任务。
 */
class NextApproverAssignmentFlowableIntegrationTest {

    @Test
    void directOverrideReplacesDefaultAssigneeAndWritesAudit() {
        try (Harness harness = harness("direct", directTarget())) {
            Task target = harness.stageAndComplete(
                    List.of("alice"));

            assertEquals("alice", target.getAssignee());
            assertTrue(harness.candidateUsers(target).isEmpty());
            harness.assertSingleAudit("target-review", List.of("alice"));
            assertNull(harness.overrideVariable());
        }
    }

    @Test
    void candidateOverrideReplacesDefaultCandidatePoolAndWritesAudit() {
        try (Harness harness = harness("candidate", candidateTarget())) {
            Task target = harness.stageAndComplete(
                    List.of("alice", "bob"));

            assertNull(target.getAssignee());
            assertEquals(
                    Set.of("alice", "bob"),
                    harness.candidateUsers(target));
            harness.assertSingleAudit(
                    "target-review", List.of("alice", "bob"));
            assertNull(harness.overrideVariable());
        }
    }

    @Test
    void sequentialMultiInstanceOverridePreservesSelectedOrderAndWritesAudit() {
        try (Harness harness = harness(
                "multi_instance", multiInstanceTarget())) {
            Task first = harness.stageAndComplete(
                    List.of("bob", "alice"));

            assertEquals("bob", first.getAssignee());
            assertEquals(
                    List.of("bob", "alice"),
                    harness.engine.getRuntimeService().getVariable(
                            harness.instance.getId(),
                            "_wfMultiInstanceUsers_target_review"));

            harness.engine.getTaskService().complete(first.getId());
            Task second = harness.activeTarget();
            assertEquals("alice", second.getAssignee());
            harness.assertSingleAudit(
                    "target-review", List.of("bob", "alice"));
            assertNull(harness.overrideVariable());
        }
    }

    @Test
    void failedTargetCreationRollsBackConsumedOverrideVariable() {
        try (Harness harness = harness("rollback", candidateTarget())) {
            harness.failTargetCreationAfterOverrideConsumption();

            assertThrows(RuntimeException.class, () ->
                    harness.stageAndComplete(List.of("alice")));

            Object stagedOverride = harness.overrideVariable();
            assertTrue(stagedOverride instanceof Map<?, ?>);
            assertTrue(((Map<?, ?>) stagedOverride)
                    .containsKey("target-review"));
            assertNotNull(harness.activeSource());
            assertNull(harness.activeTarget());
        }
    }

    @Test
    void nodeReferenceUsesDeployedSourceRuleWhenTargetTaskIsCreated() {
        try (Harness harness = harness(
                "node_reference",
                referencedSourceTask(),
                nodeReferenceTarget())) {
            harness.engine.getTaskService().complete(harness.source.getId());

            Task target = harness.activeTarget();
            assertNotNull(target);
            assertEquals("alice", target.getAssignee());
            assertTrue(harness.candidateUsers(target).isEmpty());
        }
    }

    private Harness harness(String suffix, String targetTask) {
        return harness(
                suffix,
                "<bpmn:userTask id=\"source-review\" name=\"源审批\" />",
                targetTask);
    }

    private Harness harness(
            String suffix,
            String sourceTask,
            String targetTask) {
        ProcessEngine engine = buildEngine();
        String processKey = "next_approver_assignment_" + suffix;
        engine.getRepositoryService()
                .createDeployment()
                .addString(
                        processKey + ".bpmn20.xml",
                        bpmn(processKey, sourceTask, targetTask))
                .deploy();
        ProcessInstance instance = engine.getRuntimeService()
                .startProcessInstanceByKey(processKey);
        Task source = engine.getTaskService()
                .createTaskQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey("source-review")
                .singleResult();

        ObjectMapper objectMapper = new ObjectMapper();
        NodeFormSubmissionService formService =
                mock(NodeFormSubmissionService.class);
        when(formService.projectEditableData(any(), any()))
                .thenReturn(Map.of());
        NextApprovalRouteService routeService =
                new NextApprovalRouteService(
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
                        "default-user-id",
                        "default-user",
                        "Default User")));
        when(candidateService.resolveAllowed(
                any(), any(), any(PersonResolveUsage.class)))
                .thenReturn(List.of(
                        user("user-alice", "alice"),
                        user("user-bob", "bob")));

        ProcessOperationLogMapper operationLogMapper =
                mock(ProcessOperationLogMapper.class);
        NextApproverOverrideStore overrideStore =
                new NextApproverOverrideStore(
                        engine.getRuntimeService(),
                        engine.getRepositoryService());
        NextApproverOverrideService overrideService =
                new NextApproverOverrideService(
                        engine.getRuntimeService(),
                        routeService,
                        candidateService,
                        operationLogMapper,
                        mock(SysUserService.class),
                        objectMapper);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        when(resolverRuntimeService.resolvePrincipalUsernames(any()))
                .thenReturn(List.of("alice"));
        PersonResolverTaskAssignmentListener assignmentListener =
                new PersonResolverTaskAssignmentListener(
                        mock(ProcessVersionHistoryMapper.class),
                        engine.getRepositoryService(),
                        engine.getRuntimeService(),
                        engine.getTaskService(),
                        resolverRuntimeService,
                        objectMapper);
        ReflectionTestUtils.setField(
                assignmentListener,
                "nextApproverOverrideStore",
                overrideStore);
        engine.getRuntimeService().addEventListener(assignmentListener);
        return new Harness(
                engine,
                instance,
                source,
                routeService,
                overrideService,
                operationLogMapper);
    }

    private ProcessEngine buildEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl(
                "jdbc:h2:mem:next_approver_assignment_"
                        + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        return configuration.buildProcessEngine();
    }

    private String bpmn(
            String processKey,
            String sourceTask,
            String targetTask) {
        return """
                <bpmn:definitions
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow.test/process">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    %s
                    %s
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="to-source" sourceRef="start" targetRef="source-review" />
                    <bpmn:sequenceFlow id="to-target" sourceRef="source-review" targetRef="target-review" />
                    <bpmn:sequenceFlow id="to-end" sourceRef="target-review" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(processKey, sourceTask, targetTask);
    }

    private String referencedSourceTask() {
        return """
                <bpmn:userTask id="source-review" name="源审批"
                  flowable:assignee="alice">
                  <bpmn:extensionElements>
                    <flowable:properties>
                      <flowable:property name="assigneeConfig" value="%s" />
                    </flowable:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """.formatted(escape("""
                {"assignmentConfigVersion":2,
                 "assigneeType":"user","assigneeValue":"alice"}
                """.replaceAll("\\s+", "")));
    }

    private String nodeReferenceTarget() {
        return userTask(
                "",
                "",
                """
                {"assignmentConfigVersion":2,
                 "assigneeType":"node_reference",
                 "referencedNodeId":"source-review",
                 "referencedNodeName":"源审批"}
                """);
    }

    private String directTarget() {
        return userTask(
                "flowable:assignee=\"default-user\" "
                        + "flowable:candidateUsers=\"legacy-candidate\"",
                "",
                """
                {"assigneeType":"user","assigneeValue":"default-user",
                 "candidateUsers":"legacy-candidate",
                 "nextApproverSelection":{"version":1,
                 "visible":true,"editable":true,
                 "source":{"type":"SCOPE","rules":[
                 {"type":"ALL_USERS","values":[]}]}}}
                """);
    }

    private String candidateTarget() {
        return userTask(
                "flowable:candidateUsers=\"legacy-candidate\"",
                "",
                """
                {"assigneeType":"candidate",
                 "candidateUsers":"legacy-candidate",
                 "nextApproverSelection":{"version":1,
                 "visible":true,"editable":true,
                 "source":{"type":"SCOPE","rules":[
                 {"type":"ALL_USERS","values":[]}]}}}
                """);
    }

    private String multiInstanceTarget() {
        return userTask(
                "flowable:assignee=\"${participant}\"",
                """
                <bpmn:multiInstanceLoopCharacteristics
                  isSequential="true"
                  flowable:collection="${_wfMultiInstanceUsers_target_review}"
                  flowable:elementVariable="participant" />
                """,
                """
                {"multiInstance":true,
                 "nextApproverSelection":{"version":1,
                 "visible":true,"editable":true,
                 "source":{"type":"SCOPE","rules":[
                 {"type":"ALL_USERS","values":[]}]}}}
                """);
    }

    private String userTask(
            String attributes,
            String loopCharacteristics,
            String assigneeConfig) {
        return """
                <bpmn:userTask id="target-review" name="目标审批" %s>
                  <bpmn:extensionElements>
                    <flowable:properties>
                      <flowable:property name="assigneeConfig" value="%s" />
                    </flowable:properties>
                  </bpmn:extensionElements>
                  %s
                </bpmn:userTask>
                """.formatted(
                attributes,
                escape(assigneeConfig.replaceAll("\\s+", "")),
                loopCharacteristics);
    }

    private SysUser user(String id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static final class Harness implements AutoCloseable {

        private final ProcessEngine engine;
        private final ProcessInstance instance;
        private final Task source;
        private final NextApprovalRouteService routeService;
        private final NextApproverOverrideService overrideService;
        private final ProcessOperationLogMapper operationLogMapper;

        private Harness(
                ProcessEngine engine,
                ProcessInstance instance,
                Task source,
                NextApprovalRouteService routeService,
                NextApproverOverrideService overrideService,
                ProcessOperationLogMapper operationLogMapper) {
            this.engine = engine;
            this.instance = instance;
            this.source = source;
            this.routeService = routeService;
            this.overrideService = overrideService;
            this.operationLogMapper = operationLogMapper;
        }

        private Task stageAndComplete(List<String> usernames) {
            NextApprovalPreviewRequest preview =
                    new NextApprovalPreviewRequest();
            preview.setAction("approve");
            preview.setComment("");
            NextApprovalResolution resolution =
                    routeService.resolve(source, preview, true);
            assertTrue(resolution.ready());

            NextApproverSelectionRequest selection =
                    new NextApproverSelectionRequest();
            selection.setNodeId("target-review");
            selection.setUserKeys(usernames);
            overrideService.validateAndStage(
                    source,
                    "approve",
                    "同意",
                    "",
                    resolution.scopeKey(),
                    List.of(selection));
            engine.getTaskService().complete(
                    source.getId(),
                    Map.of("action", "approve", "approved", "approve"));
            return activeTarget();
        }

        private Task activeTarget() {
            return engine.getTaskService()
                    .createTaskQuery()
                    .processInstanceId(instance.getId())
                    .taskDefinitionKey("target-review")
                    .active()
                    .singleResult();
        }

        private Task activeSource() {
            return engine.getTaskService()
                    .createTaskQuery()
                    .processInstanceId(instance.getId())
                    .taskDefinitionKey("source-review")
                    .active()
                    .singleResult();
        }

        private void failTargetCreationAfterOverrideConsumption() {
            engine.getRuntimeService().addEventListener(
                    new FlowableEventListener() {
                        @Override
                        public void onEvent(FlowableEvent event) {
                            if (event instanceof FlowableEntityEvent entity
                                    && entity.getEntity() instanceof Task task
                                    && "target-review".equals(
                                    task.getTaskDefinitionKey())) {
                                throw new IllegalStateException(
                                        "fail after override consumption");
                            }
                        }

                        @Override
                        public boolean isFailOnException() {
                            return true;
                        }

                        @Override
                        public String getOnTransaction() {
                            return null;
                        }

                        @Override
                        public boolean isFireOnTransactionLifecycleEvent() {
                            return false;
                        }
                    },
                    FlowableEngineEventType.TASK_CREATED);
        }

        private Set<String> candidateUsers(Task task) {
            return engine.getTaskService()
                    .getIdentityLinksForTask(task.getId())
                    .stream()
                    .filter(link -> "candidate".equals(link.getType()))
                    .map(IdentityLink::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        private Object overrideVariable() {
            return engine.getRuntimeService().getVariable(
                    instance.getId(),
                    NextApproverOverrideService.VARIABLE_NAME);
        }

        private void assertSingleAudit(
                String targetNodeId,
                List<String> usernames) {
            ArgumentCaptor<ProcessOperationLog> captor =
                    ArgumentCaptor.forClass(ProcessOperationLog.class);
            verify(operationLogMapper, times(1)).insert(captor.capture());
            ProcessOperationLog audit = captor.getValue();
            assertEquals("NEXT_ASSIGNEE_OVERRIDE", audit.getOperationType());
            assertTrue(audit.getNewValue().contains(targetNodeId));
            usernames.forEach(username ->
                    assertTrue(audit.getNewValue().contains(username)));
        }

        @Override
        public void close() {
            engine.close();
        }
    }
}
