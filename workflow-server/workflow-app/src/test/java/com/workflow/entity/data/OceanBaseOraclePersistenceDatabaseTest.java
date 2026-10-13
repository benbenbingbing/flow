package com.workflow.entity.data;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.core.database.jdbc.InitializedDriverDataSource;
import com.workflow.core.database.jdbc.OceanBaseOracleJdbcTemplate;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.excel.runtime.EntityExcelJobRepository;
import com.workflow.entity.excel.runtime.EntityExcelRuntimeResolver;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormPublished;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.process.instance.infrastructure.persistence.mapper.StartedProcessPageMapper;
import com.workflow.process.task.application.model.TaskInboxQuery;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskHandoverMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 显式启用的 OB Oracle 生产持久化集成测试。建表定义直接取自 docs/sql/oboracle.sql，
 * 仅替换表名、索引名及约束名建立 ORAP_ 隔离副本；被测 Mapper/Repository SQL 不做语法改写。
 * 需要 FLOW_OB_ORACLE_TEST_URL/USER/PASSWORD，不会默认连接数据库，不写平台表或原有记录。
 */
@EnabledIfEnvironmentVariable(named = "FLOW_OB_ORACLE_TEST_URL", matches = ".+")
class OceanBaseOraclePersistenceDatabaseTest {
    @Test
    void unnamedBpmnNodesAndDraftReplacementsPersistThroughNodeConfigMapper() throws Exception {
        try (var f = new Fixture("PROCESS_NODE_CONFIG")) {
            var nodes = f.mappers(com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper.class)
                    .getMapper(com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper.class);
            var json = new ObjectMapper();
            var service = new com.workflow.process.definition.application.ProcessDefinitionNodeSyncService(
                    nodes, null, null, json, null, null, null, null, null, f.jdbc,
                    new com.workflow.process.definition.application.ProcessBpmnNodeParser(json));
            List<String> tags = List.of("startEvent", "endEvent", "userTask", "serviceTask", "scriptTask", "sendTask",
                    "receiveTask", "manualTask", "businessRuleTask", "exclusiveGateway", "parallelGateway", "inclusiveGateway",
                    "eventBasedGateway", "callActivity", "subProcess");
            StringBuilder xml = new StringBuilder("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><bpmn:process id=\"oracle_nodes\">");
            for (int i = 0; i < tags.size(); i++) {
                String name = i % 3 == 0 ? "" : i % 3 == 1 ? " name=\"\"" : " name=\"  \t\"";
                xml.append("<bpmn:").append(tags.get(i)).append(" id=\"").append(tags.get(i)).append("\"").append(name).append(" />");
            }
            xml.append("</bpmn:process></bpmn:definitions>");
            service.parseAndSaveNodeConfigs("42", xml.toString());
            var created = nodes.findByProcessConfigId("42");
            assertEquals(tags.size(), created.size());
            assertEquals(Set.of(com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig.NodeType.values()),
                    new HashSet<>(created.stream().map(com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig::getNodeType).toList()));
            created.forEach(node -> assertEquals(node.getNodeId(), node.getNodeName()));
            // 草稿更新重建同一批节点：清除名称也应回退节点 ID，有意义名称须保持原值。
            var drafts = new ArrayList<com.workflow.process.configuration.api.model.NodeConfigDTO>();
            for (var node : created) {
                var draft = new com.workflow.process.configuration.api.model.NodeConfigDTO();
                draft.setId(node.getId()); draft.setNodeId(node.getNodeId()); draft.setNodeType(node.getNodeType());
                draft.setNodeName(node.getNodeType().ordinal() % 2 == 0 ? " \t " : "明确名称 " + node.getNodeId());
                drafts.add(draft);
            }
            service.syncDraftNodes("42", drafts);
            var updated = nodes.findByProcessConfigId("42");
            assertEquals(tags.size(), updated.size());
            updated.forEach(node -> assertEquals(node.getNodeType().ordinal() % 2 == 0
                    ? node.getNodeId() : "明确名称 " + node.getNodeId(), node.getNodeName()));
        }
    }

