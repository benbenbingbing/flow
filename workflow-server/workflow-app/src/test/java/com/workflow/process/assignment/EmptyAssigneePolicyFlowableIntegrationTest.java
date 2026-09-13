package com.workflow.process.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.process.assignment.application.*;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.infrastructure.flowable.PersonResolverTaskAssignmentListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 真实 Flowable 任务创建、认领、完成与回滚测试。身份目录用可控桩模拟停用和恢复，
 * 策略解析器、任务监听器、事件记录及处置服务均执行生产实现，事件写入独立 H2 数据库。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmptyAssigneePolicyFlowableIntegrationTest {
    private ProcessEngine engine;
    private JdbcTemplate jdbc;
    private PersonResolverRuntimeService directory;
    private AssigneeIncidentService incidents;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    void startEngine() {
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:empty_policy_engine_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setAsyncExecutorActivate(false).buildProcessEngine();
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:empty_policy_incident_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        // H2 不支持 MySQL 的 DATE_ADD(... INTERVAL ? SECOND) 语法，仅适配日期函数；
        // 原 SQL 的状态转换、参数值和实际落库结果仍由以下断言验证。
        jdbc = new JdbcTemplate(dataSource) {
            @Override public int update(String sql, Object... args) {
                return super.update(sql.replace("DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND)",
                        "DATEADD('SECOND', ?, CURRENT_TIMESTAMP)"), args);
            }
        };
        jdbc.execute("""
                CREATE TABLE process_assignee_incident (
                  id VARCHAR(64) PRIMARY KEY, process_config_id VARCHAR(64), process_definition_id VARCHAR(128),
                  process_instance_id VARCHAR(128), task_id VARCHAR(128), node_id VARCHAR(200), node_name VARCHAR(300),
                  policy VARCHAR(32), status VARCHAR(32), empty_reason_code VARCHAR(100), empty_reason_message VARCHAR(1000),
                  resolver_code VARCHAR(200), resolver_extra_params_json CLOB, fallback_user VARCHAR(100),
                  fallback_group VARCHAR(100), responsibility_owner VARCHAR(100), retry_count INT, max_retries INT,
                  initial_delay_seconds INT, backoff_multiplier DOUBLE, next_retry_at TIMESTAMP,
                  resolution_action VARCHAR(64), resolved_by VARCHAR(100), resolved_at TIMESTAMP,
                  detail_json CLOB, create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE process_assignee_incident_action (
                  id VARCHAR(64) PRIMARY KEY, incident_id VARCHAR(64), request_id VARCHAR(128), action_type VARCHAR(64),
                  status VARCHAR(24), operator VARCHAR(100), request_json CLOB, result_json CLOB, error_message VARCHAR(1500),
                  create_time TIMESTAMP, finished_at TIMESTAMP, UNIQUE(incident_id, request_id))
                """);
        directory = mock(PersonResolverRuntimeService.class);
        var resolution = new AssigneeResolutionService(directory);
        var policyService = new EmptyAssigneePolicyService(new EmptyAssigneePolicyResolver(json), resolution,
                new AssigneeIncidentRecorder(jdbc, json), engine.getTaskService());
        var listener = new PersonResolverTaskAssignmentListener(mock(ProcessVersionHistoryMapper.class),
                engine.getRepositoryService(), engine.getRuntimeService(), engine.getTaskService(), directory, json);
        ReflectionTestUtils.setField(listener, "emptyAssigneePolicyService", policyService);
        engine.getRuntimeService().addEventListener(listener, FlowableEngineEventType.TASK_CREATED);
        incidents = new AssigneeIncidentService(jdbc, json, engine.getTaskService(), engine.getRuntimeService(), resolution);
    }

    @BeforeEach
    void resetDirectory() {
        reset(directory);
        when(directory.supportsConfigured(anyString(), any())).thenReturn(true);
        when(directory.resolveUsernames(anyString(), any())).thenReturn(List.of());
        when(directory.resolvePrincipalUsernames(anyList())).thenAnswer(invocation -> {
            List<PersonPrincipal> principals = invocation.getArgument(0);
            if (principals.contains(PersonPrincipal.user("backup-id"))) return List.of("backup");
            if (principals.contains(new PersonPrincipal(PersonPrincipalType.GROUP, "finance"))) return List.of("alice", "bob");
            return List.of();
        });
        jdbc.update("DELETE FROM process_assignee_incident_action");
        jdbc.update("DELETE FROM process_assignee_incident");
    }

    @AfterAll
    void closeEngine() { if (engine != null) engine.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"user", "group", "resolver"})
    void blockRollsBackTaskAndInstanceButKeepsIncident(String source) throws Exception {
        String key = deploy("BLOCK_PUBLISH", source, Map.of());
        assertThrows(RuntimeException.class, () -> engine.getRuntimeService().startProcessInstanceByKey(key));
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processDefinitionKey(key).count());
        assertEquals(0, engine.getTaskService().createTaskQuery().processDefinitionKey(key).count());
        assertEquals("OPEN", incidentStatus());
    }

    @ParameterizedTest
    @CsvSource({"FALLBACK_USER,user", "FALLBACK_USER,group", "FALLBACK_USER,resolver",
            "FALLBACK_GROUP,user", "FALLBACK_GROUP,group", "FALLBACK_GROUP,resolver"})
    void fallbackReplacesUnavailableIdentitiesAndTaskCanComplete(String strategy, String source) throws Exception {
        Task task = start(deploy(strategy, source, Map.of()));
        if (strategy.equals("FALLBACK_USER")) {
            assertEquals("backup", task.getAssignee());
            assertTrue(engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
                    .noneMatch(link -> "candidate".equals(link.getType())));
        } else {
            assertNull(task.getAssignee());
            assertEquals(List.of("finance"), engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
                    .filter(link -> "candidate".equals(link.getType())).map(link -> link.getGroupId()).toList());
            engine.getTaskService().claim(task.getId(), "alice");
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_assignee_incident", Integer.class));
        engine.getTaskService().complete(task.getId());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult());
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "group", "resolver"})
    void incidentKeepsTaskAndManualAssignmentRestoresProgress(String source) throws Exception {
        Task task = start(deploy("CREATE_INCIDENT", source, Map.of()));
        assertNull(task.getAssignee());
        assertEquals("OPEN", incidentStatus());
        String id = incidentId();
        assertEquals(id, engine.getTaskService().getVariableLocal(task.getId(), "wfAssigneeIncidentId"));
        handle(id, "ASSIGN_USER", "manual-1");
        assertEquals("backup", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertEquals("RESOLVED", incidentStatus());
        engine.getTaskService().complete(task.getId());
    }

    @Test
    void inheritUsesDeployedSnapshotAndNodeOverrideWins() throws Exception {
        Task inherited = start(deploy("FALLBACK_USER", "user",
                Map.of("policy", "INHERIT", "fallbackUser", "stale", "responsibilityOwner", "stale")));
        assertEquals("backup", inherited.getAssignee());
        Task overridden = start(deploy("BLOCK_PUBLISH", "user",
                Map.of("policy", "FALLBACK_GROUP", "fallbackGroup", "finance", "responsibilityOwner", "node-ops")));
        assertNull(overridden.getAssignee());
        assertEquals(1, engine.getTaskService().createTaskQuery().taskId(overridden.getId()).taskCandidateGroup("finance").count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FALLBACK_USER", "FALLBACK_GROUP"})
    void disabledFallbackCreatesRecoverableIncident(String strategy) throws Exception {
        when(directory.resolvePrincipalUsernames(anyList())).thenReturn(List.of());
        Task task = start(deploy(strategy, "user", Map.of()));
        assertNull(task.getAssignee());
        assertEquals("OPEN", incidentStatus());
        assertEquals(strategy + "_INVALID", jdbc.queryForObject(
                "SELECT empty_reason_code FROM process_assignee_incident", String.class));
    }

    @Test
    void automaticRetryBacksOffThenRecoversWithoutCompletingApproval() throws Exception {
        Task task = start(deploy("WAIT_AND_RETRY", "resolver", Map.of()));
        String id = incidentId();
        assertEquals("RETRY_SCHEDULED", incidentStatus());
        assertDueWithin(5);
        jdbc.update("UPDATE process_assignee_incident SET next_retry_at = CURRENT_TIMESTAMP");
        incidents.retryDueIncidents();
        assertEquals(1, jdbc.queryForObject("SELECT retry_count FROM process_assignee_incident", Integer.class));
        assertDueWithin(10);
        when(directory.resolveUsernames(anyString(), any())).thenReturn(List.of("recovered"));
        jdbc.update("UPDATE process_assignee_incident SET next_retry_at = CURRENT_TIMESTAMP");
        incidents.retryDueIncidents();
        assertEquals("RESOLVED", incidentStatus());
        assertEquals("recovered", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult());
        // 相同自动请求号再次到达时不会重复执行人员接口。
        clearInvocations(directory);
        handle(id, "RETRY_RESOLVER", "AUTO:" + id + ":2");
        verifyNoInteractions(directory);
    }

    @Test
    void exhaustedRetryRemainsVisibleForManualHandling() throws Exception {
        start(deploy("WAIT_AND_RETRY", "resolver", Map.of()));
        String id = incidentId();
        handle(id, "RETRY_RESOLVER", "retry-1");
        handle(id, "RETRY_RESOLVER", "retry-2");
        assertEquals("OPEN", incidentStatus());
        assertEquals("RETRY_EXHAUSTED", jdbc.queryForObject("SELECT resolution_action FROM process_assignee_incident", String.class));
        assertNull(jdbc.queryForObject("SELECT next_retry_at FROM process_assignee_incident", LocalDateTime.class));
    }

    @Test
    void legacyRetryWithoutResolverLeavesSchedulerAndRequiresManualHandling() throws Exception {
        start(deploy("WAIT_AND_RETRY", "user", Map.of()));
        jdbc.update("UPDATE process_assignee_incident SET next_retry_at = CURRENT_TIMESTAMP");
        incidents.retryDueIncidents();
        assertEquals("MANUAL_REQUIRED", incidentStatus());
        assertNull(jdbc.queryForObject("SELECT next_retry_at FROM process_assignee_incident", LocalDateTime.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INHERIT", "FALLBACK_GROUP", "WAIT_AND_RETRY"})
    void nodeReferenceKeepsCurrentNodePolicyInsteadOfSourcePolicy(String strategy) throws Exception {
        String key = deploy("CREATE_INCIDENT", "node_reference",
                Map.of("policy", strategy, "fallbackGroup", "finance", "responsibilityOwner", "current-ops"));
        Task task = start(key);
        if (strategy.equals("FALLBACK_GROUP")) {
            assertEquals(1, engine.getTaskService().createTaskQuery().taskId(task.getId()).taskCandidateGroup("finance").count());
        } else {
            assertNull(task.getAssignee());
            assertEquals(strategy.equals("INHERIT") ? "OPEN" : "RETRY_SCHEDULED", incidentStatus());
            assertEquals(strategy.equals("INHERIT") ? "ops" : "current-ops",
                    jdbc.queryForObject("SELECT responsibility_owner FROM process_assignee_incident", String.class));
        }
    }

    private void assertDueWithin(int seconds) {
        LocalDateTime due = jdbc.queryForObject("SELECT next_retry_at FROM process_assignee_incident", LocalDateTime.class);
        assertNotNull(due);
        assertTrue(due.isAfter(LocalDateTime.now().plusSeconds(seconds - 2)));
        assertTrue(due.isBefore(LocalDateTime.now().plusSeconds(seconds + 2)));
    }

    private void handle(String id, String action, String requestId) {
        var request = new AssigneeIncidentHandleRequest();
        request.setAction(action);
        request.setRequestId(requestId);
        request.setReason("测试恢复");
        request.setUserId("backup-id");
        incidents.handle(id, request);
    }

    private String incidentStatus() { return jdbc.queryForObject("SELECT status FROM process_assignee_incident", String.class); }
    private String incidentId() { return jdbc.queryForObject("SELECT id FROM process_assignee_incident", String.class); }
    private Task start(String key) {
        var instance = engine.getRuntimeService().startProcessInstanceByKey(key);
        return engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
    }

    /** 每次部署唯一流程，覆盖固定用户失效、空组和动态接口空结果的真实创建入口。 */
    private String deploy(String strategy, String source, Map<String, Object> override) throws Exception {
        String key = "empty_policy_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> policy = Map.of("policy", strategy, "fallbackUser", "backup-id", "fallbackGroup", "finance",
                "responsibilityOwner", "ops", "maxRetries", 2, "initialDelaySeconds", 5, "backoffMultiplier", 2);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("assignmentConfigVersion", 2);
        config.put("assigneeType", source);
        if (source.equals("resolver")) config.put("resolverCode", "people");
        if (source.equals("node_reference")) config.put("referencedNodeId", "source");
        if (!override.isEmpty()) config.put("emptyAssigneeStrategy", override);
        String assignment = source.equals("user") ? "flowable:assignee=\"disabled-user\""
                : source.equals("group") ? "flowable:candidateGroups=\"empty-group\"" : "";
        String bpmn = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                  <process id="%s" isExecutable="true">
                    <extensionElements><flowable:properties><flowable:property name="emptyAssigneeDefault" value="%s"/></flowable:properties></extensionElements>
                    <startEvent id="start"/><sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="审批" %s>
                      <extensionElements><flowable:properties><flowable:property name="assigneeConfig" value="%s"/></flowable:properties></extensionElements>
                    </userTask>
                    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(key, attribute(policy), assignment, attribute(config));
        if (source.equals("node_reference")) {
            // 源节点不进入执行路径，只提供人员接口；其 BLOCK 策略不属于 review。
            String referenceSource = """
                    <userTask id="source"><extensionElements><flowable:properties>
                      <flowable:property name="assigneeConfig" value="%s"/>
                    </flowable:properties></extensionElements></userTask>
                    """.formatted(attribute(Map.of("assignmentConfigVersion", 2, "assigneeType", "resolver",
                    "resolverCode", "people", "emptyAssigneeStrategy", Map.of("policy", "BLOCK_PUBLISH"))));
            bpmn = bpmn.replace("</process>", referenceSource + "</process>");
            new EmptyAssigneePolicyBpmnValidator(json, new EmptyAssigneePolicyResolver(json),
                    new AssigneeResolutionService(directory)).validate(bpmn);
        }
        engine.getRepositoryService().createDeployment().addString(key + ".bpmn20.xml", bpmn).deploy();
        return key;
    }

    private String attribute(Object value) throws Exception {
        return json.writeValueAsString(value).replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
