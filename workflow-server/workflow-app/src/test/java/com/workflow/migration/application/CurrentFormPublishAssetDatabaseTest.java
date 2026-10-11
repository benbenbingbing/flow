package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.port.MigrationAssetPort;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiConfigSnapshotSupport;
import com.workflow.entity.ui.application.UiEventBindingSnapshotService;
import com.workflow.entity.ui.application.UiViewCompositionService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseAuditMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 执行完整 FORM 发布与真实迁移资产写入，防止只测发布预检而漏掉事务末尾的失败。
 * 数据库表直接采用历史迁移的列宽约束；不连接真实业务库。
 */
class CurrentFormPublishAssetDatabaseTest {
    @ParameterizedTest
    @EnumSource(value = EntityDefinition.StorageMode.class, names = {"DYNAMIC", "SYSTEM"})
    void fullPublicationWritesBoundedAssetsAndKeepsOnlyCurrentForm(EntityDefinition.StorageMode storageMode) throws Exception {
        try (Fixture db = new Fixture(storageMode)) {
            UiConfigRelease first = db.publish();
            assertEquals(64, first.getId().length());
            assertEquals(1, db.count("config_migration_asset"));
            assertEquals(first.getId(), db.jdbc.queryForObject("SELECT active_release_id FROM entity_form", String.class));
            assertEquals(first.getId(), db.jdbc.queryForObject("SELECT release_id FROM ui_config_release_audit", String.class));
            assertEquals("form", db.jdbc.queryForObject("SELECT form_id FROM entity_form_current_default", String.class));

            // A -> B -> A 必须保留三份实体导出快照，来源键不能只截取同一个表单摘要。
            db.draft.setFormName("配置 B");
            UiConfigRelease second = db.publish();
            assertNotEquals(first.getId(), second.getId());
            db.draft.setFormName("配置 A");
            assertEquals(first.getId(), db.publish().getId());
            assertEquals(1, db.count("entity_form_published"));
            assertEquals(0, db.count("ui_config_release"));
            assertEquals(3, db.count("config_migration_asset"));
            assertEquals(List.of(1, 2, 3), db.jdbc.queryForList(
                    "SELECT source_version FROM config_migration_asset ORDER BY source_version", Integer.class));
            assertEquals(3, db.jdbc.queryForObject("SELECT COUNT(DISTINCT source_history_id) FROM config_migration_asset", Integer.class));
            assertTrue(db.jdbc.queryForList("SELECT source_history_id FROM config_migration_asset", String.class)
                    .stream().allMatch(key -> key.matches("[0-9a-f]{64}")));
            String exported = db.jdbc.queryForObject("SELECT snapshot_json FROM config_migration_asset WHERE source_version=2", String.class);
            assertTrue(exported.contains("配置 B"));
            verify(db.bean(ConfigMigrationAssetDependencyService.class), times(3)).replace(any(), any());

            // 相同内容再次发布必须复用当前配置，不能追加审计或迁移资产。
            db.publish();
            assertEquals(3, db.count("config_migration_asset"));
            assertEquals(3, db.count("ui_config_release_audit"));
        }
    }

    @Test
    void listHistoryRemainsReadableWithAllHotfixTablesAbsent() throws Exception {
        try (Fixture db = new Fixture()) {
            db.publish();
            db.jdbc.update("INSERT INTO ui_config_release(id,config_type,config_id,version,snapshot_document,content_hash,status,published_by) VALUES('list-v6','LIST','list',6,'{}','hash','ACTIVE','publisher')");
            db.jdbc.execute("CREATE TABLE sys_user(id VARCHAR PRIMARY KEY,nickname VARCHAR,username VARCHAR,deleted INT)");
            db.jdbc.update("INSERT INTO sys_user VALUES('publisher','发布人','publisher-login',0)");
            var rows = db.bean(UiConfigReleaseMapper.class).findReleaseSummaries("LIST", "list", 0, 20);
            assertEquals(1, rows.size());
            assertEquals("list-v6", rows.get(0).getId());
            assertEquals("发布人", rows.get(0).getPublishedByName());
            assertNull(rows.get(0).getRolloutStatus());
        }
    }