    @Test
    void applicationJdbcNullsSupportStorageIncidentsAndArrayBatches() throws Exception {
        try (var f = new Fixture("STORAGE_FILE_OBJECT", "PROCESS_ASSIGNEE_INCIDENT", "PROCESS_ASSIGNEE_INCIDENT_ACTION")) {
            var vendor = DatabaseVendor.OCEANBASE_ORACLE;
            var attempt = new com.workflow.core.database.jdbc.JdbcWriteAttempt(f.jdbc, DatabaseDialects.insert(vendor));
            var dialect = com.workflow.integration.database.api.query.DatabaseQueryDialects.forVendor(vendor);
            var files = new com.workflow.storage.application.StoredFileAccessService(f.jdbc, null, null, attempt, dialect);
            var incidents = new com.workflow.process.assignment.application.AssigneeIncidentRecorder(f.jdbc,
                    new ObjectMapper(), dialect, attempt);
            f.tx.executeWithoutResult(status -> {
                // 无幂等键/原始文件名/内容类型是合法上传；这里调用生产服务而非复制其 INSERT。
                var registration = files.register(Map.of("url", "test:oracle-null", "filename", "oracle-null"),
                        new org.springframework.mock.web.MockMultipartFile("file", null, null, new byte[]{1}),
                        new com.workflow.storage.application.StoredFileAccessService.UploadClaim("user-1", null, null, null));
                assertTrue(registration.currentObjectRegistered());
                var file = f.jdbc.queryForMap("SELECT * FROM storage_file_object WHERE storage_key=?", "oracle-null");
                assertNull(file.get("idempotency_key")); assertNull(file.get("request_hash")); assertNull(file.get("content_type"));
                var command = new com.workflow.process.assignment.application.AssigneeIncidentRecorder.CreateCommand(
                        null, null, null, null, "node-1", null, "BLOCK_PUBLISH", "OPEN", "NO_USERS", null,
                        null, null, null, null, "admin", 0, 5, 1.0, null, null);
                String id = incidents.create(command);
                assertEquals(id, incidents.create(command));
                var incident = f.jdbc.queryForMap("SELECT * FROM process_assignee_incident WHERE id=?", id);
                assertNull(incident.get("task_id")); assertNull(incident.get("next_retry_at")); assertNull(incident.get("detail_json"));
                // 批处理不经过普通参数工厂，也必须支持未声明和显式声明的 null。
                assertEquals(1, f.jdbc.batchUpdate("UPDATE storage_file_object SET original_name=?,content_type=? WHERE id=?",
                        List.<Object[]>of(new Object[]{null, new org.springframework.jdbc.core.SqlParameterValue(
                                org.springframework.jdbc.core.SqlTypeValue.TYPE_UNKNOWN, null), file.get("id")}))[0]);
                assertEquals(1, f.jdbc.batchUpdate("UPDATE process_assignee_incident SET next_retry_at=?,detail_json=? WHERE id=?",
                        List.<Object[]>of(new Object[]{null, null, id}),
                        new int[]{java.sql.Types.TIMESTAMP, org.springframework.jdbc.core.SqlTypeValue.TYPE_UNKNOWN, java.sql.Types.VARCHAR})[0]);
                assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM storage_file_object WHERE storage_key=?", Integer.class, (Object) null));
                status.setRollbackOnly();
            });
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM storage_file_object", Integer.class));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM process_assignee_incident", Integer.class));
        }
    }

    @Test
    void currentFormPublicationMapperPreservesClobLocksDefaultAndRollback() throws Exception {
        try (var f = new Fixture("ENTITY_FORM_PUBLISHED", "ENTITY_FORM_PUBLICATION_LOCK", "ENTITY_FORM_CURRENT_DEFAULT")) {
            f.jdbc.update("INSERT INTO entity_form_publication_lock(lock_id) VALUES(1)");
            var mapper = f.mappers(EntityFormPublishedMapper.class).getMapper(EntityFormPublishedMapper.class);
            String document = "{\"text\":\"" + "中文".repeat(5000) + "\"}";
            var row = new EntityFormPublished(); row.setFormId("form-1"); row.setEntityId("entity-1");
            row.setSnapshotDocument(document); row.setContentHash("a".repeat(64)); row.setPublishedAt(LocalDateTime.now());
            f.tx.executeWithoutResult(status -> {
                assertEquals(1, mapper.lockPublication());
                assertEquals(1, mapper.insert(row));
                assertEquals(document, mapper.selectById("form-1").getSnapshotDocument());
                assertEquals(1, mapper.insertDefault("entity-1", "form-1"));
                assertEquals(1, mapper.removeDefaultForm("form-1"));
                assertEquals(1, mapper.insertDefault("entity-1", "form-1"));
                assertEquals(1, mapper.clearDefault("entity-1"));
                row.setSnapshotDocument("{\"updated\":true}");
                assertEquals(1, mapper.updateById(row));
                assertEquals(row.getSnapshotDocument(), mapper.selectById("form-1").getSnapshotDocument());
                status.setRollbackOnly();
            });
            assertNull(mapper.selectById("form-1"));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM entity_form_current_default", Integer.class));
        }
    }

    @Test
    void excelConfigurationAndJobsExecuteTheirProductionRepositories() throws Exception {
        try (var f = new Fixture("ENTITY_EXCEL_SETTINGS", "ENTITY_EXCEL_SCHEME", "ENTITY_EXCEL_SCHEME_PUBLISHED",
                "ENTITY_EXCEL_JOB", "ENTITY_EXCEL_JOB_ROW", "SYS_DICT", "ENTITY_FORM_PUBLISHED", "ENTITY_LIST_CONFIG",
                "UI_CONFIG_RELEASE", "UI_EXTENSION_DEFINITION", "ENTITY_FORM")) {
            var repository = new ExcelConfigurationRepository(f.jdbc);
            String document = "{\"text\":\"" + "中文".repeat(5000) + "\"}";
            var row = new ExcelConfigurationRepository.SchemeRow();
            row.setId("scheme-1"); row.setEntityId("entity-1"); row.setSchemeCode("default"); row.setName("导入方案"); row.setDraftJson(document);
            repository.insertScheme(row);
            assertEquals(document, repository.scheme("entity-1", "default").getDraftJson());
            row.setName("更新方案"); repository.updateScheme(row);
            repository.publish(row, document, "a".repeat(64), "form-1", "user-1");
            assertEquals(document, repository.schemes("entity-1").get(0).getDocumentJson());
            assertTrue(repository.publishedSchemeUsesForm("form-1"));
            repository.status("scheme-1", "DISABLED"); assertFalse(repository.publishedSchemeUsesForm("form-1"));
            repository.rebindPublishedForm("scheme-1", "form-2", "b".repeat(64), "user-1");
            repository.status("scheme-1", "ACTIVE"); assertTrue(repository.publishedSchemeUsesForm("form-2"));
            repository.saveSettings("entity-1", document, false);
            repository.saveSettings("entity-1", "{\"draft\":true}", true);
            repository.publishSettings("entity-1", document, "c".repeat(64), "d".repeat(64), "user-1");
            assertEquals(document, repository.settings("entity-1").getPublishedJson());
            // 未启用导入/导出与系统发布可缺少关联身份，验证真实 JDBC 空值绑定。
            repository.publish(row, document, "a".repeat(64), null, null);
            assertNull(repository.scheme("entity-1", "default").getWriteFormId());
            repository.rebindPublishedForm("scheme-1", null, "b".repeat(64), null);
            repository.publishSettings("entity-1", document, null, null, null);
            assertNull(repository.settings("entity-1").getImportHash());
            assertNull(repository.settings("entity-1").getExportHash());
            assertFalse(repository.dictionaryAvailable("missing"));
            assertNull(repository.excelInterfaceConfiguration("missing", "legacy"));
            assertTrue(repository.snapshots("entity-1").isEmpty());
            var jobs = new EntityExcelJobRepository(f.jdbc, new ObjectMapper(), f.manager);
            var scheme = new ExcelResolvedScheme("entity-1", "entity_code", "default", null, "a".repeat(64), "", "form-1");
            var plan = new EntityExcelRuntimeResolver.Plan(null, scheme, null, null, null);
            UserContext.setCurrentUser("user-1", "oracle-tester");
            try {
                var first = jobs.claim(plan, "IMPORT", "LIST:test", "request-1", "e".repeat(64));
                assertTrue(first.acquired());
                var replay = jobs.claim(plan, "IMPORT", "LIST:test", "request-1", "e".repeat(64));
                assertFalse(replay.acquired()); assertEquals(first.jobId(), replay.jobId());
                assertEquals("RUNNING", jobs.requireOwned(first.jobId()).get("status"));
                jobs.inputFile(first.jobId(), "file-1"); jobs.progress(first.jobId(), Map.of("processed", 1));
                f.tx.executeWithoutResult(status -> {
                    jobs.lockRunning(first.jobId());
                    jobs.saveErrors(first.jobId(), List.of(Map.of("sheetNo", 0, "rowNumber", 2, "message", "测试错误")));
                    jobs.finish(first.jobId(), "FAILED", Map.of("failed", 1), null, "测试.xlsx", "error-file", "测试错误");
                });
                assertEquals("FAILED", jobs.requireOwned(first.jobId()).get("status"));
                assertEquals(1, jobs.errors(first.jobId(), 1, 10).get("total"));
                assertFalse(jobs.lockIfRunning(first.jobId()));
                var success = jobs.claim(plan, "IMPORT", "LIST:test", "request-2", "e".repeat(64));
                jobs.inputFile(success.jobId(), null);
                jobs.finish(success.jobId(), "SUCCEEDED", Map.of("savedRowCount", 1), null, null, null, null);
                assertEquals("SUCCEEDED", jobs.requireOwned(success.jobId()).get("status"));
                assertNull(jobs.requireOwned(success.jobId()).get("error_message"));
            } finally { UserContext.clear(); }
            repository.delete("scheme-1"); assertTrue(repository.schemes("entity-1").isEmpty());
        }
    }

    @Test
    void taskInboxHandoverAndFlowableHistoryMappersExecuteOraclePagination() throws Exception {
        try (var f = new Fixture("PROCESS_TASK", "ACT_RU_TASK", "ACT_RU_IDENTITYLINK", "ACT_HI_PROCINST",
                "SYS_USER", "SYS_GROUP", "SYS_ROLE", "SYS_USER_GROUP", "SYS_USER_ROLE", "PROCESS_TASK_ADD_SIGN",
                "PROCESS_TASK_ADD_SIGN_USER", "PROCESS_TASK_CANDIDATE_USER", "PROCESS_TASK_CANDIDATE_GROUP")) {
            f.jdbc.update("INSERT INTO sys_user(id,username,nickname,password) VALUES('user-1','alice','审批人','unused')");
            for (int i = 1; i <= 5; i++) {
                f.jdbc.update("INSERT INTO ACT_RU_TASK(ID_,REV_,ASSIGNEE_,PROC_INST_ID_) VALUES(?,1,'alice','process-1')", "task-" + i);
                f.jdbc.update("INSERT INTO process_task(id,task_id,node_id,node_name,process_instance_id,process_definition_id,process_key,"
                        + "process_name,entity_code,entity_data_id,assignee_id,assignee_type,status,create_time,start_user_id,"
                        + "business_name,business_code,inbox_summary_ready,inbox_identity_ready) "
                        + "VALUES(?,?, 'review','审批','process-1','def-1','test','测试流程','entity','record','alice','user','todo',?,'alice','事项','B-1',1,1)",
                        i, "task-" + i, LocalDateTime.of(2026, 10, 1, 0, 0).plusSeconds(i));
                f.jdbc.update("INSERT INTO ACT_HI_PROCINST(ID_,PROC_INST_ID_,PROC_DEF_ID_,START_TIME_,START_USER_ID_) VALUES(?,?,'def-1',?,'alice')",
                        "history-" + i, "process-" + i, LocalDateTime.of(2026, 10, 1, 0, 0).plusSeconds(i));
            }
            var mappers = f.mappers(TaskInboxMapper.class, TaskHandoverMapper.class, StartedProcessPageMapper.class, ProcessTaskMapper.class);
            var inbox = mappers.getMapper(TaskInboxMapper.class);
            var query = new TaskInboxQuery("user-1", "todo", 2, 2, null, null, null, null, null);
            assertEquals(5, inbox.count(query)); assertEquals(0, inbox.countUnready(query));
            assertEquals(List.of("task-3", "task-2"), inbox.selectPage(query).stream().map(row -> row.getTaskId()).toList());
            var filtered = new TaskInboxQuery("user-1", "todo", 1, 2, "事项", "审批人", null, null, null);
            assertEquals(5, inbox.count(filtered)); assertEquals(2, inbox.selectPage(filtered).size());
            var handover = mappers.getMapper(TaskHandoverMapper.class);
            assertEquals(5, handover.count("user-1"));
            assertEquals(List.of("task-3", "task-2"), handover.selectPage(new Page<>(2, 2, false), "user-1").stream().map(row -> row.getTaskId()).toList());
            assertEquals(5, handover.selectAllTaskIds("user-1").size());
            assertEquals("ASSIGNED", handover.findEligible("user-1", "task-1").getAssignmentType());
            var history = mappers.getMapper(StartedProcessPageMapper.class);
            assertEquals(5, history.count("alice", Set.of("def-1"), null, null));
            assertEquals(List.of("process-3", "process-2"), history.page("alice", Set.of("def-1"), null, null, 2, 2));
            var tasks = mappers.getMapper(ProcessTaskMapper.class);
            assertEquals(1, tasks.completeTask(1L, "done", "APPROVE", "Oracle 备注 O'Reilly", 100L));
            assertEquals("Oracle 备注 O'Reilly", tasks.selectByTaskIdForUpdate("task-1").getComment());
        }
    }

    @Test
    void baseMapperAndLambdaQueriesQuoteReservedColumnsForReadsAndWrites() throws Exception {
        try (var f = new Fixture("PROCESS_TASK")) {
            f.jdbc.update("INSERT INTO process_task(id,process_instance_id,process_definition_id,process_key,node_id,task_id,\"COMMENT\") "
                    + "VALUES(1001,'process-1','def-1','test','review','existing-task','原始备注')");
            var tasks = f.mappers(ProcessTaskMapper.class).getMapper(ProcessTaskMapper.class);
            var row = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
            row.setId(1002L); row.setProcessInstanceId("process-1"); row.setProcessDefinitionId("def-1");
            row.setProcessKey("test"); row.setNodeId("review"); row.setTaskId("inserted-task"); row.setComment("新增备注 O'Reilly");
            // 独立路径一起报告，不能因首次读取失败而遗漏生成式写入 SQL 的同类错误。
            assertAll("BaseMapper/Wrapper COMMENT", () -> {
                assertEquals(1, tasks.insert(row));
                assertEquals(row.getComment(), tasks.selectByTaskIdForUpdate("inserted-task").getComment());
            }, () -> assertEquals("原始备注", tasks.selectByTaskId("existing-task").getComment()), () -> {
                var history = tasks.selectProgressHistoryByProcessInstanceId("process-1");
                assertTrue(history.stream().anyMatch(item -> "原始备注".equals(item.getComment())));
            }, () -> {
                var changed = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
                changed.setId(1001L); changed.setComment("更新备注");
                assertEquals(1, tasks.updateById(changed));
                assertEquals("更新备注", tasks.selectByTaskIdForUpdate("existing-task").getComment());
            }, () -> {
                // 不预置 ID，才能验证 Oracle RETURNING 输出绑定和 MP 自动主键回填，而非只验证 INSERT 成功。
                var generated = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
                generated.setProcessInstanceId("process-1"); generated.setProcessDefinitionId("def-1");
                generated.setProcessKey("test"); generated.setNodeId("review"); generated.setTaskId("generated-task");
                generated.setComment("自动主键备注");
                assertNull(generated.getId());
                assertEquals(1, tasks.insert(generated));
                assertNotNull(generated.getId());
                assertTrue(generated.getId() > 0);
                assertEquals(generated.getId(), tasks.selectByTaskIdForUpdate("generated-task").getId());
            });
        }
    }

    /** 只替换测试表的坐标，保留生产 SQL 的表达式、绑定和结果映射，隔离包括 Repository 的 JDBC 调用。 */
    private static final class Fixture implements AutoCloseable {
        final com.zaxxer.hikari.HikariDataSource pool = new com.zaxxer.hikari.HikariDataSource();
        final Map<String, String> tables = new LinkedHashMap<>();
        final List<String> created = new ArrayList<>();
        final String prefix = "ORAP_M_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        final JdbcTemplate raw;
        final DataSource source;
        final JdbcTemplate jdbc;
        final DataSourceTransactionManager manager;
        final TransactionTemplate tx;

        Fixture(String... logicalTables) throws Exception {
            pool.setDataSource(new InitializedDriverDataSource(DatabaseVendor.OCEANBASE_ORACLE, System.getenv("FLOW_OB_ORACLE_TEST_URL"),
                    System.getenv("FLOW_OB_ORACLE_TEST_USER"), System.getenv("FLOW_OB_ORACLE_TEST_PASSWORD"), "com.oceanbase.jdbc.Driver", null));
            pool.setMaximumPoolSize(2); pool.setMinimumIdle(0); pool.setPoolName("oracle-production-mappers");
            raw = new OceanBaseOracleJdbcTemplate(pool); raw.setQueryTimeout(30);
            for (String logical : logicalTables) tables.put(logical, prefix + "_" + tables.size());
            source = new DelegatingDataSource(pool) {
                @Override public Connection getConnection() throws SQLException { return isolated(super.getConnection()); }
                @Override public Connection getConnection(String user, String password) throws SQLException { return isolated(super.getConnection(user, password)); }
            };
            jdbc = new OceanBaseOracleJdbcTemplate(source); jdbc.setQueryTimeout(30);
            manager = new DataSourceTransactionManager(source); tx = new TransactionTemplate(manager);
            try {
                String script = Files.readString(bootstrap());
                for (var table : tables.entrySet()) {
                    var definition = Pattern.compile("CREATE TABLE \\\"" + table.getKey() + "\\\" \\((.*?)\\n\\);", Pattern.DOTALL).matcher(script);
                    if (!definition.find()) throw new IllegalArgumentException("Bootstrap table missing: " + table.getKey());
                    // 同一表可能同时有主键和 CHECK，隔离命名必须逐个唯一，保持原约束表达式。
                    var constraintNumber = new java.util.concurrent.atomic.AtomicInteger();
                    String ddl = Pattern.compile("CONSTRAINT \\\"[^\\\"]+\\\"").matcher(definition.group())
                            .replaceAll(match -> "CONSTRAINT \"" + table.getValue() + "_C" + constraintNumber.getAndIncrement() + "\"");
                    raw.execute(rewrite(ddl.substring(0, ddl.length() - 1))); created.add(table.getValue());
                }
                var indexes = Pattern.compile("CREATE (?:UNIQUE )?INDEX \\\"[^\\\"]+\\\" ON \\\"([^\\\"]+)\\\" \\([^;]+\\);", Pattern.DOTALL).matcher(script);
                int n = 0;
                while (indexes.find()) if (tables.containsKey(indexes.group(1))) {
                    String ddl = indexes.group().replaceFirst("INDEX \\\"[^\\\"]+\\\"", "INDEX \\\"" + prefix + "_I" + n++ + "\\\"");
                    raw.execute(rewrite(ddl.substring(0, ddl.length() - 1)));
                }
            } catch (Exception error) { close(); throw error; }
        }

        SqlSessionTemplate mappers(Class<?>... types) throws Exception {
            var dialect = DatabaseDialects.forVendor(DatabaseVendor.OCEANBASE_ORACLE);
            var database = new DatabaseMybatisConfiguration();
            var properties = new com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties();
            database.oracleIdentifierMappings(dialect).customize(properties);
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config, properties.getGlobalConfig());
            config.setDatabaseId("OCEANBASE_ORACLE"); config.setDefaultStatementTimeout(30);
            database.nullParameterBindings().customize(config); database.largeTextBindings().customize(config); database.numericBooleanBindings().customize(config);
            config.addInterceptor(database.mybatisPlusInterceptor(dialect));
            for (Class<?> type : types) config.addMapper(type);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            factory.setGlobalConfig(properties.getGlobalConfig());
            return new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        }

        private Connection isolated(Connection actual) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (args != null && args.length > 0 && args[0] instanceof String sql && method.getName().startsWith("prepare")) args[0] = rewrite(sql);
                try {
                    Object result = method.invoke(actual, args);
                    if (method.getName().equals("createStatement")) return Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(), new Class<?>[]{java.sql.Statement.class}, (p, m, a) -> {
                        if (a != null && a.length > 0 && a[0] instanceof String sql) a[0] = rewrite(sql);
                        try { return m.invoke(result, a); } catch (InvocationTargetException error) { throw error.getCause(); }
                    });
                    return result;
                } catch (InvocationTargetException error) { throw error.getCause(); }
            });
        }

        private String rewrite(String sql) {
            for (var table : tables.entrySet()) sql = sql.replaceAll("(?i)\\b" + Pattern.quote(table.getKey()) + "\\b", table.getValue());
            return sql;
        }

        /** Maven 模块及仓库根目录运行均可解析同一交付脚本，不复制一套可能过期的表定义。 */
        private static Path bootstrap() {
            for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
                Path path = root.resolve("docs/sql/oboracle.sql"); if (Files.isRegularFile(path)) return path;
            }
            throw new IllegalStateException("docs/sql/oboracle.sql not found");
        }

        @Override public void close() {
            RuntimeException failure = null;
            var reverse = new ArrayList<>(created); Collections.reverse(reverse);
            try {
                for (String name : reverse) try { raw.execute("DROP TABLE \"" + name + "\" PURGE"); }
                catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            } finally { pool.close(); }
            if (failure != null) throw failure;
        }
    }
}
