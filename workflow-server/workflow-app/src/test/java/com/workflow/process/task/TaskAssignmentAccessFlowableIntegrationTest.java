package com.workflow.process.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.identity.IdentityUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.infrastructure.flowable.PersonResolverTaskAssignmentListener;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.application.MultiInstanceOutcomeService;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通过真实 Flowable 身份链接验证各审批人类型的访问、认领和审批闭环。
 * 业务组和角色只存在于业务目录中，刻意不注册 Flowable IDM 成员关系，
 * 防止原先只调用 taskCandidateUser 的实现被测试数据掩盖。
 */
class TaskAssignmentAccessFlowableIntegrationTest {

    private static ProcessEngine engine;
    private PersonResolverTaskAssignmentListener assignmentListener;
    private TaskActionService taskActionService;
    private ProcessTaskService processTaskService;

    @BeforeAll
    static void startEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:assignment_access_"
                + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        engine = configuration.buildProcessEngine();
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("alice-id", "alice");
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysGroup group = new SysGroup();
        group.setId("review-group-id");
        group.setGroupCode("reviewers");
        group.setStatus("0");
        group.setDeleted(0);
        when(groupMapper.selectGroupsByUserId("alice-id")).thenReturn(List.of(group));
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRole role = new SysRole();
        role.setId("review-role-id");
        role.setRoleCode("reviewer");
        role.setStatus("0");
        role.setDeleted(0);
        when(roleMapper.selectRolesByUserId("alice-id")).thenReturn(List.of(role));
        IdentityDirectoryPort identityDirectory = mock(IdentityDirectoryPort.class);
        IdentityUser alice = new IdentityUser("alice-id", "alice", "Alice", null, null);
        when(identityDirectory.findUser("alice-id")).thenReturn(Optional.of(alice));
        when(identityDirectory.findUser("alice")).thenReturn(Optional.of(alice));

        ObjectMapper objectMapper = new ObjectMapper();
        PersonResolverRuntimeService resolverRuntime = mock(PersonResolverRuntimeService.class);
        when(resolverRuntime.supportsConfigured(anyString(), eq(PersonResolveUsage.ASSIGNEE)))
                .thenReturn(true);
        when(resolverRuntime.resolveUsernames(anyString(), any())).thenReturn(List.of("alice"));
        when(resolverRuntime.resolvePrincipalUsernames(any())).thenReturn(List.of("alice"));
        assignmentListener = new PersonResolverTaskAssignmentListener(
                mock(ProcessVersionHistoryMapper.class), engine.getRepositoryService(),
                engine.getRuntimeService(), engine.getTaskService(), resolverRuntime, objectMapper);
        engine.getRuntimeService().addEventListener(assignmentListener);

