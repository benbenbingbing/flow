package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.extension.application.*;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.annotation.*;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.transaction.TransactionDefinition;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用实际数据库和新增迁移验证写入/扩展/映射的事务语义，外部依赖仅模拟已有领域服务。 */
class IdentityWriteIntegrationTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    IdentitySourceStore store;
    IdentityExtensionService extensions;
    IdentityExtensionRegistry registry;
    IdentityWriteService service;
    SysUserService users;
    SystemAuditPort audit;
    IdentityAccessService access;
    IdentityWriteProperties.Source source;

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64) PRIMARY KEY, username VARCHAR(50) UNIQUE, password VARCHAR(100) NOT NULL, nickname VARCHAR(50), email VARCHAR(100), phone VARCHAR(20), avatar VARCHAR(255), status CHAR(1), org_id VARCHAR(64), dept_id VARCHAR(64), password_reset_required INT DEFAULT 0, token_version BIGINT DEFAULT 0, deleted INT DEFAULT 0, create_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_organization (id VARCHAR(64) PRIMARY KEY, org_code VARCHAR(100), org_name VARCHAR(100), parent_id VARCHAR(64), type VARCHAR(20), status CHAR(1), deleted INT DEFAULT 0, phone VARCHAR(50), email VARCHAR(100), address VARCHAR(200), description VARCHAR(500))");
        jdbc.execute("CREATE TABLE entity_definition (id BIGINT, table_name VARCHAR(100), deleted INT)");
        jdbc.execute("CREATE TABLE entity_field (entity_id BIGINT, field_code VARCHAR(100), is_required INT)");
        String migration = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V126__identity_source_write_and_external_accounts.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
        new ResourceDatabasePopulator(new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
        for (String name : List.of("first", "second", "local")) {
            jdbc.execute("CREATE TABLE biz_test_" + name + " (user_id VARCHAR(64) PRIMARY KEY, value_text VARCHAR(64), row_version BIGINT NOT NULL DEFAULT 0)");
        }
        jdbc.execute("CREATE TABLE biz_test_numbers (user_id VARCHAR(64) PRIMARY KEY, small INTEGER, large BIGINT, amount DECIMAL(30, 8), row_version BIGINT NOT NULL DEFAULT 0)");
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(IdentityPersistenceMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        var persistence = new SqlSessionTemplate(factory.getObject()).getMapper(IdentityPersistenceMapper.class);
        store = new IdentitySourceStore(persistence);
        var properties = new IdentityWriteProperties();
        source = new IdentityWriteProperties.Source();
        source.setExtensions(Set.of("test.first", "test.second"));
        var writer = new IdentityAccessService.Writer("actor", source);
        access = mock(IdentityAccessService.class);
        when(access.requireSource(any())).thenReturn(writer);
        when(access.requireObject(any(), anyString())).thenReturn("actor");
        when(access.hasPermission(any(), any())).thenReturn(true);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        registry = new IdentityExtensionRegistry(properties, new JdbcSchemaMetadata(jdbc, DatabaseDialects.forVendor(DatabaseVendor.MYSQL)));
        registry.register(First.class); registry.register(Second.class); registry.register(Local.class);
        registry.register(Numbers.class);
        extensions = new IdentityExtensionService(registry, access, persistence, mapper);
        users = mock(SysUserService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(anyString())).thenAnswer(invocation -> {
            var found = jdbc.query("SELECT * FROM sys_user WHERE id = ? AND deleted = 0", (rs, n) -> {
                SysUser user = new SysUser(); user.setId(rs.getString("id")); user.setUsername(rs.getString("username")); user.setStatus(rs.getString("status")); return user;
            }, (String) invocation.getArgument(0));
            return found.isEmpty() ? null : found.get(0);
        });
        var organizations = mock(SysOrganizationService.class);
        when(organizations.saveProvisionedOrganization(any())).thenAnswer(invocation -> {
            SysOrganization org = invocation.getArgument(0);
            jdbc.update("INSERT INTO sys_organization (id, org_code, org_name, parent_id, type, status) VALUES (?, ?, ?, ?, ?, ?)", org.getId(), org.getOrgCode(), org.getOrgName(), org.getParentId(), org.getType(), org.getStatus());
            return org;
        });
        audit = mock(SystemAuditPort.class);
        service = new IdentityWriteService(store, access, extensions, persistence, mapper, users, userMapper, organizations, audit);
    }

    @Test
    void simultaneousFirstWritesConvergeToOneLocalIdentity() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Callable<IdentitySaveResult> call = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                return save(user("concurrent", 1L, "a", null, List.of(first("T1"))));
            };
            var a = executor.submit(call); var b = executor.submit(call);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertEquals(first.localId(), second.localId());
            assertEquals(Set.of(IdentitySaveOutcome.CREATED, IdentitySaveOutcome.UNCHANGED), Set.of(first.outcome(), second.outcome()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void rejectsOuterRepeatableReadTransactionInsteadOfReadingAnOldDirectorySnapshot() {
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertEquals("INVALID_TRANSACTION_ISOLATION", assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 1L, "a", null, List.of()))).code());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    @Test
    void provisionsStableIdentityAndPreservesUnsubmittedExtension() {
        var first = save(user("E1", 1L, "old", "a@example.com", List.of(first("T1"), second("Java"))));
        var updated = save(user("E1", 2L, "new", null, List.of(first("T2"))));
        assertEquals(first.localId(), updated.localId());
        assertEquals(first.canonicalUsername(), updated.canonicalUsername());
        assertEquals("EXTERNAL_ONLY", jdbc.queryForObject("SELECT authentication_mode FROM sys_user", String.class));
        assertNull(jdbc.queryForObject("SELECT password FROM sys_user", String.class));
        assertNull(jdbc.queryForObject("SELECT email FROM sys_user", String.class));
        assertEquals("Java", jdbc.queryForObject("SELECT value_text FROM biz_test_second", String.class));
        assertEquals("new", jdbc.queryForObject("SELECT external_account FROM sys_identity_source_binding", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    @Test
    void rejectsSameVersionConflictAndIgnoresOldSnapshots() {
        var model = user("E1", 4L, "name", null, List.of(first("T1")));
        save(model);
        assertEquals(IdentitySaveOutcome.UNCHANGED, save(model).outcome());
        assertEquals(IdentitySaveOutcome.STALE_IGNORED, save(user("E1", 3L, "old", null, List.of())).outcome());
        var error = assertThrows(IdentityWriteException.class, () -> save(user("E1", 4L, "different", null, List.of())));
        assertEquals("SOURCE_VERSION_CONFLICT", error.code());
        assertEquals("name", jdbc.queryForObject("SELECT external_account FROM sys_identity_source_binding", String.class));
    }

    @Test
    void auditFailureRollsBackBaseExtensionsAndBinding() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
        assertThrows(IllegalStateException.class, () -> save(user("E1", 1L, "a", null, List.of(first("T1")))));
        for (String table : List.of("sys_user", "sys_identity_source_binding", "biz_test_first")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
    }

    @Test
    void laterBatchFailureRollsBackEarlierSuccessfulRows() {
        var bad = UserSaveModel.builder().key(new ExternalIdentityKey("erp", "E2")).sourceVersion(1L)
                .base(UserBaseData.builder().nickname("bad").organizationExternalId("missing").enabled(true).build()).extensions(List.of()).build();
        assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveUsers(List.of(user("E1", 1L, "a", null, List.of()), bad))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    @Test
    void sourceEnableCannotOverrideLocalDisableAndRetiredObjectsAreNotRecreated() {
        var created = save(user("E1", 1L, "a", null, List.of()));
        tx.execute(s -> { store.lockDirectory(); store.localStatus(IdentityObjectType.USER, created.localId(), "1"); return null; });
        save(user("E1", 2L, "a", null, List.of()));
        assertEquals("1", jdbc.queryForObject("SELECT status FROM sys_user", String.class));
        tx.execute(s -> { store.retire(IdentityObjectType.USER, created.localId()); return null; });
        assertEquals("SOURCE_OBJECT_RETIRED", assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 3L, "a", null, List.of()))).code());
    }

    @Test
    void exactNumbersSurviveBrowserRoundTripAndFractionalIntegersAreRejected() {
        var created = save(user("E1", 1L, "a", null, List.of()));
        tx.execute(s -> {
            extensions.saveLocal(IdentityObjectType.USER, created.localId(), Map.of("test.numbers",
                    new IdentityExtensionEdit(0L, Map.of("small", 2, "large", "9007199254740993", "amount", "1234567890.12345678"))));
            return null;
        });
        var detail = extensions.readAll(IdentityObjectType.USER, created.localId()).get("test.numbers");
        assertEquals("9007199254740993", detail.data().get("large"));
        assertEquals("1234567890.12345678", detail.data().get("amount"));
        assertEquals(9007199254740993L, extensions.findUserExtension(created.localId(), Numbers.class).orElseThrow().getLarge());
        assertThrows(IdentityWriteException.class, () -> tx.execute(s -> {
            extensions.saveLocal(IdentityObjectType.USER, created.localId(), Map.of("test.numbers",
                    new IdentityExtensionEdit(1L, Map.of("small", 1.5)))); return null;
        }));
        assertEquals(2, jdbc.queryForObject("SELECT small FROM biz_test_numbers", Integer.class));
    }

    @Test
    void localProfilesUseOptimisticVersionAndCannotWriteSourceGroups() {
        var created = save(user("E1", 1L, "a", null, List.of()));
        tx.execute(s -> { extensions.saveLocal(IdentityObjectType.USER, created.localId(), Map.of("test.local", new IdentityExtensionEdit(0L, Map.of("valueText", "note")))); return null; });
        assertEquals(1L, jdbc.queryForObject("SELECT row_version FROM biz_test_local", Long.class));
        assertEquals("EXTENSION_VERSION_CONFLICT", assertThrows(IdentityWriteException.class, () -> tx.execute(s -> {
            extensions.saveLocal(IdentityObjectType.USER, created.localId(), Map.of("test.local", new IdentityExtensionEdit(0L, Map.of("valueText", "overwrite")))); return null;
        })).code());
        assertEquals("EXTENSION_WRITE_NOT_ALLOWED", assertThrows(IdentityWriteException.class, () -> tx.execute(s -> {
            extensions.saveLocal(IdentityObjectType.USER, created.localId(), Map.of("test.first", new IdentityExtensionEdit(0L, Map.of("valueText", "bad")))); return null;
        })).code());
    }

    @Test
    void organizationBatchResolvesOutOfOrderParentsAndReturnsInputOrder() {
        var child = organization("child", "parent");
        var parent = organization("parent", null);
        var results = tx.execute(s -> service.saveOrganizations(List.of(child, parent)));
        assertEquals("child", results.get(0).externalId());
        assertEquals(results.get(1).localId(), jdbc.queryForObject("SELECT parent_id FROM sys_organization WHERE id = ?", String.class, results.get(0).localId()));
        assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganizations(List.of(organization("A", "B"), organization("B", "A")))));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Integer.class));
    }

    @Test
    void keysRemainCaseSensitiveAndSerialModeDoesNotPretendToHaveSourceVersions() {
        save(user("E", 1L, "a", null, List.of())); save(user("e", 1L, "b", null, List.of()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_binding", Integer.class));
        source.setVersionMode(IdentityWriteProperties.VersionMode.SERIAL);
        var model = user("serial", null, "c", null, List.of());
        assertEquals(IdentitySaveOutcome.CREATED, save(model).outcome());
        assertEquals(IdentitySaveOutcome.UNCHANGED, save(model).outcome());
        assertThrows(IdentityWriteException.class, () -> save(user("serial", 2L, "d", null, List.of())));
    }

    @Test
    void rejectsUnknownFieldsDuplicateGroupsAndUnauthorizedSourceExtension() {
        assertThrows(IdentityWriteException.class, () -> save(user("E1", 1L, "a", null, List.of(first("T1"), first("T2")))));
        source.setExtensions(Set.of());
        assertThrows(IdentityWriteException.class, () -> save(user("E1", 1L, "a", null, List.of(first("T1")))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    private IdentitySaveResult save(UserSaveModel model) { return tx.execute(s -> service.saveUser(model)); }
    private UserSaveModel user(String id, Long revision, String account, String email, List<UserExtension> groups) {
        return new UserSaveModel(new ExternalIdentityKey("erp", id), revision,
                new UserBaseData(account, "name", email, null, null, null, null, true), groups);
    }
    private OrganizationSaveModel organization(String id, String parent) {
        return new OrganizationSaveModel(new ExternalIdentityKey("erp", id), 1L,
                new OrganizationBaseData(id, id, parent == null ? OrganizationUnitType.ORGANIZATION : OrganizationUnitType.DEPARTMENT,
                        parent, null, null, null, null, null, 0, true), List.of());
    }
    private First first(String text) { First value = new First(); value.setValueText(text); return value; }
    private Second second(String text) { Second value = new Second(); value.setValueText(text); return value; }

    @Data
    @IdentityExtension(code="test.first", title="first", table="biz_test_first", readPermission="view", writePermission="write", maintenance=ExtensionMaintenance.SOURCE)
    public static class First extends UserExtension { @ExtensionField(label="value", maxLength=64) private String valueText; }
    @Data
    @IdentityExtension(code="test.second", title="second", table="biz_test_second", readPermission="view", writePermission="write", maintenance=ExtensionMaintenance.SOURCE)
    public static class Second extends UserExtension { @ExtensionField(label="value", maxLength=64) private String valueText; }
    @Data
    @IdentityExtension(code="test.local", title="local", table="biz_test_local", readPermission="view", writePermission="write")
    public static class Local extends UserExtension { @ExtensionField(label="value", maxLength=64) private String valueText; }
    @Data
    @IdentityExtension(code="test.numbers", title="numbers", table="biz_test_numbers", readPermission="view", writePermission="write")
    public static class Numbers extends UserExtension {
        @ExtensionField(label="small") private Integer small;
        @ExtensionField(label="large") private Long large;
        @ExtensionField(label="amount") private java.math.BigDecimal amount;
    }
}
