package com.workflow.entity.definition.application;

import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.core.database.port.SchemaMetadataPort;
import static org.mockito.AdditionalAnswers.delegatesTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableNaming;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.definition.api.response.EntitySchemaOperationDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用真实连接和 Spring 事务代理覆盖迁移的“新建未提交 -> 独立事务准备/完成结构发布”。
 * 元数据、物理表和操作台账均实际落库；只替换数据库行数估计，结构读取使用 JDBC 元数据。
 */
class EntitySchemaOperationTransactionIntegrationTest {
    private JdbcTemplate jdbc;
    private JdbcTemplate ddlJdbc;
    private EntityDefinitionMapper entityMapper;
    private DynamicTableService tables;
    private EntitySchemaOperationService service;
    private TransactionTemplate migrationTransaction;
    private TransactionTemplate independentTransaction;
    private EntityDefinition entity;
    private List<EntityField> fields;

    @BeforeEach
    void setUp() {
        String database = "entity_schema_" + UUID.randomUUID().toString().replace("-", "");
        // 为本测试隔离 catalog/schema，避免不同发布事务用例共享结构。
        String url = "jdbc:h2:mem:" + database
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS " + database + "\\;SET SCHEMA " + database;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        // DDL 使用另一个 DataSource 获取独立连接，避免 H2 的 DDL 隐式提交迁移事务。
        ddlJdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        createMetadataTables();

        entityMapper = mock(EntityDefinitionMapper.class);
        when(entityMapper.findByEntityCode(anyString())).thenAnswer(invocation -> jdbc.query(
                "SELECT * FROM entity_definition WHERE entity_code = ?",
                (rs, rowNum) -> {
                    EntityDefinition value = new EntityDefinition();
                    value.setId(rs.getString("id"));
                    value.setEntityCode(rs.getString("entity_code"));
                    value.setPhysicalTableName(rs.getString("physical_table_name"));
                    value.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
                    return value;
                }, invocation.getArgument(0, String.class)).stream().findFirst());
        var metadata = mock(SchemaMetadataPort.class, delegatesTo(new JdbcSchemaMetadata(jdbc, new MySqlSchemaDdlDialect())));
        // H2 的发布事务测试只替换行数统计，表列与索引读取仍通过真正 JDBC 元数据。
        doAnswer(invocation -> metadata.tableExists(invocation.getArgument(0))
                ? jdbc.queryForObject("SELECT COUNT(*) FROM " + invocation.getArgument(0), Long.class) : 0L)
                .when(metadata).estimateRows(anyString());
        EntityPhysicalTableResolver resolver = new EntityPhysicalTableResolver(
                entityMapper, new EntityPhysicalTableNaming(), metadata);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        tables = new DynamicTableService(jdbc, fieldMapper, resolver, ddlJdbc::execute, new MySqlSchemaDdlDialect(), metadata, com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));

        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        migrationTransaction = new TransactionTemplate(manager);
        independentTransaction = new TransactionTemplate(manager);
        independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(new EntitySchemaOperationService(jdbc, new ObjectMapper(), tables,
                com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"),
                new JdbcLockedRow(jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL))));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        service = (EntitySchemaOperationService) proxy.getProxy();

        entity = new EntityDefinition();
        entity.setId("new-entity");
        entity.setEntityCode("all_entity");
        entity.setEntityName("全流程验收实体");
        // 表名刻意与编码不同，确保使用登记值而非重新拼接 biz_ + entityCode。
        entity.setPhysicalTableName("biz_imported_acceptance");
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        EntityField field = new EntityField();
        field.setFieldCode("external_code");
        field.setFieldName("外部编码");
        field.setFieldType(EntityField.FieldType.STRING);
        field.setIsUnique(true);
        fields = List.of(field);
        when(fieldMapper.findByEntityId(entity.getId())).thenReturn(fields);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void preparesAndCompletesSchemaWhileNewEntityIsUncommitted() {
        migrationTransaction.executeWithoutResult(status -> {
            insertEntity();
            assertTrue(entityMapper.findByEntityCode("all_entity").isPresent());
            independentTransaction.executeWithoutResult(inner -> {
                assertTrue(entityMapper.findByEntityCode("all_entity").isEmpty());
                assertEquals("实体不存在: all_entity", assertThrows(IllegalArgumentException.class,
                        () -> tables.getTableName("all_entity")).getMessage());
            });
            clearInvocations(entityMapper);

            List<String> plan = tables.planEntityTableStructure(entity);
            EntitySchemaOperationDTO operation = service.prepare(entity, fields, plan, "admin");
            assertEquals("DDL_PENDING", operation.getStatus());
            assertEquals(0L, operation.getEstimatedRows());
            assertEquals(List.of("物理表不存在: biz_imported_acceptance"), operation.getDrift());
            assertEquals(operation.getId(), service.prepare(entity, fields, plan, "admin").getId());
            service.markRunning(operation.getId());
            tables.syncEntityTableStructure(entity);
            service.complete(operation.getId(), entity, fields);

            EntitySchemaOperationDTO completed = service.latest(entity.getId());
            assertEquals("SCHEMA_CONSISTENT", completed.getStatus());
            assertEquals(completed.getTargetFingerprint(), completed.getActualFingerprint());
            verifyNoInteractions(entityMapper);
        });
        assertTrue(entityMapper.findByEntityCode("all_entity").isPresent());
        assertEquals("SCHEMA_CONSISTENT", service.latest(entity.getId()).getStatus());
    }

    @Test
    void keepsFailureEvidenceWhenNewEntityTransactionRollsBack() {
        IllegalStateException failure = new IllegalStateException("模拟结构执行失败");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> migrationTransaction.executeWithoutResult(status -> {
                    insertEntity();
                    EntitySchemaOperationDTO operation = service.prepare(entity, fields,
                            tables.planEntityTableStructure(entity), "admin");
                    service.markRunning(operation.getId());
                    service.markFailed(operation.getId(), failure);
                    throw failure;
                })));

        assertTrue(entityMapper.findByEntityCode("all_entity").isEmpty());
        EntitySchemaOperationDTO failed = service.latest(entity.getId());
        assertEquals("DDL_FAILED", failed.getStatus());
        assertEquals(failure.getMessage(), failed.getErrorMessage());
        assertTrue(failed.getRetryable());
    }

    @Test
    void stillRejectsIncompletePhysicalSchemaAfterDdl() {
        BusinessConflictException failure = assertThrows(BusinessConflictException.class,
                () -> migrationTransaction.executeWithoutResult(status -> {
                    insertEntity();
                    EntitySchemaOperationDTO operation = service.prepare(entity, fields,
                            tables.planEntityTableStructure(entity), "admin");
                    service.markRunning(operation.getId());
                    ddlJdbc.execute("CREATE TABLE biz_imported_acceptance (id VARCHAR(64))");
                    service.complete(operation.getId(), entity, fields);
                }));

        assertEquals("ENTITY_SCHEMA_DRIFT_DETECTED", failure.getErrorCode());
        assertTrue(failure.getMessage().contains("external_code"));
        assertTrue(entityMapper.findByEntityCode("all_entity").isEmpty());
    }

    @Test
    void stillChecksExistingTableRowsAndUniqueConflicts() {
        insertEntity();
        tables.syncEntityTableStructure(entity);
        jdbc.update("INSERT INTO biz_imported_acceptance (id, external_code) VALUES ('1', 'duplicate'), ('2', 'duplicate')");

        EntitySchemaOperationDTO preview = service.preview(entity, fields, List.of());
        assertEquals(2L, preview.getEstimatedRows());
        assertTrue(preview.getDrift().isEmpty());
        assertFalse(preview.getUniqueConflicts().isEmpty());
        assertEquals("HIGH", preview.getRiskLevel());
        assertEquals(tables.actualSchemaFingerprint("all_entity"), preview.getActualFingerprint());
        assertEquals("ENTITY_UNIQUE_DATA_CONFLICT", assertThrows(BusinessConflictException.class,
                () -> service.prepare(entity, fields, List.of(), "admin")).getErrorCode());
    }

    private void insertEntity() {
        jdbc.update("INSERT INTO entity_definition (id, entity_code, physical_table_name) VALUES (?, ?, ?)",
                entity.getId(), entity.getEntityCode(), entity.getPhysicalTableName());
    }

    /** 仅建立本测试所需元数据与台账表，业务表由生产 DynamicTableService 的 DDL 创建。 */
    private void createMetadataTables() {
        jdbc.execute("CREATE TABLE entity_definition (id VARCHAR(64) PRIMARY KEY, entity_code VARCHAR(100), physical_table_name VARCHAR(64))");
        jdbc.execute("""
                CREATE TABLE entity_schema_operation (
                    id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), entity_code VARCHAR(100), status VARCHAR(32),
                    plan_hash CHAR(64), idempotency_key VARCHAR(160) UNIQUE, plan_json TEXT,
                    target_fingerprint CHAR(64), actual_fingerprint CHAR(64), drift_json TEXT, unique_conflict_json TEXT,
                    risk_level VARCHAR(16), risk_reason VARCHAR(1000), estimated_rows BIGINT,
                    lock_risk VARCHAR(16), release_window VARCHAR(255), attempt_count INT DEFAULT 0,
                    error_message TEXT, started_at TIMESTAMP, finished_at TIMESTAMP, created_by VARCHAR(64),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (entity_id, plan_hash))
                """);
        jdbc.execute("""
                CREATE TABLE entity_schema_operation_event (
                    id VARCHAR(64) PRIMARY KEY, operation_id VARCHAR(64), from_status VARCHAR(32), to_status VARCHAR(32),
                    message VARCHAR(1000), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (operation_id) REFERENCES entity_schema_operation(id))
                """);
    }
}
