package com.workflow.migration.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.model.*;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.*;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.*;
import com.workflow.entity.definition.infrastructure.persistence.record.*;
import com.workflow.entity.history.*;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.process.archive.ProcessArchiveService;
import com.workflow.process.archive.infrastructure.persistence.ProcessArchiveMapper;
import com.workflow.entity.history.infrastructure.persistence.EntityHistorySourceMapper;
import com.workflow.migration.history.infrastructure.persistence.HistoryImportBatchMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 JDBC 事务、V124 表结构、来源去重与档案存储；动态实体物理写入端口使用隔离测试表。 */
class HistoryImportTransactionTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    HistoryImportService service;
    EntityHistoryImportService entities;
    ProcessArchiveService archives;
    EntityHistoryWriteGuard guard;
    HistoryImportWorkbook workbook;
    EntityPublishedSnapshot snapshot;
    EntityRelationRuntimeService relations;
    EntityDataDynamicService records;
    MockedStatic<PermissionUtil> permissions;
    ObjectMapper json = new ObjectMapper();
    AtomicBoolean failWrite = new AtomicBoolean(false);
    final Plan plan = new Plan("旧报销", "legacy", List.of(new EntitySpec("main", "expense", true, Map.of())), "v1", Map.of("经理", "review"), Map.of("通过", "APPROVED"));

    @BeforeEach void setup() throws Exception {
        permissions = mockStatic(PermissionUtil.class); permissions.when(() -> PermissionUtil.hasPermission(anyString())).thenReturn(true);
        UserContext.setCurrentUser("u1", "importer");
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:history_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(ds); var manager = new DataSourceTransactionManager(ds); tx = new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE sys_menu(id VARCHAR(64) PRIMARY KEY,parent_id VARCHAR(64),menu_name VARCHAR(200),menu_type VARCHAR(10),path VARCHAR(200),component VARCHAR(200),icon VARCHAR(30),sort INT,visible VARCHAR(2),status VARCHAR(2),perm VARCHAR(200),create_time TIMESTAMP)");
        String migration = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V124__historical_entity_and_process_import.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        try (var connection = ds.getConnection()) { ScriptUtils.executeSqlScript(connection, new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8))); }
        jdbc.execute("CREATE TABLE test_business(id VARCHAR(64) PRIMARY KEY,name VARCHAR(200))");
        // 真实 MyBatis 代理通过 SpringManagedTransaction 加入同一 JDBC 事务，验证 Mapper 的实际 SQL。
        var factoryBean = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(ds);
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(HistoryImportBatchMapper.class); configuration.addMapper(EntityHistorySourceMapper.class); configuration.addMapper(ProcessArchiveMapper.class);
        factoryBean.setConfiguration(configuration);
        var pagination = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));
        factoryBean.setPlugins(pagination);
        var session = new org.mybatis.spring.SqlSessionTemplate(factoryBean.getObject());
        var batchMapper = session.getMapper(HistoryImportBatchMapper.class);
        var sourceMapper = session.getMapper(EntityHistorySourceMapper.class);
        var archiveMapper = session.getMapper(ProcessArchiveMapper.class);
        var definitions = mock(EntityDefinitionMapper.class); var snapshots = mock(EntityPublishedSnapshotService.class);
        var access = mock(EntityActionCapabilityService.class); var mutations = mock(EntityMutationPort.class);
        records = mock(EntityDataDynamicService.class); relations = mock(EntityRelationRuntimeService.class);
        var rules = mock(EntityCodeRuleMapper.class); var tables = mock(DynamicTableService.class);
        var definition = new EntityDefinition(); definition.setEntityCode("expense"); definition.setStatus(EntityDefinition.Status.PUBLISHED); definition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(definitions.findByEntityCode("expense")).thenReturn(Optional.of(definition));
        when(tables.tableExists("expense")).thenReturn(true); when(rules.findByEntityCode("expense")).thenReturn(Optional.empty());
        snapshot = new EntityPublishedSnapshot(); snapshot.setVersion(1); snapshot.setEntityCode("expense"); snapshot.setEntityName("报销"); snapshot.setRelations(List.of());
        var name = new EntityField(); name.setFieldCode("name"); name.setFieldName("名称"); name.setFieldType(EntityField.FieldType.STRING); name.setIsRequired(true);
        snapshot.setFields(List.of(name)); when(snapshots.getLatestByEntityCode("expense")).thenReturn(snapshot);
        guard = new EntityHistoryWriteGuard(sourceMapper);
        entities = new EntityHistoryImportService(definitions, snapshots, access, mutations, records, sourceMapper, json, relations, rules, tables);
        when(mutations.execute(any())).thenAnswer(call -> {
            EntityMutationCommand command = call.getArgument(0);
            assertFalse((Boolean)command.payload().get("startProcess"));
            assertTrue(HistoryRelationValidationScope.deferred(), "只在受控创建调用内延迟关系检查");
            String value = command.payload().get("name").toString();
            if (value.equals("失败行") && failWrite.get()) throw new IllegalArgumentException("模拟字段约束失败");
            String id = UUID.randomUUID().toString(); jdbc.update("INSERT INTO test_business VALUES (?,?)", id, value);
            return new EntityMutationResult(command.operationId(), "expense", id, EntityMutationOperationType.CREATE, Map.of("name", value), 1, "HISTORY", true, false);
        });
        when(records.findAccessibleById(eq("expense"), anyString(), isNull())).thenAnswer(call -> {
            var names = jdbc.query("SELECT name FROM test_business WHERE id=?", (rs, n) -> rs.getString(1), (String)call.getArgument(1));
            if (names.isEmpty()) throw new ForbiddenException("不可读");
            var record = new EntityDataDTO(); record.setName(names.get(0)); record.setData(Map.of("name", names.get(0))); return record;
        });
        var versions = mock(ProcessVersionHistoryMapper.class); var version = new ProcessVersionHistory();
        version.setId("v1"); version.setProcessName("新审批图"); version.setVersion(1); version.setDeleted(0);
        version.setBpmnXml("<definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL'><process id='p'><userTask id='review' name='审批'/></process></definitions>");
        when(versions.selectById("v1")).thenReturn(version);
        archives = new ProcessArchiveService(versions, entities, archiveMapper, json);
        workbook = new HistoryImportWorkbook(entities);
        service = new HistoryImportService(batchMapper, json, new HistoryImportPlanner(entities, archives), workbook, entities, archives, manager);
    }
    @AfterEach void cleanup() { permissions.close(); UserContext.clear(); }

    Map<String,Object> batch(boolean withHistory, String... names) throws Exception {
        var created = tx.execute(status -> service.create(plan)); String id = created.get("id").toString();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook.template(plan, "entities")))) {
            for (int i = 0; i < names.length; i++) {
                var row = book.getSheetAt(0).createRow(i + 1); row.createCell(0).setCellValue("00" + i); row.createCell(1).setCellValue("group1"); row.createCell(2).setCellValue(names[i]);
            }
            var file = file(book); tx.execute(status -> { try { return service.upload(id, 1, "entities", file); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
        if (withHistory) try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook.template(plan, "history")))) {
            String[] summary = {"P1", "main", "000", "历史报销", "张三", "2020-01-01 10:00:00", "2020-01-02 10:00:00", "APPROVED", "2"};
            var row = book.getSheetAt(0).createRow(1); for (int i = 0; i < summary.length; i++) row.createCell(i).setCellValue(summary[i]);
            for (int e = 0; e < 2; e++) {
                String[] event = {"E" + e, "P1", "经理", "审批人" + e, "通过", "同意", "2020-01-02 09:00:00", "" + (e + 1)};
                var eventRow = book.getSheetAt(1).createRow(e + 1); for (int i = 0; i < event.length; i++) eventRow.createCell(i).setCellValue(event[i]);
            }
            var file = file(book); tx.execute(status -> { try { return service.upload(id, 2, "history", file); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
        return tx.execute(status -> service.validate(id, withHistory ? 3 : 2));
    }
    MockMultipartFile file(XSSFWorkbook book) throws IOException { var out = new ByteArrayOutputStream(); book.write(out); return new MockMultipartFile("file", "test.xlsx", "application/octet-stream", out.toByteArray()); }
    Map<String,Object> execute(Map<String,Object> batch, boolean retry) { return service.executeNext(batch.get("id").toString(), batch.get("validationHash").toString(), "group1", retry); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void lateFailureRollsBackEveryEntityAndSourceLinkThenCanRetry() throws Exception {
        var batch = batch(false, "第一行", "失败行"); failWrite.set(true);
        assertThrows(IllegalArgumentException.class, () -> execute(batch, false));
        assertEquals(0, count("test_business")); assertEquals(0, count("entity_import_source_link"));
        assertEquals("FAILED", service.get(batch.get("id").toString()).get("state"));
        assertFalse(HistoryRelationValidationScope.deferred());
        failWrite.set(false); assertEquals("SUCCEEDED", execute(batch, true).get("state"));
        assertEquals(2, count("test_business")); execute(batch, true); assertEquals(2, count("test_business"));
    }
    @Test void beforeCommitRelationshipFailureAlsoRollsBackArchiveAndAllowsRetry() throws Exception {
        var batch = batch(true, "业务记录");
        doThrow(new IllegalArgumentException("必填关系缺少子记录")).when(relations).validateImportedRelations(anyString(), anyString());
        assertThrows(IllegalArgumentException.class, () -> execute(batch, false));
        assertEquals(0, count("test_business")); assertEquals(0, count("entity_import_source_link")); assertEquals(0, count("process_archive_instance"));
        doNothing().when(relations).validateImportedRelations(anyString(), anyString()); execute(batch, true);
        assertEquals(1, count("process_archive_instance"));
        String archiveId = jdbc.queryForObject("SELECT id FROM process_archive_instance", String.class);
        assertEquals(2, ((List<?>)archives.detail(archiveId).get("events")).size());
    }
    @Test void identicalSecondBatchReusesRowsAndArchiveButChangedContentConflicts() throws Exception {
        execute(batch(true, "相同数据"), false); execute(batch(true, "相同数据"), false);
        assertEquals(1, count("test_business")); assertEquals(1, count("process_archive_instance"));
        var changed = batch(true, "变更数据"); assertThrows(RuntimeException.class, () -> execute(changed, false));
        assertEquals(1, count("test_business"));
        String id = jdbc.queryForObject("SELECT id FROM test_business", String.class);
        assertThrows(RuntimeException.class, () -> guard.requireWritable("expense", id));
    }
    @Test void schemaDriftAndWrongValidationHashCannotWrite() throws Exception {
        var batch = batch(false, "记录");
        assertThrows(RuntimeException.class, () -> service.executeNext(batch.get("id").toString(), "oldHash", "group1", false));
        snapshot.setVersion(2); assertThrows(RuntimeException.class, () -> execute(batch, false)); assertEquals(0, count("test_business"));
    }
    @Test void batchOwnershipAndArchiveRecordPermissionAreCheckedAgainOnRead() throws Exception {
        var batch = batch(true, "记录"); execute(batch, false);
        UserContext.setCurrentUser("u2", "another"); assertThrows(ForbiddenException.class, () -> service.get(batch.get("id").toString()));
        when(records.findAccessibleById(eq("expense"), anyString(), isNull())).thenThrow(new ForbiddenException("权限收回"));
        String id = jdbc.queryForObject("SELECT id FROM process_archive_instance", String.class);
        assertThrows(ForbiddenException.class, () -> archives.detail(id));
    }
    @Test void concurrentSameGroupRequestsCommitOnlyOnce() throws Exception {
        var batch = batch(false, "并发记录");
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> call = () -> {
                try (var auth = mockStatic(PermissionUtil.class)) {
                    auth.when(() -> PermissionUtil.hasPermission(anyString())).thenReturn(true); UserContext.setCurrentUser("u1", "importer");
                    return execute(batch, false);
                } finally { UserContext.clear(); }
            };
            var results = pool.invokeAll(List.of(call, call)); for (var result : results) result.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, count("test_business")); assertEquals(1, count("entity_import_source_link"));
    }
    @Test void normalizeChecksTypesAndRejectsEngineFields() {
        var schema = new EntitySchema("expense", "报销", "v1", List.of(new Field("when", "日期", "DATE", false, null), new Field("amount", "金额", "INTEGER", true, null)), List.of());
        assertEquals("2020-01-02", entities.normalize(schema, Map.of("when", "2020-01-02T00:00", "amount", "1")).get("when"));
        assertThrows(IllegalArgumentException.class, () -> entities.normalize(schema, Map.of("amount", "1.1")));
        assertThrows(IllegalArgumentException.class, () -> entities.normalize(schema, Map.of("amount", "1", "processInstanceId", "forged")));
    }

    @Test void persistedDecimalRetainsPrecisionAndBatchPaginationUsesMapper() throws Exception {
        var amount = new EntityField(); amount.setFieldCode("amount"); amount.setFieldName("金额"); amount.setFieldType(EntityField.FieldType.DECIMAL);
        snapshot.setFields(List.of(snapshot.getFields().get(0), amount));
        var batch = batch(true, "金额记录"); String id = batch.get("id").toString();
        String exact = "9007199254740993.123456789";
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook.template(plan, "entities")))) {
            var row = book.getSheetAt(0).createRow(1);
            String[] cells = {"000", "group1", "金额记录", exact}; for (int i = 0; i < cells.length; i++) row.createCell(i).setCellValue(cells[i]);
            var file = file(book);
            tx.execute(status -> { try { return service.upload(id, 4, "entities", file); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
        batch = tx.execute(status -> service.validate(id, 5)); execute(batch, false);
        String document = jdbc.queryForObject("SELECT document_json FROM process_archive_instance", String.class);
        assertTrue(document.contains(exact));
        assertEquals(1, service.list(1, 1).size()); assertTrue(service.list(2, 1).isEmpty());
    }

    @Test void xmlExternalEntitiesAreNeverRead() {
        String unsafe = "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///not-allowed'>]><definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL'>&secret;</definitions>";
        assertThrows(IllegalArgumentException.class, () -> ProcessArchiveService.nodeIndex(unsafe));
    }
}
