package com.workflow.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.admin.externalsystem.application.ExternalSystemService;
import com.workflow.admin.externalsystem.application.ExternalSystemServiceAccess;
import com.workflow.admin.externalsystem.infrastructure.adapter.SysExternalSystemAdapter;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemMapper;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemParameterMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.core.error.ForbiddenException;
import com.workflow.port.entity.model.EntityTaskSummary;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.application.EntityQueryConditions;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.outbox.application.DatabaseOutboxPublisher;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.port.platform.WorkflowOutboxEventPort;
import com.workflow.port.process.model.ProcessPageRequest;
import com.workflow.port.system.SysExternalSystemPort;
import com.workflow.process.definition.application.ProcessCatalogAdapter;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxProjectionMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.sla.runtime.application.ProcessTaskSlaAdapter;
import com.workflow.process.sla.runtime.application.TaskSlaMonitorService;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.port.process.ProcessTaskSlaPort;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用真实 Mapper 代理和临时数据库验证 Wrapper 改写后的并发条件、NULL、投影及分页行为。 */
class MapperWrapperBehaviorTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 8, 0);
    private JdbcTemplate jdbc;
    private SqlSession session;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:mapper_wrapper_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        // 只建本次入口读取/修改的列，意外扩展 SELECT 或整行更新会直接使测试失败。
        jdbc.execute("""
                CREATE TABLE sys_external_system (
                  id VARCHAR(64) PRIMARY KEY, system_code VARCHAR(64), system_name VARCHAR(64),
                  status VARCHAR(16), address VARCHAR(128), description VARCHAR(128), version BIGINT NOT NULL DEFAULT 0,
                  created_by VARCHAR(64), updated_by VARCHAR(64), create_time TIMESTAMP,
                  update_time TIMESTAMP, deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE sys_external_system_parameter (
                  id VARCHAR(64) PRIMARY KEY, external_system_id VARCHAR(64),
                  parameter_name_zh VARCHAR(100), parameter_name_en VARCHAR(100),
                  parameter_value CLOB, sort_order INT, deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE sys_user (
                  id VARCHAR(64) PRIMARY KEY, username VARCHAR(64), password VARCHAR(128),
                  password_reset_required INT, status VARCHAR(16), token_version BIGINT,
                  update_time TIMESTAMP, deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE process_task (
                  id BIGINT PRIMARY KEY, task_id VARCHAR(64), process_instance_id VARCHAR(64),
                  action VARCHAR(64), action_label VARCHAR(64), comment VARCHAR(128), status VARCHAR(16),
                  entity_code VARCHAR(64), entity_data_id VARCHAR(64), assignee_id VARCHAR(64),
                  assignee_name VARCHAR(64), assignee_type VARCHAR(64), start_user_id VARCHAR(64),
                  business_name CLOB, business_code CLOB, business_data_name CLOB,
                  business_current_task_name CLOB, business_status CLOB,
                  inbox_summary_ready INT, inbox_identity_ready INT, deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE process_definition_config (
                  id VARCHAR(64) PRIMARY KEY, process_name VARCHAR(64), description VARCHAR(128),
                  process_key VARCHAR(64), status VARCHAR(16), category VARCHAR(64), bpmn_xml CLOB,
                  draft_hash VARCHAR(64), draft_revision BIGINT,
                  update_time TIMESTAMP, deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE entity_version_config (
                  id VARCHAR(64) PRIMARY KEY, enabled INT, config_document CLOB, revision INT,
                  update_by VARCHAR(64), update_time TIMESTAMP, deleted INT DEFAULT 0)
                """);
        var configuration = new MybatisConfiguration();
        configuration.setDatabaseId("MYSQL");
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfigUtils.getGlobalConfig(configuration).getDbConfig().setLogicDeleteField("deleted");
        var bindings = new DatabaseMybatisConfiguration();
        bindings.nullParameterBindings().customize(configuration);
        bindings.numericBooleanBindings().customize(configuration);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(pagination);
        // 同一实体的两个 BaseMapper 同时注册，覆盖生产环境的实际组合。
        for (Class<?> type : List.of(ExternalSystemMapper.class, ExternalSystemParameterMapper.class,
                SysUserMapper.class, ProcessTaskMapper.class,
                TaskInboxProjectionMapper.class, ProcessDefinitionConfigMapper.class, EntityVersionConfigMapper.class,
                EntityDataDynamicMapper.class, OutboxRecordMapper.class, ProcessTaskSlaMapper.class)) {
            configuration.addMapper(type);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        session = factory.getObject().openSession(true);
    }

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void externalSystemMutationsKeepVersionGuardNullsAuditAndSoftDelete() {
        jdbc.update("""
                INSERT INTO sys_external_system (id,system_code,system_name,status,address,description,version)
                VALUES ('system','permanent-code','old','0','address','description',4)
                """);
        var mapper = session.getMapper(ExternalSystemMapper.class);
        assertEquals(1, mapper.updateMutableFields("system", "new", "0", null, null, 4, "actor", NOW));
        assertEquals("permanent-code", text("SELECT system_code FROM sys_external_system"));
        assertNull(text("SELECT address FROM sys_external_system"));
        assertNull(text("SELECT description FROM sys_external_system"));
        assertEquals("actor", text("SELECT updated_by FROM sys_external_system"));
        assertEquals(NOW, jdbc.queryForObject("SELECT update_time FROM sys_external_system", LocalDateTime.class));
        assertEquals(0, mapper.updateMutableFields("system", "stale", "1", "stale", "stale", 4, null, null));
        assertEquals("new", text("SELECT system_name FROM sys_external_system"));
        assertEquals(0, mapper.updateStatus("system", "1", 4, null, null));
        assertEquals(1, mapper.updateStatus("system", "1", 5, null, null));
        assertNull(text("SELECT updated_by FROM sys_external_system"));
        assertNull(text("SELECT update_time FROM sys_external_system"));
        assertEquals(0, mapper.softDelete("system", 5, "actor", NOW));
        assertEquals(1, mapper.softDelete("system", 6, "actor", NOW));
        assertEquals(7, number("SELECT version FROM sys_external_system"));
        assertEquals(1, number("SELECT deleted FROM sys_external_system"));
        assertEquals("1", text("SELECT status FROM sys_external_system"));
        assertEquals(0, mapper.softDelete("system", 7, "actor", NOW));
        assertEquals(0, mapper.updateStatus("system", "0", 7, "actor", NOW));
        assertEquals(0, mapper.updateMutableFields("system", "revive", "0", null, null, 7, "actor", NOW));
    }

    @Test
    void externalSystemPortReadsWithoutWebIdentityAndChecksExplicitServiceCredentials() {
        jdbc.update("""
                INSERT INTO sys_external_system (id, system_code, system_name, status, deleted)
                VALUES ('main', 'erp-main', 'ERP 主系统', '0', 0),
                       ('alt', 'erp-alt', 'ERP 备用系统', '1', 0),
                       ('gone', 'erp-gone', 'ERP 历史系统', '1', 1)
                """);
        String sampleValue = "  sample-token  ";
        jdbc.update("""
                INSERT INTO sys_external_system_parameter (id, external_system_id, parameter_name_zh,
                    parameter_name_en, parameter_value, sort_order, deleted)
                VALUES ('token', 'main', '令牌', 'token', ?, 20, 0),
                       ('tenant', 'main', '租户', 'tenant', 'workflow', 10, 0),
                       ('old', 'main', '历史参数', 'old', 'deleted', 5, 1)
                """, sampleValue);
        var service = new ExternalSystemService(session.getMapper(ExternalSystemMapper.class),
                session.getMapper(ExternalSystemParameterMapper.class));
        var credential = "test-only-credential";
        var properties = new MapConfigurationPropertySource(Map.of(
                "workflow.external-system.services.erp-sync.credential-hash", new BCryptPasswordEncoder(4).encode(credential),
                "workflow.external-system.services.erp-sync.system-codes", "erp-main,erp-alt,erp-gone"));
        var serviceAccess = new Binder(properties).bind("workflow.external-system", Bindable.of(ExternalSystemServiceAccess.class)).get();
        SysExternalSystemPort port = new SysExternalSystemAdapter(service, serviceAccess);

        assertThrows(ForbiddenException.class, () -> port.findConfigurationForService("erp-main", "erp-sync", "invalid"));
        assertThrows(ForbiddenException.class, () -> port.findConfigurationForService("erp-main", "unknown", credential));
        assertThrows(ForbiddenException.class, () -> port.findConfigurationForService("other", "erp-sync", credential));
        assertEquals(sampleValue, port.findConfigurationForService(" erp-main ", "erp-sync", credential)
                .orElseThrow().parameters().get(1).value());
        assertTrue(port.findConfigurationForService("erp-alt", "erp-sync", credential).isEmpty());
        assertTrue(port.findConfigurationForService("erp-gone", "erp-sync", credential).isEmpty());

        var page = port.pageSystems(1, 1, " ERP ", " erp-", "0");
        assertEquals(1, page.total());
        assertEquals(1, page.pageNumber());
        assertEquals(1, page.pageSize());
        var summary = page.records().get(0);
        assertEquals("erp-main", summary.systemCode());
        assertEquals(2, summary.parameterCount());
        assertEquals(2, port.pageSystems(1, 10, null, null, null).total());
        assertEquals(summary, port.findById(" main ").orElseThrow());
        assertEquals(summary, port.findByCode(" erp-main ").orElseThrow());
        assertTrue(port.findByCode("erp").isEmpty());
        assertTrue(port.findById("gone").isEmpty());
        assertTrue(port.findByCode("erp-gone").isEmpty());

        assertEquals(summary, port.findByCode("erp-main").orElseThrow());
        var configuration = port.findConfigurationByCode(" erp-main ").orElseThrow();
        assertEquals(summary, configuration.system());
        assertEquals(List.of("tenant", "token"), configuration.parameters().stream()
                .map(SysExternalSystemPort.Parameter::nameEn).toList());
        assertEquals(sampleValue, configuration.parameters().get(1).value());
        assertFalse(configuration.toString().contains(sampleValue.trim()));
        assertTrue(port.findConfigurationByCode("erp-gone").isEmpty());
        var disabled = port.findConfigurationByCode("erp-alt").orElseThrow();
        assertEquals("1", disabled.system().status());
        assertTrue(disabled.parameters().isEmpty());
    }

    @Test
    void userUpdatesKeepActivationGuardsAndAtomicTokenIncrement() {
        jdbc.update("INSERT INTO sys_user VALUES ('1','admin','old-hash',1,'1',8,NULL,0)");
        var mapper = session.getMapper(SysUserMapper.class);
        assertEquals(0, mapper.activateBootstrapAdministrator("new-hash", "wrong-hash"));
        jdbc.update("UPDATE sys_user SET deleted=1");
        assertEquals(0, mapper.activateBootstrapAdministrator("new-hash", "old-hash"));
        assertEquals(0, mapper.incrementTokenVersion("1"));
        jdbc.update("UPDATE sys_user SET deleted=0,username='other'");
        assertEquals(0, mapper.activateBootstrapAdministrator("new-hash", "old-hash"));
        jdbc.update("UPDATE sys_user SET username='admin'");
        assertEquals(1, mapper.activateBootstrapAdministrator("new-hash", "old-hash"));
        assertEquals(0, mapper.activateBootstrapAdministrator("another-hash", "new-hash"));
        assertEquals("new-hash", text("SELECT password FROM sys_user"));
        assertEquals("0", text("SELECT status FROM sys_user"));
        assertEquals(0, number("SELECT password_reset_required FROM sys_user"));
        assertNotNull(text("SELECT update_time FROM sys_user"));
        assertEquals(1, mapper.incrementTokenVersion("1"));
        assertEquals(1, mapper.incrementTokenVersion("1"));
        assertEquals(10, number("SELECT token_version FROM sys_user"));
        assertEquals(0, mapper.incrementTokenVersion("missing"));
    }

    @Test
    void progressHistoryKeepsFourColumnProjectionOrderingAndScope() {
        insertTask(2, 0, 1, 1);
        insertTask(1, 0, 1, 1);
        insertTask(3, 1, 1, 1);
        insertTask(4, 0, 1, 1);
        jdbc.update("UPDATE process_task SET process_instance_id='other' WHERE id=4");
        var mapper = session.getMapper(ProcessTaskMapper.class);
        var history = mapper.selectProgressHistoryByProcessInstanceId("process");
        assertEquals(List.of("task-1", "task-2"), history.stream().map(ProcessTask::getTaskId).toList());
        assertEquals("approve", history.get(0).getAction());
        assertEquals("同意", history.get(0).getActionLabel());
        assertEquals("comment", history.get(0).getComment());
        assertNull(history.get(0).getId());
        assertNull(history.get(0).getStatus());
        assertTrue(mapper.selectProgressHistoryByProcessInstanceId(null).isEmpty());
    }

    @Test
    void projectionUpdatesClearNullsWithoutOverwritingOtherTaskFields() {
        insertTask(1, 0, 0, 0);
        insertTask(2, 0, 0, 0);
        insertTask(3, 1, 0, 0);
        insertTask(4, 0, 0, 0);
        jdbc.update("UPDATE process_task SET entity_data_id='other' WHERE id=4");
        var mapper = session.getMapper(TaskInboxProjectionMapper.class);
        assertEquals(1, mapper.updateIdentity(1L, null, null, null));
        assertEquals(1, mapper.markSummaryReady(1L, null));
        assertNull(text("SELECT assignee_id FROM process_task WHERE id=1"));
        assertNull(text("SELECT assignee_name FROM process_task WHERE id=1"));
        assertNull(text("SELECT assignee_type FROM process_task WHERE id=1"));
        assertNull(text("SELECT start_user_id FROM process_task WHERE id=1"));
        assertEquals(1, number("SELECT inbox_identity_ready FROM process_task WHERE id=1"));
        assertEquals(1, number("SELECT inbox_summary_ready FROM process_task WHERE id=1"));
        String longName = "业务名称".repeat(2000);
        assertEquals(2, mapper.updateBusinessSummaries("asset", "record",
                new EntityTaskSummary(longName, "code", "data", "current", "active")));
        assertEquals(longName, text("SELECT business_name FROM process_task WHERE id=2"));
        assertNull(text("SELECT business_name FROM process_task WHERE id=3"));
        assertNull(text("SELECT business_name FROM process_task WHERE id=4"));
        assertEquals(1, mapper.updateBusinessSummary(1L, null));
        for (String column : List.of("business_name", "business_code", "business_data_name",
                "business_current_task_name", "business_status")) {
            assertNull(text("SELECT " + column + " FROM process_task WHERE id=1"));
        }
        assertEquals("todo", text("SELECT status FROM process_task WHERE id=1"));
        assertEquals("approve", text("SELECT action FROM process_task WHERE id=1"));
        assertEquals(0, mapper.updateIdentity(3L, "new", "new", "user"));
        assertEquals(0, mapper.markSummaryReady(3L, "new"));
        assertEquals(0, mapper.updateBusinessSummary(3L, EntityTaskSummary.empty()));
    }

    @Test
    void backfillUsesGroupedNullFlagsCursorAndDatabaseLimit() {
        insertTask(1, 0, null, 1);
        insertTask(2, 0, 1, null);
        insertTask(3, 0, 0, 1);
        insertTask(4, 0, 1, 0);
        insertTask(5, 0, 1, 1);
        insertTask(6, 1, null, 0);
        var mapper = session.getMapper(TaskInboxProjectionMapper.class);
        assertEquals(4, mapper.countUnready());
        assertEquals(List.of(2L, 3L), mapper.findUnready(1, 2));
        assertEquals(List.of(4L), mapper.findUnready(3, 10));
        assertTrue(mapper.findUnready(0, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.findUnready(0, -1));
        assertEquals(1L, mapper.findFirstBusinessTask("asset", "record"));
        assertNull(mapper.findFirstBusinessTask("asset", "missing"));
    }

    @Test
    void revisionUpdatesKeepExpectedRevisionNullsAndDatabaseTime() {
        jdbc.update("INSERT INTO process_definition_config (id,description,draft_revision) VALUES ('flow','old',4)");
        var process = session.getMapper(ProcessDefinitionConfigMapper.class);
        assertEquals(1, process.updateDraftCas("flow", 4, "name", null, "category", "<bpmn/>", "hash"));
        assertEquals(0, process.updateDraftCas("flow", 4, "stale", "old", null, null, null));
        assertEquals(5, number("SELECT draft_revision FROM process_definition_config"));
        assertNull(text("SELECT description FROM process_definition_config"));
        assertEquals("name", text("SELECT process_name FROM process_definition_config"));
        assertNotNull(text("SELECT update_time FROM process_definition_config"));
        jdbc.update("UPDATE process_definition_config SET deleted=1");
        assertEquals(0, process.updateDraftCas("flow", 5, "stale", null, null, null, null));

        jdbc.update("INSERT INTO entity_version_config (id,config_document,revision) VALUES ('config','old',2)");
        var entity = session.getMapper(EntityVersionConfigMapper.class);
        assertEquals(1, entity.updateCurrentIfRevision("config", 2, true, null, "actor"));
        assertEquals(0, entity.updateCurrentIfRevision("config", 2, false, "stale", null));
        assertEquals(3, number("SELECT revision FROM entity_version_config"));
        assertEquals(1, number("SELECT enabled FROM entity_version_config"));
        assertNull(text("SELECT config_document FROM entity_version_config"));
        assertEquals("actor", text("SELECT update_by FROM entity_version_config"));
        assertNotNull(text("SELECT update_time FROM entity_version_config"));
        jdbc.update("UPDATE entity_version_config SET deleted=1");
        assertEquals(0, entity.updateCurrentIfRevision("config", 3, false, "stale", null));
    }

    @Test
    void publishedProcessCatalogFiltersBeforeCountingAndPaginatesWithStableTies() {
        jdbc.update("""
                INSERT INTO process_definition_config (id,process_key,process_name,status,deleted,update_time)
                VALUES ('1','onboarding','Onboarding','PUBLISHED',0,?),
                       ('2','offboarding','Offboarding','PUBLISHED',0,?),
                       ('3','disabled','Offboarding','DISABLED',0,?),
                       ('4','removed','Offboarding','PUBLISHED',1,?)
                """, NOW, NOW, NOW, NOW);
        var catalog = new ProcessCatalogAdapter(session.getMapper(ProcessDefinitionConfigMapper.class),
                mock(ProcessVersionHistoryMapper.class));

        var first = catalog.pagePublished("boarding", new ProcessPageRequest(1, 1, null));
        var second = catalog.pagePublished("boarding", new ProcessPageRequest(2, 1, null));

        assertEquals(2, first.total());
        assertEquals("offboarding", first.items().get(0).processKey());
        assertEquals("onboarding", second.items().get(0).processKey());
    }

    @Test
    void entityLogicalDeletionRetainsBusinessDataAndHidesItFromNormalReads() {
        jdbc.execute("""
                CREATE TABLE wf_port_delete (
                  id VARCHAR(64) PRIMARY KEY, name VARCHAR(64), update_time TIMESTAMP, deleted INT DEFAULT 0)
                """);
        jdbc.update("INSERT INTO wf_port_delete (id,name) VALUES ('1','保留历史业务数据')");
        var mapper = session.getMapper(EntityDataDynamicMapper.class);

        assertEquals(1, mapper.deleteById("wf_port_delete", "1"));

        assertEquals(1, number("SELECT deleted FROM wf_port_delete WHERE id = '1'"));
        assertEquals("保留历史业务数据", text("SELECT name FROM wf_port_delete WHERE id = '1'"));
        assertNull(mapper.selectById("wf_port_delete", "1"));
    }

    @Test
    void entitySumFiltersByPublishedConditionsDataScopeAndSoftDeletion() {
        jdbc.execute("""
                CREATE TABLE wf_port_sum (
                  id VARCHAR(64), status VARCHAR(32), create_by VARCHAR(64),
                  amount DECIMAL(18, 4), deleted INT DEFAULT 0)
                """);
        jdbc.update("""
                INSERT INTO wf_port_sum VALUES
                  ('1','ACTIVE','user-1', 10.1015, 0), ('2','ACTIVE','user-1', 0.0005, 0),
                  ('3','INACTIVE','user-1', 20, 0), ('4','ACTIVE','user-2', 100, 0),
                  ('5','ACTIVE','user-1', 1000, 1), ('6','ACTIVE','user-1', NULL, 0)
                """);
        var field = new EntityField();
        field.setFieldCode("amount");
        field.setFieldType(EntityField.FieldType.DECIMAL);
        var filter = EntityQueryConditions.fromPublishedFields(Map.of("status", "ACTIVE", "status_op", "EQ"), List.of(field));
        var mapper = session.getMapper(EntityDataDynamicMapper.class);
        String scope = "create_by = #{permissionParameters.owner}";
        var sum = mapper.sumByCondition("wf_port_sum", "amount", filter, scope, Map.of("owner", "user-1"));
        assertEquals(0, new BigDecimal("10.102").compareTo(sum));
        assertEquals(0, mapper.sumByCondition("wf_port_sum", "amount", filter, scope, Map.of("owner", "missing")).signum());
    }

    @Test
    void outboxStatusProjectsOnlyStateAndRetryMetadataForExactEvent() {
        jdbc.execute("""
                CREATE TABLE workflow_outbox_event (
                  topic VARCHAR(64), event_key VARCHAR(64), status VARCHAR(20), retry_count INT,
                  max_retries INT, next_retry_time TIMESTAMP, processed_time TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO workflow_outbox_event VALUES
                  ('erp','event-1','FAILED',2,8,?,NULL), ('other','event-1','PROCESSED',0,8,NULL,?)
                """, NOW, NOW);
        WorkflowOutboxEventPort outbox = new DatabaseOutboxPublisher(session.getMapper(OutboxRecordMapper.class), null, null, null);
        var state = outbox.findStatus(" erp ", " event-1 ").orElseThrow();
        assertEquals(WorkflowOutboxEventPort.State.FAILED, state.state());
        assertEquals(2, state.retryCount());
        assertEquals(NOW, state.nextRetryTime());
        assertTrue(outbox.findStatus("erp", "missing").isEmpty());
    }

    @Test
    void slaMonitorRequiresPermissionAndFiltersBeforeCounting() {
        jdbc.execute("""
                CREATE TABLE process_task_sla (
                  id VARCHAR(64), task_id VARCHAR(64), process_instance_id VARCHAR(64),
                  process_definition_id VARCHAR(64), process_key VARCHAR(64), node_id VARCHAR(64),
                  node_name VARCHAR(64), business_key VARCHAR(64), entity_code VARCHAR(64),
                  entity_data_id VARCHAR(64), policy_code VARCHAR(64), policy_version INT,
                  policy_snapshot_json CLOB, calendar_code VARCHAR(64), calendar_version INT,
                  calendar_snapshot_json CLOB, timezone_id VARCHAR(64), current_assignee_id VARCHAR(64),
                  started_at TIMESTAMP, responded_at TIMESTAMP, completed_at TIMESTAMP,
                  response_due_at TIMESTAMP, completion_due_at TIMESTAMP, response_remaining_minutes INT,
                  completion_remaining_minutes INT, response_status VARCHAR(20), completion_status VARCHAR(20),
                  overall_status VARCHAR(20), pause_started_at TIMESTAMP, version INT,
                  create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO process_task_sla (id, task_id, process_instance_id, process_key, overall_status, policy_version)
                VALUES ('1','task-1','first','workflow','RUNNING',1),
                       ('2','task-2','first','workflow','BREACHED',1),
                       ('3','task-3','other','workflow','BREACHED',1)
                """);
        ProcessTaskSlaPort sla = new ProcessTaskSlaAdapter(null,
                new TaskSlaMonitorService(session.getMapper(ProcessTaskSlaMapper.class)));
        var query = new ProcessTaskSlaPort.MonitorQuery(1, 1, null, "workflow", "first", null, null);
        var page = sla.pageMonitor(query);
        assertEquals(2, page.total());
        assertEquals("task-2", page.items().get(0).sla().taskId());
        assertEquals(Map.of("RUNNING", 1L, "BREACHED", 1L, "PAUSED", 0L, "COMPLETED", 0L),
                sla.countMonitorByStatus("workflow", "first"));
    }

    private void insertTask(long id, int deleted, Integer summaryReady, Integer identityReady) {
        jdbc.update("""
                INSERT INTO process_task (id,task_id,process_instance_id,action,action_label,comment,status,
                  entity_code,entity_data_id,assignee_id,assignee_name,assignee_type,start_user_id,
                  inbox_summary_ready,inbox_identity_ready,deleted)
                VALUES (?,?,'process','approve','同意','comment','todo','asset','record','old','old','user','starter',?,?,?)
                """, id, "task-" + id, summaryReady, identityReady, deleted);
    }

    private String text(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }

    private long number(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
