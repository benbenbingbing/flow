package com.workflow.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.admin.externalsystem.infrastructure.persistence.mapper.ExternalSystemMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.contracts.entity.model.EntityTaskSummary;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxProjectionMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
                  status VARCHAR(16), address VARCHAR(128), description VARCHAR(128), version BIGINT,
                  updated_by VARCHAR(64), update_time TIMESTAMP, deleted INT DEFAULT 0)
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
                  category VARCHAR(64), bpmn_xml CLOB, draft_hash VARCHAR(64), draft_revision BIGINT,
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
        for (Class<?> type : List.of(ExternalSystemMapper.class, SysUserMapper.class, ProcessTaskMapper.class,
                TaskInboxProjectionMapper.class, ProcessDefinitionConfigMapper.class, EntityVersionConfigMapper.class)) {
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