    @Test
    void migrationAssetFailureRollsBackCurrentDefaultOwnerAndAudit() throws Exception {
        try (Fixture db = new Fixture()) {
            String firstHash = db.publish().getId();
            db.draft.setFormName("失败候选");
            db.draft.setIsDefault(false);
            doThrow(new IllegalStateException("依赖资产登记失败"))
                    .when(db.bean(ConfigMigrationAssetDependencyService.class)).replace(any(), any());
            assertThrows(IllegalStateException.class, db::publish);
            assertEquals(firstHash, db.bean(UiConfigReleaseMapper.class).findCurrentForm("form").getId());
            assertEquals(firstHash, db.jdbc.queryForObject("SELECT active_release_id FROM entity_form", String.class));
            assertEquals("form", db.jdbc.queryForObject("SELECT form_id FROM entity_form_current_default", String.class));
            assertEquals(1, db.count("config_migration_asset"));
            assertEquals(1, db.count("ui_config_release_audit"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Map<Class<?>, Object> beans = new HashMap<>();
        final JdbcTemplate jdbc;
        final TransactionTemplate transactions;
        final EntityForm draft = new EntityForm();
        final UiConfigReleaseService service;

        Fixture() throws Exception { this(EntityDefinition.StorageMode.DYNAMIC); }

        Fixture(EntityDefinition.StorageMode storageMode) throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:form_asset_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
            // 从不可变基线提取原始表定义，保留真实列宽、非空与唯一约束，仅去除 H2 不支持的存储选项。
            String schema = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V001__business_schema.sql"));
            for (String table : List.of("entity_form", "ui_config_release", "ui_config_release_audit", "config_migration_asset")) {
                String start = "CREATE TABLE `" + table + "` (";
                int offset = schema.indexOf(start);
                String ddl = schema.substring(offset, schema.indexOf(") ENGINE=", offset) + 1)
                        .replaceAll("(?i) COLLATE [a-z0-9_]+", "")
                        .replaceAll("(?i) CHARACTER SET [a-z0-9_]+", "")
                        .replace("_utf8mb4", "").replace(" STORED", "");
                jdbc.execute(ddl);
            }
            // 故意不建立任何热修复表：完整发布事务与列表历史 SQL 必须独立于退役结构。
            jdbc.execute("CREATE TABLE entity_form_published(form_id VARCHAR(64) PRIMARY KEY,entity_id VARCHAR(64),snapshot_document CLOB,content_hash CHAR(64) UNIQUE,published_by VARCHAR(64),published_at TIMESTAMP)");
            jdbc.execute("CREATE TABLE entity_form_current_default(entity_id VARCHAR(64) PRIMARY KEY,form_id VARCHAR(64))");
            jdbc.execute("CREATE TABLE entity_form_publication_lock(lock_id INT PRIMARY KEY)");
            jdbc.update("INSERT INTO entity_form_publication_lock VALUES(1)");
            jdbc.update("INSERT INTO entity_form(id,entity_id,form_name,form_key,revision,deleted) VALUES('form','entity','配置 A','test_form',1,0)");

            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            List<Class<?>> mappers = List.of(EntityFormPublishedMapper.class, EntityFormMapper.class,
                    UiConfigReleaseMapper.class, UiConfigReleaseAuditMapper.class, ConfigMigrationAssetMapper.class);
            mappers.forEach(configuration::addMapper);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            var session = new SqlSessionTemplate(factory.getObject());
            mappers.forEach(type -> beans.put(type, session.getMapper(type)));

            var json = new ObjectMapper().findAndRegisterModules();
            beans.put(ObjectMapper.class, json);
            beans.put(JsonDocumentCodec.class, new JsonDocumentCodec(json));
            create(UiConfigSnapshotSupport.class);
            create(UiEventBindingSnapshotService.class);
            create(ConfigMigrationReferenceService.class);
            create(ConfigMigrationSubFormReferences.class);
            ConfigMigrationAssetService assets = create(ConfigMigrationAssetService.class);
            beans.put(MigrationAssetPort.class, assets);

            EntityDefinition entity = new EntityDefinition();
            entity.setId("entity"); entity.setEntityCode(storageMode == EntityDefinition.StorageMode.SYSTEM ? "sys_user" : "TEST_ENTITY");
            entity.setEntityName("测试实体"); entity.setStorageMode(storageMode);
            when(bean(SystemEntityFieldPolicy.class).isSupportedEntity(entity.getEntityCode())).thenReturn(true);
            when(bean(EntityDefinitionMapper.class).selectById("entity")).thenReturn(entity);
            draft.setId("form"); draft.setEntityId("entity"); draft.setFormKey("test_form");
            draft.setFormName("配置 A"); draft.setLayoutType("grid"); draft.setRevision(1);
            draft.setIsDefault(true); draft.setNodes(List.of()); draft.setFields(List.of());
            when(bean(EntityFormService.class).getById("form")).thenReturn(draft);
            // 迁移快照使用真实 owner Mapper 查询元数据，再通过真实发布 Mapper 取得刚写入的当前内容。
            service = create(UiConfigReleaseService.class);
            ReflectionTestUtils.setField(service, "formPublishedMapper", bean(EntityFormPublishedMapper.class));
            ReflectionTestUtils.setField(service, "viewCompositionService", bean(UiViewCompositionService.class));
        }

        UiConfigRelease publish() {
            return transactions.execute(status -> service.publish("FORM", "form", (String) null));
        }

        int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

        @SuppressWarnings("unchecked")
        <T> T bean(Class<T> type) { return (T) beans.computeIfAbsent(type, key -> mock(type)); }

        /** 真实装配发布/快照服务，其余依赖默认为空配置；写入相关 Mapper 均已显式装配为真实 SQL。 */
        @SuppressWarnings("unchecked")
        <T> T create(Class<T> type) throws Exception {
            Constructor<?> constructor = Arrays.stream(type.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
            Object[] args = Arrays.stream(constructor.getParameterTypes()).map(this::bean).toArray();
            T result = (T) constructor.newInstance(args);
            beans.put(type, result);
            return result;
        }

        @Override public void close() { jdbc.execute("SHUTDOWN"); }
    }
}
