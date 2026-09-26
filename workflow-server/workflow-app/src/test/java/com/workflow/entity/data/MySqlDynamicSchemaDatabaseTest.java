package com.workflow.entity.data;

import com.workflow.entity.data.application.port.SchemaDdlExecutor;

import com.workflow.entity.data.application.*;
import com.workflow.entity.data.infrastructure.schema.JdbcSchemaDdlExecutor;
import com.workflow.core.database.port.DatabaseConnections;
import com.workflow.core.database.jdbc.InitializedDriverDataSource;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import javax.sql.DataSource;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import org.junit.jupiter.api.*;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.dbmigrator.schema.SchemaDdlReplayVerifier;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 真正 MySQL 的运行时结构回归。只使用随机命名的测试表，finally 清理，不读取或修改业务数据。
 * 通过 FLOW_MYSQL_TEST_URL/USER/PASSWORD 注入测试连接，缺少环境时明确跳过。
 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlDynamicSchemaDatabaseTest {
    private JdbcTemplate jdbc;
    private final MySqlSchemaDdlDialect dialect = new MySqlSchemaDdlDialect();
    private String table;
    private DynamicTableService service;
    private EntityDefinition entity;
    private EntityFieldMapper fieldMapper;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource(System.getenv("FLOW_MYSQL_TEST_URL"),
                System.getenv("FLOW_MYSQL_TEST_USER"), System.getenv("FLOW_MYSQL_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8."), "此测试要求真正 MySQL 8");
        table = "biz_dialect_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        entity = new EntityDefinition();
        entity.setId(table);
        entity.setEntityCode(table);
        entity.setEntityName("方言测试 O'Reilly\\中文");
        var resolver = mock(EntityPhysicalTableResolver.class);
        when(resolver.resolve(entity)).thenReturn(table);
        when(resolver.resolve(table)).thenReturn(table);
        fieldMapper = mock(EntityFieldMapper.class);
        var independent = new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"),
                System.getenv("FLOW_MYSQL_TEST_USER"), System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null,
                DatabaseJdbcProfiles.connectionInitSql(dialect.vendor()));
        var connections = new DatabaseConnections() {
            public DataSource application() { return independent; }
            public DataSource schema() { return independent; }
        };
        SchemaDdlExecutor executor = new JdbcSchemaDdlExecutor(connections, dialect);
        service = new DynamicTableService(jdbc, fieldMapper, resolver, executor, dialect, new JdbcSchemaMetadata(jdbc, dialect), com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"));
    }

    @AfterEach
    void cleanup() {
        if (jdbc != null && table != null) {
            for (String name : List.of(table + "_team", table + "_multi", table)) jdbc.execute("DROP TABLE IF EXISTS `" + name + "`");
        }
    }

    @Test
    void publishesAllStorageTypesDefaultsIndexesAndAuditTimestamp() {
        List<EntityField> fields = new ArrayList<>();
        for (var type : List.of(EntityField.FieldType.STRING, EntityField.FieldType.TEXT, EntityField.FieldType.INTEGER,
                EntityField.FieldType.LONG, EntityField.FieldType.DECIMAL, EntityField.FieldType.DATE,
                EntityField.FieldType.DATETIME, EntityField.FieldType.BOOLEAN, EntityField.FieldType.MULTI_REFERENCE)) {
            fields.add(field("f_" + type.name().toLowerCase(Locale.ROOT), type));
        }
        fields.get(0).setDefaultValue("O'Reilly\\中文'; --");
        fields.get(1).setDefaultValue("文本默认值");
        fields.get(4).setDefaultValue("12.30");
        fields.get(7).setDefaultValue("true");
        when(fieldMapper.findByEntityId(table)).thenReturn(fields);
        String preview = service.buildCreateTableSqlPreview(table, fields, entity.getEntityName());
        assertEquals(preview, service.createEntityTable(entity));
        var verifier = new SchemaDdlReplayVerifier(jdbc, dialect);
        for (var column : EntityTableDefinitionFactory.mainTable(table, fields, entity.getEntityName()).columns()) {
            assertTrue(verifier.isApplied(dialect.addColumn(table, column).get(0)), "加列重放不匹配: " + column.name());
        }
        assertTrue(verifier.isApplied(preview), "建表重放必须识别全部业务类型、默认值及审计列");
        assertTrue(service.inspectSchemaDrift(entity, fields).isEmpty());
        assertTrue(service.planEntityTableStructure(entity).isEmpty(), "原样发布不应再次改表");
        var metadata = new JdbcSchemaMetadata(jdbc, dialect);
        assertTrue(metadata.tables().stream().anyMatch(t -> t.name().equals(table) && entity.getEntityName().equals(t.comment())));
        var idColumn = metadata.columns(table).stream().filter(c -> c.name().equals("id")).findFirst().orElseThrow();
        assertTrue(idColumn.primaryKey());
        assertTrue(idColumn.singleColumnUnique());
        assertFalse(idColumn.nullable());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", Integer.class, table));
        assertEquals(entity.getEntityName(), jdbc.queryForObject("SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", String.class, table));
        jdbc.update("INSERT INTO `" + table + "` (id) VALUES (?)", "record1");
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM `" + table + "` WHERE id=?", "record1");
        assertEquals("NOT_STARTED", row.get("process_status"));
        assertEquals(fields.get(0).getDefaultValue(), row.get("f_string"));
        assertEquals("文本默认值", row.get("f_text"));
        assertEquals(new BigDecimal("12.30"), row.get("f_decimal"));
        assertEquals(1, jdbc.queryForObject("SELECT f_boolean + 0 FROM `" + table + "`", Integer.class));
        jdbc.update("UPDATE `" + table + "` SET update_time='2001-01-01 00:00:00' WHERE id='record1'");
        jdbc.update("UPDATE `" + table + "` SET name='changed' WHERE id='record1'");
        assertTrue(jdbc.queryForObject("SELECT YEAR(update_time) > 2001 FROM `" + table + "`", Boolean.class));
    }

    @Test
    void addsModifiesAndDropsColumnsAndPreservesMultiValueUniqueness() {
        EntityField amount = field("amount", EntityField.FieldType.DECIMAL);
        when(fieldMapper.findByEntityId(table)).thenReturn(List.of(amount));
        service.createEntityTable(entity);
        var description = field("description", EntityField.FieldType.STRING);
        description.setDefaultValue("初始值");
        service.addColumn(table, description);
        description.setFieldLength(400);
        description.setDefaultValue("新值");
        service.modifyColumn(table, description);
        jdbc.update("INSERT INTO `" + table + "` (id) VALUES ('r1')");
        assertEquals("新值", jdbc.queryForObject("SELECT description FROM `" + table + "`", String.class));
        amount.setFieldPrecision(3);
        assertEquals(1, service.planEntityTableStructure(entity).size(), "小数位变化必须形成修改计划");
        service.syncEntityTableStructure(entity);
        assertTrue(service.planEntityTableStructure(entity).isEmpty());
        service.dropColumn(table, "description");
        assertFalse(service.getTableColumns(table).stream().anyMatch(c -> c.getName().equals("description")));
        String insert = "INSERT INTO `" + table + "_multi` (id,record_id,field_code,target_entity_id,target_record_id) VALUES (?, 'r1', 'members', 'users', 'u1')";
        jdbc.update(insert, "m1");
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(insert, "m2"));
        service.ensureEntityMultiValueTable(table);
        var metadata = new JdbcSchemaMetadata(jdbc, dialect);
        assertTrue(metadata.columns(table + "_multi").stream().filter(c -> c.name().equals("record_id"))
                .noneMatch(c -> c.singleColumnUnique()), "复合唯一索引不能被识别为单列唯一");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "_multi`", Integer.class));
        dialect.createTable(EntityTableDefinitionFactory.teamTable(table + "_team")).forEach(jdbc::execute);
        var replay = new SchemaDdlReplayVerifier(jdbc, dialect);
        assertTrue(replay.isApplied(dialect.createTable(EntityTableDefinitionFactory.teamTable(table + "_team")).get(0)), () -> metadata.columns(table + "_team").toString());
        assertTrue(replay.isApplied(dialect.createTable(EntityTableDefinitionFactory.multiTable(table + "_multi")).get(0)));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", Integer.class, table + "_team"));
        service.dropEntityTable(table);
        assertFalse(service.tableExists(table));
    }

    private EntityField field(String code, EntityField.FieldType type) {
        var field = new EntityField();
        field.setFieldCode(code);
        field.setFieldName("字段 " + code);
        field.setFieldType(type);
        field.setIsPublished(true);
        return field;
    }
}