        processTaskService = mock(ProcessTaskService.class);
        taskActionService = new TaskActionService(
                engine.getTaskService(), engine.getRuntimeService(), engine.getHistoryService(),
                processTaskService, engine.getRepositoryService(),
                mock(ProcessOperationLogMapper.class), mock(SysUserService.class),
                mock(NodeFormSubmissionService.class), mock(EntityActionCapabilityService.class),
                mock(EntityRecordPort.class), mock(ProcessCcService.class),
                mock(NextApproverOverrideService.class),
                new MultiInstanceOutcomeService(engine.getRuntimeService(), engine.getRepositoryService(),
                        engine.getTaskService(), objectMapper),
                mock(NodeOperationCapabilityService.class),
                new TaskIdentityAccessService(engine.getTaskService(), groupMapper, roleMapper, identityDirectory));
    }

    @AfterEach
    void tearDown() {
        engine.getRuntimeService().removeEventListener(assignmentListener);
        UserContext.clear();
    }

    /** 每种普通审批人规则同时验证显式认领和审批时自动认领。 */
    @ParameterizedTest(name = "{0}; explicitClaim={1}")
    @MethodSource("assignmentModes")
    void assignmentCanBeAccessedClaimedAndApproved(AssignmentMode mode, boolean explicitClaim) {
        ProcessInstance instance = deployAndStart(mode.taskXml(), mode.sourceXml(), mode.variables());
        if (!mode.sourceXml().isEmpty()) {
            Task source = engine.getTaskService().createTaskQuery()
                    .processInstanceId(instance.getId()).taskDefinitionKey("source").singleResult();
            engine.getTaskService().complete(source.getId());
        }
        Task task = engine.getTaskService().createTaskQuery()
                .processInstanceId(instance.getId()).taskDefinitionKey("review").singleResult();
        assertNotNull(task);
        assertEquals(mode.assignee(), task.getAssignee());
        assertEquals(mode.candidateUsers(), candidateValues(task, false));
        assertEquals(mode.candidateGroups(), candidateValues(task, true));

        if (!mode.candidateGroups().isEmpty()) {
            // 复现关键前提：引擎 IDM 不认识业务组关系，但真实候选成员应能办理。
            assertEquals(0, engine.getTaskService().createTaskQuery()
                    .taskId(task.getId()).taskCandidateUser("alice").count());
            assertEquals(0, engine.getTaskService().createTaskQuery()
                    .taskId(task.getId()).taskCandidateUser("alice-id").count());
        }
        taskActionService.requireTaskAccess(task.getId());
        if (explicitClaim) {
            taskActionService.claimTask(task.getId());
            String expectedAssignee = mode.assignee() == null ? "alice" : mode.assignee();
            assertEquals(expectedAssignee, engine.getTaskService().createTaskQuery()
                    .taskId(task.getId()).singleResult().getAssignee());
        }
        taskActionService.completeTask(task.getId(), "alice", "approve", "同意", null, null);

        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult());
        verify(processTaskService).completeTask(task.getId(), "approve", "同意", null);
    }

    /** 会签各实例与或签首个实例都使用直接办理人权限，并保留引擎汇聚行为。 */
    @ParameterizedTest(name = "multi-instance decision={0}")
    @ValueSource(strings = {"countersign", "orsign"})
    void multiInstanceApproversCanCompleteTheirOwnTasks(String decision) {
        String completion = "orsign".equals(decision)
                ? "<bpmn:completionCondition>${nrOfCompletedInstances >= 1}</bpmn:completionCondition>"
                : "";
        String taskXml = """
                <bpmn:userTask id="review" name="多人审批" flowable:assignee="${participant}">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="false"
                    flowable:collection="${participants}" flowable:elementVariable="participant">
                    %s
                  </bpmn:multiInstanceLoopCharacteristics>
                </bpmn:userTask>
                """.formatted(completion);
        ProcessInstance instance = deployAndStart(taskXml, "", Map.of("participants", List.of("alice", "bob")));
        Task alice = engine.getTaskService().createTaskQuery()
                .processInstanceId(instance.getId()).taskAssignee("alice").singleResult();
        taskActionService.requireTaskAccess(alice.getId());
        taskActionService.completeTask(alice.getId(), "alice", "approve", "同意", null, null);
        if ("countersign".equals(decision)) {
            Task bob = engine.getTaskService().createTaskQuery()
                    .processInstanceId(instance.getId()).taskAssignee("bob").singleResult();
            assertNotNull(bob);
            UserContext.setCurrentUser("bob-id", "bob");
            taskActionService.requireTaskAccess(bob.getId());
            taskActionService.completeTask(bob.getId(), "bob", "approve", "同意", null, null);
        }
        assertNull(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult());
    }

    private static Stream<Arguments> assignmentModes() {
        Stream.Builder<AssignmentMode> modes = Stream.builder();
        modes.add(staticMode("固定用户 username", "assignee", "alice", "alice", false));
        modes.add(staticMode("固定用户 ID", "assignee", "alice-id", "alice-id", false));
        modes.add(staticMode("固定候选 username", "candidateUsers", "alice", null, false));
        modes.add(staticMode("固定候选 ID", "candidateUsers", "alice-id", null, false));
        for (String group : List.of("reviewers", "review-group-id", "ROLE_reviewer", "ROLE_review-role-id")) {
            modes.add(staticMode("组或角色 " + group, "candidateGroups", group, null, false));
        }
        modes.add(staticMode("表达式直接办理", "assignee", "alice", "alice", true));
        modes.add(staticMode("表达式候选人员", "candidateUsers", "alice", null, true));
        modes.add(staticMode("表达式用户组", "candidateGroups", "reviewers", null, true));
        modes.add(staticMode("表达式角色", "candidateGroups", "ROLE_reviewer", null, true));
        for (String resolver : List.of("customAssigneeResolver", "entityUserReferenceField", "relativeOrgPosition")) {
            for (String mode : List.of("DIRECT", "CANDIDATE")) {
                String config = "{\"assigneeType\":\"interface\",\"resolverCode\":\"" + resolver
                        + "\",\"assignmentMode\":\"" + mode + "\"}";
                modes.add(new AssignmentMode(resolver + " " + mode, task("", config), "", Map.of(),
                        "DIRECT".equals(mode) ? "alice" : null,
                        "CANDIDATE".equals(mode) ? Set.of("alice") : Set.of(), Set.of()));
            }
        }
        String source = """
                <bpmn:userTask id="source" name="源审批" flowable:candidateGroups="reviewers">
                  <bpmn:extensionElements><flowable:properties>
                    <flowable:property name="assigneeConfig" value="%s" />
                  </flowable:properties></bpmn:extensionElements>
                </bpmn:userTask>
                """.formatted(escape("{\"assigneeType\":\"group\",\"assigneeValue\":\"reviewers\"}"));
        modes.add(new AssignmentMode("引用用户组节点", task("", """
                {"assignmentConfigVersion":2,"assigneeType":"node_reference",
                "referencedNodeId":"source","assignmentMode":"CANDIDATE"}
                """), source, Map.of(), null, Set.of("alice"), Set.of()));
        return modes.build().flatMap(mode -> Stream.of(Arguments.of(mode, true), Arguments.of(mode, false)));
    }

    private static AssignmentMode staticMode(String name, String attribute, String value,
                                             String assignee, boolean expression) {
        String configuredValue = expression ? "${selectedApprovers}" : value;
        String config = expression ? "{\"assigneeType\":\"expression\"}" : "";
        return new AssignmentMode(name,
                task("flowable:" + attribute + "=\"" + configuredValue + "\"", config),
                "", expression ? Map.of("selectedApprovers", value) : Map.of(), assignee,
                "candidateUsers".equals(attribute) ? Set.of(value) : Set.of(),
                "candidateGroups".equals(attribute) ? Set.of(value) : Set.of());
    }

    private static String task(String attributes, String config) {
        String extensions = config.isEmpty() ? "" : """
                <bpmn:extensionElements><flowable:properties>
                  <flowable:property name="assigneeConfig" value="%s" />
                </flowable:properties></bpmn:extensionElements>
                """.formatted(escape(config));
        return "<bpmn:userTask id=\"review\" name=\"审批\" " + attributes + ">"
                + extensions + "</bpmn:userTask>";
    }

    private ProcessInstance deployAndStart(String taskXml, String sourceXml, Map<String, Object> variables) {
        String key = "assignment_access_" + UUID.randomUUID().toString().replace("-", "");
        String sourceFlow = sourceXml.isEmpty() ? "" : """
                <bpmn:sequenceFlow id="sourceToReview" sourceRef="source" targetRef="review" />
                """;
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://workflow.test/process">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:startEvent id="start" />%s%s
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="toReview" sourceRef="start" targetRef="%s" />%s
                    <bpmn:sequenceFlow id="toEnd" sourceRef="review" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(key, sourceXml, taskXml, sourceXml.isEmpty() ? "review" : "source", sourceFlow);
        engine.getRepositoryService().createDeployment().addString(key + ".bpmn20.xml", xml).deploy();
        return engine.getRuntimeService().startProcessInstanceByKey(key, variables);
    }

    private Set<String> candidateValues(Task task, boolean groups) {
        return engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
                .filter(link -> "candidate".equals(link.getType()))
                .map(groups ? IdentityLink::getGroupId : IdentityLink::getUserId)
                .filter(value -> value != null).collect(Collectors.toSet());
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private record AssignmentMode(String name, String taskXml, String sourceXml,
                                  Map<String, Object> variables, String assignee,
                                  Set<String> candidateUsers, Set<String> candidateGroups) {
        @Override
        public String toString() {
            return name;
        }
    }
}
