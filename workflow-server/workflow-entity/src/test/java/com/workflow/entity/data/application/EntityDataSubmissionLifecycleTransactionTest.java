package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.process.model.ProcessStartRequest;
import com.workflow.contracts.process.model.ProcessStartResult;
import com.workflow.contracts.process.port.ProcessRuntimePort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.code.EntityCodeGenerationScope;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.LocalDateTimeTypeHandler;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 首次提交审计与当前流程生命周期分离；真实 JDBC 事务同时验证正常路径和失败回滚。 */
class EntityDataSubmissionLifecycleTransactionTest {
    private static final LocalDateTime FORGED_TIME = LocalDateTime.of(2001, 1, 1, 0, 0);

    @AfterEach
    void clearActor() {
        UserContext.clear();
    }

    @Test
    void draftIgnoresForgedSubmissionMetadataAndKeepsCreationActorAndDepartment() {
        var f = new Fixture();
        UserContext.setCurrentUser("creator", "创建人");
        EntityDataDTO request = f.newRecord(false);
        Map<String, Object> callerData = request.getData();
        Map<String, Object> originalData = new HashMap<>(callerData);
        EntityDataDTO saved = f.service.save(request);

        assertUnsubmitted(saved);
        assertNoSubmissionFields(saved.getData());
        assertEquals(originalData, callerData);
        assertUnsubmitted(f.read(saved.getId()));
        assertEquals("creator", f.read(saved.getId()).getCreateBy());
        assertEquals("creator-dept", f.read(saved.getId()).getDeptId());
        verifyNoInteractions(f.processes);
    }

    @Test
    void entityWithoutWorkflowHasCreationTimeButNoSubmissionMetadata() {
        var f = new Fixture();
        f.definition.setProcessDefinitionId(null);
        f.snapshot.setProcessDefinitionId(null);
        UserContext.setCurrentUser("creator", "创建人");
        EntityDataDTO saved = f.service.save(f.newRecord(false));

        assertUnsubmitted(f.read(saved.getId()));
        assertNotNull(f.read(saved.getId()).getCreateTime());
        verifyNoInteractions(f.processes);
    }

    @Test
    void createAndSubmitWritesServerTimeAndAuthenticatedActorAfterSuccessfulStart() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "实际提交人");
        LocalDateTime before = LocalDateTime.now();
        EntityDataDTO saved = f.service.save(f.newRecord(true));
        LocalDateTime after = LocalDateTime.now();

        assertSubmission(saved, "submitter", "实际提交人");
        assertFalse(saved.getSubmitTime().isBefore(before));
        assertFalse(saved.getSubmitTime().isAfter(after));
        assertEquals(saved.getSubmitTime(), saved.getProcessStartTime());
        assertEquals(saved.getSubmitTime(), f.read(saved.getId()).getSubmitTime());
        assertEquals("submitter", f.lastStart.submitterId());
        assertEquals("实际提交人", f.lastStart.submitterName());
        assertNoSubmissionFields(saved.getData());
        assertNoSubmissionFields(f.lastStart.data());
        assertEquals(1, f.processCount());
    }

    @Test
    void trustedMutationScopeSuppliesActorWhenThereIsNoLoggedInUser() {
        var f = new Fixture();
        UserContext.clear();
        var context = EntityMutationContext.builder(EntityMutationSourceType.SYSTEM_TASK, "CREATE", "后台创建")
                .operator("trusted-operator", "可信命令操作人").build();

        try (var ignored = EntityCodeGenerationScope.open(context)) {
            EntityDataDTO saved = f.service.save(f.newRecord(true));

            assertSubmission(saved, "trusted-operator", "可信命令操作人");
            assertEquals("trusted-operator", f.read(saved.getId()).getCreateBy());
            assertEquals("trusted-operator", f.lastStart.submitterId());
            assertEquals("可信命令操作人", f.lastStart.submitterName());
            assertSame(context, EntityCodeGenerationScope.current());
        }
        assertNull(EntityCodeGenerationScope.current());
    }

    @Test
    void loggedInActorTakesPriorityAndNestedMutationScopesAreRestored() {
        var f = new Fixture();
        UserContext.setCurrentUser("http-user", "当前登录人");
        var outer = EntityMutationContext.builder(EntityMutationSourceType.SYSTEM_TASK, "OUTER", "外层命令")
                .operator("outer-operator", "外层操作人").build();
        var inner = EntityMutationContext.builder(EntityMutationSourceType.SYSTEM_TASK, "CREATE", "内层创建")
                .operator("inner-operator", "内层操作人").build();

        // 嵌套后台命令不能替换本次登录身份，调用结束也不能泄漏内层命令身份到后续工作。
        try (var ignored = EntityCodeGenerationScope.open(outer)) {
            try (var nested = EntityCodeGenerationScope.open(inner)) {
                EntityDataDTO saved = f.service.save(f.newRecord(true));

                assertSubmission(saved, "http-user", "当前登录人");
                assertEquals("http-user", f.read(saved.getId()).getCreateBy());
                assertEquals("http-user", f.lastStart.submitterId());
                assertEquals("当前登录人", f.lastStart.submitterName());
                assertSame(inner, EntityCodeGenerationScope.current());
            }
            assertSame(outer, EntityCodeGenerationScope.current());
        }
        assertNull(EntityCodeGenerationScope.current());
        assertEquals("http-user", UserContext.getUserId());
    }

    @Test
    void saveWithMissingRecordIdFailsBeforeStartingWorkflow() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "提交人");
        EntityDataDTO request = f.newRecord(true);
        request.setId("missing-record");

        BusinessConflictException failure = assertThrows(BusinessConflictException.class,
                () -> f.service.save(request));

        assertEquals("ENTITY_DATA_NOT_FOUND", failure.getErrorCode());
        assertEquals(0, f.recordCount());
        assertEquals(0, f.processCount());
        verifyNoInteractions(f.processes);
    }

    @Test
    void draftCreatedByOneUserRecordsTheUserWhoActuallySubmits() {
        var f = new Fixture();
        UserContext.setCurrentUser("creator", "创建人");
        String id = f.service.save(f.newRecord(false)).getId();
        UserContext.setCurrentUser("submitter", "实际提交人");

        EntityDataDTO submitted = f.service.update("asset", id, f.submitRequest());

        assertSubmission(submitted, "submitter", "实际提交人");
        assertEquals("creator", submitted.getCreateBy());
        assertEquals("creator-dept", submitted.getDeptId());
        assertEquals("submitter", f.lastStart.submitterId());
        assertEquals("实际提交人", f.lastStart.submitterName());
        assertEquals(submitted.getSubmitTime(), f.read(id).getSubmitTime());
    }

    @Test
    void draftEditCannotSetSubmissionMetadataThroughCamelCaseOrStorageNames() {
        var f = new Fixture();
        UserContext.setCurrentUser("creator", "创建人");
        String id = f.service.save(f.newRecord(false)).getId();

        EntityDataDTO edited = f.service.update("asset", id, f.forgedRequest());

        assertUnsubmitted(edited);
        assertUnsubmitted(f.read(id));
        assertEquals("changed", edited.getName());
        verifyNoInteractions(f.processes);
    }

    @Test
    void normalUpdateAndSaveCannotRewriteFirstSubmissionEvenWithForgedValues() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "首次提交人");
        EntityDataDTO first = f.service.save(f.newRecord(true));
        UserContext.setCurrentUser("editor", "编辑人");

        EntityDataDTO edited = f.service.update("asset", first.getId(), f.forgedRequest());
        assertSameSubmission(first, edited);
        EntityDataDTO saveRequest = f.newRecord(false);
        saveRequest.setId(first.getId());
        EntityDataDTO saved = f.service.save(saveRequest);

        assertSameSubmission(first, saved);
        assertSameSubmission(first, f.read(first.getId()));
        verify(f.processes, times(1)).start(any());
    }

    @Test
    void repeatedStartAndAuthorizedRestartPreserveFirstSubmission() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "首次提交人");
        EntityDataDTO first = f.service.save(f.newRecord(true));
        EntityDataDTO repeated = f.service.update("asset", first.getId(), f.submitRequest());
        assertSameSubmission(first, repeated);
        verify(f.processes, times(1)).start(any());

        // 不依赖 sleep 判断新一轮时间：将当前轮开始时间回拨，首次提交审计仍保持原值。
        f.jdbc.update("UPDATE wf_asset SET process_status='COMPLETED', status='WITHDRAWN', process_start_time=? WHERE id=?",
                FORGED_TIME, first.getId());
        EntityDataDTO restarted = f.restart(first.getId(), first.getProcessInstanceId());

        assertSameSubmission(first, restarted);
        assertNotEquals(first.getProcessInstanceId(), restarted.getProcessInstanceId());
        assertTrue(restarted.getProcessStartTime().isAfter(FORGED_TIME));
        assertEquals(first.getProcessInstanceId(), f.lastStart.previousProcessInstanceId());
        assertEquals("submitter", f.lastStart.submitterId());
        verify(f.processes, times(2)).start(any());
    }

    @Test
    void legacyWorkflowWithoutSubmissionTimeStaysUnknownOnRestart() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "当前提交人");
        String id = f.service.save(f.newRecord(false)).getId();
        f.jdbc.update("""
                UPDATE wf_asset SET process_instance_id='legacy-instance', process_status='COMPLETED',
                    status='WITHDRAWN', process_start_time=?, submitter_id='legacy-user',
                    submitter_name='历史提交人' WHERE id=?
                """, FORGED_TIME, id);

        EntityDataDTO restarted = f.restart(id, "legacy-instance");

        assertNull(restarted.getSubmitTime());
        assertEquals("legacy-user", restarted.getSubmitterId());
        assertEquals("历史提交人", restarted.getSubmitterName());
        assertEquals("submitter", f.lastStart.submitterId());
        assertEquals("当前提交人", f.lastStart.submitterName());
        assertNull(f.read(id).getSubmitTime());
    }

    @Test
    void historicalProcessStartTimeAloneAlsoPreventsInventingFirstSubmission() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "当前提交人");
        String id = f.service.save(f.newRecord(false)).getId();
        f.jdbc.update("UPDATE wf_asset SET process_start_time=? WHERE id=?", FORGED_TIME, id);

        EntityDataDTO submitted = f.service.update("asset", id, f.submitRequest());

        assertUnsubmitted(submitted);
        assertNotNull(submitted.getProcessInstanceId());
        assertUnsubmitted(f.read(id));
    }

    @Test
    void historicalCompletedStatusAloneAlsoPreventsInventingFirstSubmission() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "当前提交人");
        String id = f.service.save(f.newRecord(false)).getId();
        f.jdbc.update("UPDATE wf_asset SET process_status='COMPLETED' WHERE id=?", id);

        EntityDataDTO submitted = f.service.update("asset", id, f.submitRequest());

        assertUnsubmitted(submitted);
        assertNotNull(submitted.getProcessInstanceId());
        assertUnsubmitted(f.read(id));
    }

    @Test
    void engineFailureRollsBackNewRecordAndTransactionalProcessWrites() {
        var f = new Fixture();
        UserContext.setCurrentUser("submitter", "提交人");
        f.failStart = true;

        assertThrows(IllegalStateException.class, () -> f.service.save(f.newRecord(true)));

        assertEquals(0, f.recordCount());
        assertEquals(0, f.processCount());
    }

    @Test
    void engineFailureRollsBackDraftEditsWithoutLeavingSubmissionMetadata() {
        var f = new Fixture();
        UserContext.setCurrentUser("creator", "创建人");
        String id = f.service.save(f.newRecord(false)).getId();
        EntityDataDTO before = f.read(id);
        f.failStart = true;
        UserContext.setCurrentUser("submitter", "提交人");

        assertThrows(IllegalStateException.class, () -> f.service.update("asset", id, f.submitRequest()));

        assertUnsubmitted(f.read(id));
        assertEquals(before.getName(), f.read(id).getName());
        assertEquals(before.getUpdateTime(), f.read(id).getUpdateTime());
        assertNull(f.read(id).getProcessInstanceId());
        assertEquals(0, f.processCount());
    }

    @Test
    void failureAfterAuditWriteRollsBackSubmissionAndProcessAndCanRetry() {
        var f = new Fixture();
        UserContext.setCurrentUser("creator", "创建人");
        String id = f.service.save(f.newRecord(false)).getId();
        UserContext.setCurrentUser("submitter", "提交人");
        doAnswer(call -> {
            // 确保异常发生在提交审计已落库之后，避免只验证到启动之前的失败路径。
            assertSubmission(f.read(id), "submitter", "提交人");
            throw new IllegalStateException("模拟启动后的团队审计写入失败");
        }).when(f.teams).record(eq("asset"), eq(id), eq("START_PROCESS"), anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> f.service.update("asset", id, f.submitRequest()));

        assertUnsubmitted(f.read(id));
        assertNull(f.read(id).getProcessInstanceId());
        assertEquals("original", f.read(id).getName());
        assertEquals(0, f.processCount());
        reset(f.teams);
        EntityDataDTO retry = f.service.update("asset", id, f.submitRequest());
        assertSubmission(retry, "submitter", "提交人");
        assertEquals(1, f.processCount());
    }

    private static void assertUnsubmitted(EntityDataDTO record) {
        assertNull(record.getSubmitTime());
        assertNull(record.getSubmitterId());
        assertNull(record.getSubmitterName());
    }

    private static void assertNoSubmissionFields(Map<String, Object> data) {
        assertNotNull(data);
        for (String field : List.of("submitTime", "submit_time", "submitterId", "submitter_id",
                "submitterName", "submitter_name")) {
            assertFalse(data.containsKey(field), "业务数据不应回显提交审计字段: " + field);
        }
    }

    private static void assertSubmission(EntityDataDTO record, String id, String name) {
        assertNotNull(record.getSubmitTime());
        assertEquals(id, record.getSubmitterId());
        assertEquals(name, record.getSubmitterName());
    }

    private static void assertSameSubmission(EntityDataDTO expected, EntityDataDTO actual) {
        assertNotNull(expected.getSubmitTime());
        assertEquals(expected.getSubmitTime(), actual.getSubmitTime());
        assertEquals(expected.getSubmitterId(), actual.getSubmitterId());
        assertEquals(expected.getSubmitterName(), actual.getSubmitterName());
    }

    /** JDBC/MyBatis 与生产事务注解共同参与测试，流程端口仅用探针模拟同一数据库内的流程写入。 */
    private static final class Fixture {
        private final JdbcTemplate jdbc;
        private final EntityDataDynamicMapper rows;
        private final EntityRuntimeRecordMapper records = new EntityRuntimeRecordMapper(new ObjectMapper());
        private final ProcessRuntimePort processes = mock(ProcessRuntimePort.class);
        private final EntityRecordTeamService teams = mock(EntityRecordTeamService.class);
        private final EntityDefinition definition = new EntityDefinition();
        private final EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        private final EntityDataMutationService service;
        private final AtomicInteger sequence = new AtomicInteger();
        private ProcessStartRequest lastStart;
        private boolean failStart;

        private Fixture() {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:submission_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE wf_asset (
                        id VARCHAR(64) PRIMARY KEY, name VARCHAR(100), code VARCHAR(100), status VARCHAR(30),
                        process_instance_id VARCHAR(64), process_status VARCHAR(30),
                        process_start_time TIMESTAMP(9), process_end_time TIMESTAMP(9),
                        current_task_id VARCHAR(64), current_task_name VARCHAR(100), current_task_assignee VARCHAR(64),
                        submit_time TIMESTAMP(9), submitter_id VARCHAR(64), submitter_name VARCHAR(100), dept_id VARCHAR(64),
                        create_by VARCHAR(64), create_time TIMESTAMP(9), update_by VARCHAR(64), update_time TIMESTAMP(9),
                        deleted INT DEFAULT 0)
                    """);
            jdbc.execute("CREATE TABLE process_probe (id VARCHAR(64) PRIMARY KEY, record_id VARCHAR(64))");
            var configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), source));
            configuration.setDatabaseId("MYSQL");
            configuration.setCallSettersOnNulls(true);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            // H2 的 TIMESTAMP 默认返回 Timestamp；这里保持生产 MySQL DATETIME 的 LocalDateTime 读取契约。
            configuration.getTypeHandlerRegistry().register(Object.class, JdbcType.TIMESTAMP, new LocalDateTimeTypeHandler());
            configuration.addMapper(EntityDataDynamicMapper.class);
            rows = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(EntityDataDynamicMapper.class);

            definition.setEntityCode("asset");
            definition.setProcessDefinitionId("definition");
            snapshot.setEntityCode("asset");
            snapshot.setProcessDefinitionId("definition");
            snapshot.setFields(List.of());
            var definitions = mock(EntityDefinitionMapper.class);
            when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
            var statuses = mock(EntityStatusMapper.class);
            var withdrawn = new EntityStatus();
            withdrawn.setStatusCategory("WITHDRAWN");
            when(statuses.findByEntityAndCode("asset", "WITHDRAWN")).thenReturn(withdrawn);
            var tables = mock(DynamicTableService.class);
            when(tables.getTableName("asset")).thenReturn("wf_asset");
            when(tables.tableExists("asset")).thenReturn(true);
            var codes = mock(EntityCodeGeneratorService.class);
            when(codes.generateCode(any())).thenReturn("ASSET-001");
            var relations = mock(EntityRelationRuntimeService.class);
            when(relations.loadRelations(any())).thenReturn(List.of());
            when(relations.withoutRelationData(anyMap(), anyList()))
                    .thenAnswer(call -> new HashMap<>(call.<Map<String, Object>>getArgument(0)));
            when(relations.withoutRelationDataFromRequest(anyMap(), anyList()))
                    .thenAnswer(call -> new HashMap<>(call.<Map<String, Object>>getArgument(0)));
            when(relations.extractRelationData(anyMap(), anyList())).thenReturn(Map.of());
            when(relations.extractRelationDataFromRequest(anyMap(), anyList())).thenReturn(Map.of());
            var multiValues = mock(EntityMultiValueRuntimeService.class);
            when(multiValues.extractConfiguredValues(any(), anyMap())).thenReturn(Map.of());
            var users = mock(SysUserService.class);
            var creator = new SysUser();
            creator.setDeptId("creator-dept");
            when(users.getById("creator")).thenReturn(creator);
            var snapshots = mock(EntityPublishedSnapshotService.class);
            when(snapshots.getLatestByEntityCode("asset")).thenReturn(snapshot);
            var validator = mock(EntityDataMutationValidator.class);
            var payloads = new EntityDataMutationPayloadMapper(records, snapshots, validator);
            when(processes.canRestart(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
            when(processes.start(any())).thenAnswer(call -> {
                lastStart = call.getArgument(0);
                String instance = "instance-" + sequence.incrementAndGet();
                jdbc.update("INSERT INTO process_probe VALUES (?, ?)", instance, lastStart.entityRecordId());
                if (lastStart.previousProcessInstanceId() == null
                        && read(lastStart.entityRecordId()).getProcessStartTime() == null) {
                    assertUnsubmitted(read(lastStart.entityRecordId()));
                }
                if (failStart) throw new IllegalStateException("模拟流程启动失败");
                return new ProcessStartResult(instance, "PENDING", "task-" + instance, "审批", "approver");
            });
            var target = new EntityDataMutationService(rows, definitions, statuses, tables, codes, records,
                    relations, multiValues, processes, users, snapshots, teams, validator, payloads);
            var transactions = new TransactionInterceptor();
            transactions.setTransactionManager(new DataSourceTransactionManager(source));
            transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            var proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(transactions);
            service = (EntityDataMutationService) proxy.getProxy();
        }

        private EntityDataDTO newRecord(boolean start) {
            var record = new EntityDataDTO();
            record.setEntityCode("asset");
            record.setName("original");
            record.setStartProcess(start);
            record.setSubmitTime(FORGED_TIME);
            record.setSubmitterId("forged-user");
            record.setSubmitterName("伪造提交人");
            record.setData(new HashMap<>(Map.of(
                    "submitTime", FORGED_TIME, "submit_time", FORGED_TIME,
                    "submitterId", "nested-forged-user", "submitter_id", "nested-forged-user",
                    "submitterName", "嵌套伪造提交人", "submitter_name", "嵌套伪造提交人")));
            return record;
        }

        private Map<String, Object> forgedRequest() {
            var metadata = Map.<String, Object>of(
                    "submitTime", FORGED_TIME, "submit_time", FORGED_TIME,
                    "submitterId", "forged-user", "submitter_id", "forged-user",
                    "submitterName", "伪造提交人", "submitter_name", "伪造提交人");
            var request = new HashMap<>(metadata);
            request.put("data", new HashMap<>(metadata));
            request.put("name", "changed");
            return request;
        }

        private Map<String, Object> submitRequest() {
            var request = forgedRequest();
            request.put("startProcess", true);
            return request;
        }

        private EntityDataDTO restart(String id, String previousInstance) {
            var request = submitRequest();
            request.put("restartProcess", true);
            request.put("previousProcessInstanceId", previousInstance);
            return EntityProcessRestartContext.execute("asset", id, previousInstance,
                    () -> service.update("asset", id, request));
        }

        private EntityDataDTO read(String id) {
            return records.toDto(rows.selectById("wf_asset", id), "asset");
        }

        private int recordCount() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM wf_asset", Integer.class);
        }

        private int processCount() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM process_probe", Integer.class);
        }
    }
}
