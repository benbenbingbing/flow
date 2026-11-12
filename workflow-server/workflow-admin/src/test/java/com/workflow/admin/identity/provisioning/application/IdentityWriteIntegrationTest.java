package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.extension.application.*;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentitySourceSnapshotMapper;
import com.workflow.admin.security.context.UserContext;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.organization.application.SysOrganizationServiceImpl;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.annotation.*;
import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.contracts.identity.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
    SysOrganizationServiceImpl organizations;
    IdentityWriteProperties properties;
    IdentitySourceSnapshotService snapshots;

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64) PRIMARY KEY, username VARCHAR(50) UNIQUE, password VARCHAR(100) NOT NULL, nickname VARCHAR(50), email VARCHAR(100), phone VARCHAR(20), avatar VARCHAR(255), status CHAR(1), org_id VARCHAR(64), dept_id VARCHAR(64), password_reset_required INT DEFAULT 0, token_version BIGINT DEFAULT 0, deleted INT DEFAULT 0, create_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_role (id VARCHAR(64) PRIMARY KEY, role_name VARCHAR(50), role_code VARCHAR(50) UNIQUE, description VARCHAR(200), sort_order INT, status CHAR(1) DEFAULT '0', create_time TIMESTAMP, update_time TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_user_role (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, role_id VARCHAR(64) NOT NULL, create_time TIMESTAMP, UNIQUE(user_id, role_id))");
        // 组织使用真实领域服务及 Mapper，包含 V129 结构，迁移本身另由 db-migrator 的测试验证。
        jdbc.execute("CREATE TABLE sys_organization (id VARCHAR(64) PRIMARY KEY, org_code VARCHAR(100) UNIQUE, org_name VARCHAR(100), parent_id VARCHAR(64), type VARCHAR(20), hierarchy_ready SMALLINT DEFAULT 1 NOT NULL, level INT, path VARCHAR(500), business_level_code VARCHAR(64), sort_order INT, leader_id VARCHAR(64), leader_name VARCHAR(100), status CHAR(1), deleted INT DEFAULT 0, phone VARCHAR(50), email VARCHAR(100), address VARCHAR(200), description VARCHAR(500), create_by VARCHAR(64), create_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE entity_definition (id BIGINT, table_name VARCHAR(100), deleted INT)");
        jdbc.execute("CREATE TABLE entity_field (entity_id BIGINT, field_code VARCHAR(100), is_required INT)");
        String migration = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V126__identity_source_write_and_external_accounts.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
        new ResourceDatabasePopulator(new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
        jdbc.execute("ALTER TABLE sys_identity_source_binding ADD parent_external_id VARCHAR(255) NULL");
        jdbc.execute("ALTER TABLE sys_identity_source_binding ADD parent_source_key VARCHAR(64) NULL");
        jdbc.execute("CREATE INDEX idx_identity_parent ON sys_identity_source_binding(parent_source_key)");
        jdbc.execute("CREATE TABLE sys_identity_source_snapshot(source_key CHAR(64) PRIMARY KEY, source_version BIGINT, payload_hash CHAR(64) NOT NULL, raw_json CLOB NOT NULL, raw_hash CHAR(64) NOT NULL, raw_bytes INTEGER NOT NULL, is_current SMALLINT DEFAULT 1 NOT NULL, recorded_at TIMESTAMP(6) NOT NULL, recorded_by VARCHAR(64) NOT NULL)");
        for (String name : List.of("first", "second", "local")) {
            jdbc.execute("CREATE TABLE biz_test_" + name + " (user_id VARCHAR(64) PRIMARY KEY, value_text VARCHAR(64), row_version BIGINT NOT NULL DEFAULT 0)");
        }
        jdbc.execute("CREATE TABLE biz_test_numbers (user_id VARCHAR(64) PRIMARY KEY, small INTEGER, large BIGINT, amount DECIMAL(30, 8), row_version BIGINT NOT NULL DEFAULT 0)");
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(IdentityPersistenceMapper.class);
        configuration.addMapper(IdentitySourceSnapshotMapper.class);
        configuration.addMapper(SysOrganizationMapper.class);
        configuration.addMapper(SysRoleMapper.class);
        configuration.addMapper(SysUserRoleMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var persistence = session.getMapper(IdentityPersistenceMapper.class);
        var organizationMapper = session.getMapper(SysOrganizationMapper.class);
        var roleMapper = session.getMapper(SysRoleMapper.class);
        store = new IdentitySourceStore(persistence);
        properties = new IdentityWriteProperties();
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
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(anyString())).thenAnswer(invocation -> {
            var found = jdbc.query("SELECT * FROM sys_user WHERE id = ? AND deleted = 0", (rs, n) -> {
                SysUser user = new SysUser(); user.setId(rs.getString("id")); user.setUsername(rs.getString("username")); user.setStatus(rs.getString("status")); return user;
            }, (String) invocation.getArgument(0));
            return found.isEmpty() ? null : found.get(0);
        });
        // 默认角色使用真实角色关联方法及 Mapper，归属校验和会话撤销继续保持原测试边界。
        users = spy(new SysUserService(userMapper, roleMapper, session.getMapper(SysUserRoleMapper.class),
                null, organizationMapper, null, null));
        doNothing().when(users).validateProvisionedMembership(any());
        doNothing().when(users).revokeSessions(anyString());
        organizations = new SysOrganizationServiceImpl(organizationMapper, userMapper, mock(SysDictItemMapper.class));
        audit = mock(SystemAuditPort.class);
        var hierarchy = new IdentityOrganizationHierarchyService(store, persistence, organizationMapper);
        snapshots = new IdentitySourceSnapshotService(session.getMapper(IdentitySourceSnapshotMapper.class), properties, access, audit);
        service = new IdentityWriteService(store, access, extensions, persistence, mapper, users, userMapper, roleMapper, organizations, hierarchy, audit, snapshots);
    }

    @Test
    void simultaneousFirstWritesConvergeToOneLocalIdentity() throws Exception {
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('employee-role', 'employee')");
        source.setDefaultRoleCode("employee");
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Callable<IdentitySaveResult> call = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                return save(raw(user("concurrent", 1L, "a", null, List.of(first("T1"))), "{\"id\":\"concurrent\"}"));
            };
            var a = executor.submit(call); var b = executor.submit(call);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertEquals(first.localId(), second.localId());
            assertEquals(Set.of(IdentitySaveOutcome.CREATED, IdentitySaveOutcome.UNCHANGED), Set.of(first.outcome(), second.outcome()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
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
        var updated = save(user("E1", 2L, "old", null, List.of(first("T2"))));
        assertEquals(first.localId(), updated.localId());
        assertEquals(first.canonicalUsername(), updated.canonicalUsername());
        assertEquals("old", updated.canonicalUsername());
        assertEquals("EXTERNAL_ONLY", jdbc.queryForObject("SELECT authentication_mode FROM sys_user", String.class));
        assertNull(jdbc.queryForObject("SELECT password FROM sys_user", String.class));
        assertNull(jdbc.queryForObject("SELECT email FROM sys_user", String.class));
        assertEquals("Java", jdbc.queryForObject("SELECT value_text FROM biz_test_second", String.class));
        assertEquals("old", jdbc.queryForObject("SELECT external_account FROM sys_identity_source_binding", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    /** 两个独立空库同步相同来源快照后，配置引用所用的用户名和组织编码一致。 */
    @Test
    void usesUnmodifiedSourceCodesAcrossIndependentDirectories() throws Exception {
        String account = " Alice_目录 ";
        String orgCode = " ORG-目录 ";
        var firstUser = save(user("E1", 1L, account, null, List.of()));
        var firstOrganization = tx.execute(s -> service.saveOrganization(organization(orgCode, null)));
        String firstSourceKey = jdbc.queryForObject(
                "SELECT source_key FROM sys_identity_source_binding WHERE object_type = 'USER'", String.class);
        assertEquals(orgCode, organizations.getById(firstOrganization.localId()).getOrgCode());

        setup();
        var secondUser = save(user("E1", 1L, account, null, List.of()));
        var secondOrganization = tx.execute(s -> service.saveOrganization(organization(orgCode, null)));
        assertNotEquals(firstUser.localId(), secondUser.localId());
        assertNotEquals(firstOrganization.localId(), secondOrganization.localId());
        assertEquals(account, firstUser.canonicalUsername());
        assertEquals(account, secondUser.canonicalUsername());
        assertEquals(orgCode, organizations.getById(secondOrganization.localId()).getOrgCode());
        assertEquals(firstSourceKey, jdbc.queryForObject(
                "SELECT source_key FROM sys_identity_source_binding WHERE object_type = 'USER'", String.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "123456789012345678901234567890123456789012345678901"})
    void rejectsMissingOrOverlongUsernameOnCreateAndUpdate(String account) {
        var error = assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 1L, account, null, List.of())));
        assertEquals("INVALID_MODEL", error.code());
        assertEquals("account", error.field());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_binding", Integer.class));

        save(user("E1", 1L, "alice", null, List.of()));
        var updateError = assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 2L, account, null, List.of())));
        assertEquals("INVALID_MODEL", updateError.code());
        assertEquals("account", updateError.field());
        assertEquals("alice", jdbc.queryForObject("SELECT username FROM sys_user", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT source_version FROM sys_identity_source_binding", Long.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrganizationCodeOnCreateAndUpdate(String code) {
        var model = new OrganizationSaveModel(new ExternalIdentityKey("erp", "O1"), 1L,
                OrganizationBaseData.builder().externalCode(code).name("组织")
                        .type(OrganizationUnitType.ORGANIZATION).enabled(true).build(), List.of());
        var error = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganization(model)));
        assertEquals("INVALID_MODEL", error.code());
        assertEquals("externalCode", error.field());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_binding", Integer.class));

        var created = tx.execute(s -> service.saveOrganization(organization("O1", null)));
        var update = new OrganizationSaveModel(model.key(), 2L, model.base(), model.extensions());
        var updateError = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganization(update)));
        assertEquals("INVALID_MODEL", updateError.code());
        assertEquals("externalCode", updateError.field());
        assertEquals("O1", organizations.getById(created.localId()).getOrgCode());
        assertEquals(1L, jdbc.queryForObject("SELECT source_version FROM sys_identity_source_binding", Long.class));
    }

    @Test
    void acceptsMaximumCodeLengthsAndRejectsOverlongOrganizationCode() {
        String account = "a".repeat(50);
        String code = "o".repeat(100);
        assertEquals(account, save(user("E1", 1L, account, null, List.of())).canonicalUsername());
        var organization = new OrganizationSaveModel(new ExternalIdentityKey("erp", "O1"), 1L,
                OrganizationBaseData.builder().externalCode(code).name("组织")
                        .type(OrganizationUnitType.ORGANIZATION).enabled(true).build(), List.of());
        var created = tx.execute(s -> service.saveOrganization(organization));
        assertEquals(code, organizations.getById(created.localId()).getOrgCode());
        var overlong = new OrganizationSaveModel(new ExternalIdentityKey("erp", "O2"), 1L,
                OrganizationBaseData.builder().externalCode(code + "x").name("组织")
                        .type(OrganizationUnitType.ORGANIZATION).enabled(true).build(), List.of());
        var error = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganization(overlong)));
        assertEquals("INVALID_MODEL", error.code());
        assertEquals("externalCode", error.field());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Integer.class));
        var update = new OrganizationSaveModel(organization.key(), 2L, overlong.base(), List.of());
        var updateError = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganization(update)));
        assertEquals("INVALID_MODEL", updateError.code());
        assertEquals("externalCode", updateError.field());
        assertEquals(code, organizations.getById(created.localId()).getOrgCode());
    }

    @Test
    void rejectsChangedUserCodeAndRollsBackSnapshot() {
        var created = save(user("E1", 1L, "alice", "old@example.com", List.of(first("old"))));
        var error = assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 2L, "bob", "new@example.com", List.of(first("new")))));
        assertEquals("SOURCE_CODE_IMMUTABLE", error.code());
        assertEquals("account", error.field());
        assertEquals("alice", jdbc.queryForObject("SELECT username FROM sys_user", String.class));
        assertEquals("old@example.com", jdbc.queryForObject("SELECT email FROM sys_user", String.class));
        assertEquals("old", jdbc.queryForObject("SELECT value_text FROM biz_test_first", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT source_version FROM sys_identity_source_binding", Long.class));
        assertEquals(created.localId(), save(user("E1", 2L, "alice", null, List.of())).localId());
    }

    @Test
    void rejectsChangedOrganizationCodeAndRollsBackSnapshot() {
        var original = organization("O1", null);
        var created = tx.execute(s -> service.saveOrganization(original));
        var changed = new OrganizationSaveModel(original.key(), 2L,
                OrganizationBaseData.builder().externalCode("O2").name("变更名称")
                        .type(OrganizationUnitType.ORGANIZATION).enabled(true).build(), List.of());
        var error = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganization(changed)));
        assertEquals("SOURCE_CODE_IMMUTABLE", error.code());
        assertEquals("externalCode", error.field());
        assertEquals("O1", organizations.getById(created.localId()).getOrgCode());
        assertEquals("O1", organizations.getById(created.localId()).getOrgName());
        assertEquals(1L, jdbc.queryForObject("SELECT source_version FROM sys_identity_source_binding", Long.class));
    }

    /** 各来源共用全局唯一编码空间，逻辑删除也不能使其他来源占用已有编码。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsCodeCollisionsAcrossSourcesIncludingDeletedObjects(boolean deleted) {
        var createdUser = save(user("E1", 1L, "alice", null, List.of()));
        var originalOrganization = organization("ORG-1", null);
        var createdOrganization = tx.execute(s -> service.saveOrganization(originalOrganization));
        if (deleted) {
            jdbc.update("UPDATE sys_user SET deleted = 1 WHERE id = ?", createdUser.localId());
            jdbc.update("UPDATE sys_organization SET deleted = 1 WHERE id = ?", createdOrganization.localId());
        }
        var otherUser = new UserSaveModel(new ExternalIdentityKey("other", "E1"), 1L,
                user("E1", 1L, "alice", null, List.of()).base(), List.of());
        var userError = assertThrows(IdentityWriteException.class, () -> save(otherUser));
        assertEquals("IDENTITY_CODE_CONFLICT", userError.code());
        assertEquals("account", userError.field());
        var otherOrganization = new OrganizationSaveModel(new ExternalIdentityKey("other", "ORG-1"), 1L,
                originalOrganization.base(), List.of());
        var orgError = assertThrows(IdentityWriteException.class,
                () -> tx.execute(s -> service.saveOrganization(otherOrganization)));
        assertEquals("IDENTITY_CODE_CONFLICT", orgError.code());
        assertEquals("externalCode", orgError.field());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_binding", Integer.class));
    }

    /** 省略或留空默认角色时，沿用已有来源接入的无初始角色行为。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void createsUsersWithoutRolesWhenDefaultRoleIsAbsent(String roleCode) {
        source.setDefaultRoleCode(roleCode);
        assertEquals(IdentitySaveOutcome.CREATED, save(user("E1", 1L, "a", null, List.of())).outcome());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class));
        verify(users, never()).saveUserRoles(anyString(), anyList());
    }

    /** 单条和批量首次创建均按角色编码解析，重复提交复用身份及同一条角色关联。 */
    @Test
    void assignsConfiguredRoleToNewUsersInSingleAndBatchWrites() {
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('employee-role', 'employee')");
        source.setDefaultRoleCode("employee");
        var model = user("E1", 1L, "a", null, List.of());
        var created = save(model);
        assertEquals("employee-role", jdbc.queryForObject(
                "SELECT role_id FROM sys_user_role WHERE user_id = ?", String.class, created.localId()));
        var results = tx.execute(s -> service.saveUsers(List.of(model, user("E2", 1L, "b", null, List.of()))));
        assertEquals(IdentitySaveOutcome.UNCHANGED, results.get(0).outcome());
        assertEquals(IdentitySaveOutcome.CREATED, results.get(1).outcome());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE role_id = 'employee-role'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    /** 默认角色配置只影响新身份，后续改配或角色失效时仍保留管理员维护的角色集合。 */
    @Test
    void userUpdatesPreserveLocalRolesWhenDefaultRoleChangesOrBecomesInvalid() {
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('employee-role', 'employee'), ('local-role', 'local')");
        source.setDefaultRoleCode("employee");
        var created = save(user("E1", 1L, "a", null, List.of()));
        tx.executeWithoutResult(s -> users.saveUserRoles(created.localId(), List.of("local-role")));
        source.setDefaultRoleCode("missing");
        assertEquals(IdentitySaveOutcome.UPDATED, save(user("E1", 2L, "a", null, List.of())).outcome());
        assertEquals("local-role", jdbc.queryForObject("SELECT role_id FROM sys_user_role", String.class));
        verify(users, times(2)).saveUserRoles(eq(created.localId()), anyList());
    }

    /** 已同步的无角色用户在后续新增来源配置时继续保留本地角色状态。 */
    @Test
    void addingDefaultRoleConfigurationOnlyAffectsLaterNewUsers() {
        var existing = save(user("E1", 1L, "a", null, List.of()));
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('employee-role', 'employee')");
        source.setDefaultRoleCode("employee");
        save(user("E1", 2L, "a", null, List.of()));
        var created = save(user("E2", 1L, "c", null, List.of()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id = ?", Integer.class, existing.localId()));
        assertEquals("employee-role", jdbc.queryForObject("SELECT role_id FROM sys_user_role WHERE user_id = ?", String.class, created.localId()));
    }

    /** 配置缺失、禁用或逻辑删除的角色时拒绝创建，避免接入成功却漏授角色。 */
    @ParameterizedTest
    @CsvSource({"other,0,0", "employee,1,0", "employee,0,1"})
    void unavailableDefaultRoleRollsBackNewUser(String storedCode, String status, int deleted) {
        jdbc.update("INSERT INTO sys_role(id, role_code, status, deleted) VALUES ('employee-role', ?, ?, ?)", storedCode, status, deleted);
        source.setDefaultRoleCode("employee");
        var error = assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 1L, "a", null, List.of(first("T1")))));
        assertEquals("DEFAULT_ROLE_NOT_AVAILABLE", error.code());
        assertEquals("defaultRoleCode", error.field());
        for (String table : List.of("sys_user", "sys_user_role", "sys_identity_source_binding", "biz_test_first")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
    }

    /** 组织同步共用来源配置，用户默认角色只在用户首次创建时解析。 */
    @Test
    void organizationWritesIgnoreUserDefaultRole() {
        source.setDefaultRoleCode("missing");
        assertEquals(IdentitySaveOutcome.CREATED, tx.execute(s -> service.saveOrganization(organization("root", null))).outcome());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class));
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
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('employee-role', 'employee')");
        source.setDefaultRoleCode("employee");
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
        assertThrows(IllegalStateException.class, () -> save(user("E1", 1L, "a", null, List.of(first("T1")))));
        for (String table : List.of("sys_user", "sys_user_role", "sys_identity_source_binding", "biz_test_first")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
    }

    @Test
    void laterBatchFailureRollsBackEarlierSuccessfulRows() {
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('employee-role', 'employee')");
        source.setDefaultRoleCode("employee");
        var bad = UserSaveModel.builder().key(new ExternalIdentityKey("erp", "E2")).sourceVersion(1L)
                .base(UserBaseData.builder().account("bad").nickname("bad").organizationExternalId("missing").enabled(true).build()).extensions(List.of()).build();
        assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveUsers(List.of(user("E1", 1L, "a", null, List.of()), bad))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class));
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

    /** 祖先跨调用逆序到达时，接收基础资料后保持隐藏，根节点到达一次性补齐整条父链。 */
    @Test
    void singleOrganizationsConnectWhenAncestorsArriveInLaterCalls() {
        var childModel = organization("child", "parent");
        var child = tx.execute(s -> service.saveOrganization(childModel));
        assertFalse(organizations.getById(child.localId()).isHierarchyReady());
        assertNull(organizations.getById(child.localId()).getParentId());
        assertNull(organizations.getById(child.localId()).getPath());
        assertTrue(organizations.getOrgTree(null).isEmpty());
        assertTrue(organizations.getOrgTree("dept").isEmpty());

        var parent = tx.execute(s -> service.saveOrganization(organization("parent", "root")));
        assertEquals(parent.localId(), organizations.getById(child.localId()).getParentId());
        assertFalse(organizations.getById(child.localId()).isHierarchyReady());
        assertTrue(organizations.getOrgTree(null).isEmpty());

        var root = tx.execute(s -> service.saveOrganization(organization("root", null)));
        var connected = organizations.getById(child.localId());
        assertTrue(connected.isHierarchyReady());
        assertEquals(2, connected.getLevel());
        assertEquals("/0/" + root.localId() + "/" + parent.localId() + "/" + child.localId() + "/", connected.getPath());
        assertEquals(child.localId(), organizations.getOrgTree(null).get(0).getChildren().get(0).getChildren().get(0).getId());
        assertEquals(IdentitySaveOutcome.UNCHANGED, tx.execute(s -> service.saveOrganization(childModel)).outcome());
        assertEquals(1L, jdbc.queryForObject("SELECT source_version FROM sys_identity_source_binding WHERE local_id = ?", Long.class, child.localId()));
    }

    /** 一批只含当前到达的子级也可提交，后续单条父级写入会自动接上，返回顺序继续与输入一致。 */
    @Test
    void organizationBatchAllowsMissingParentsOutsideTheBatch() {
        var results = tx.execute(s -> service.saveOrganizations(List.of(organization("C", "B"), organization("B", "A"))));
        assertEquals(List.of("C", "B"), results.stream().map(IdentitySaveResult::externalId).toList());
        assertTrue(organizations.getOrgTree(null).isEmpty());
        tx.execute(s -> service.saveOrganization(organization("A", null)));
        assertTrue(organizations.getById(results.get(0).localId()).isHierarchyReady());
        assertEquals(2, organizations.getById(results.get(0).localId()).getLevel());
    }

    /** 父子同时提交时共享目录事务锁，无论哪一条先获得锁，提交后都必须形成完整子树。 */
    @Test
    void concurrentParentAndChildWritesConvergeToACompleteHierarchy() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<IdentitySaveResult>>();
            for (var model : List.of(organization("child", "parent"), organization("parent", null))) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    return tx.execute(s -> service.saveOrganization(model));
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var child = futures.get(0).get(10, TimeUnit.SECONDS);
            var parent = futures.get(1).get(10, TimeUnit.SECONDS);
            var connected = organizations.getById(child.localId());
            assertTrue(connected.isHierarchyReady());
            assertEquals(parent.localId(), connected.getParentId());
            assertEquals(1, connected.getLevel());
            assertEquals("/0/" + parent.localId() + "/" + child.localId() + "/", connected.getPath());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /** 待关联引用遵守来源版本，旧父级到达或旧快照重试都不能覆盖已提交的新父级引用。 */
    @Test
    void pendingParentChangesRespectVersionsAndIgnoreOldParents() {
        var original = organization("child", "old-parent");
        var child = tx.execute(s -> service.saveOrganization(original));
        var changed = organization("child", "new-parent");
        var update = new OrganizationSaveModel(changed.key(), 2L, changed.base(), changed.extensions());
        assertEquals(IdentitySaveOutcome.UPDATED, tx.execute(s -> service.saveOrganization(update)).outcome());
        tx.execute(s -> service.saveOrganization(organization("old-parent", null)));
        assertFalse(organizations.getById(child.localId()).isHierarchyReady());
        assertNull(organizations.getById(child.localId()).getParentId());
        assertEquals(IdentitySaveOutcome.STALE_IGNORED, tx.execute(s -> service.saveOrganization(original)).outcome());
        var parent = tx.execute(s -> service.saveOrganization(organization("new-parent", null)));
        assertEquals(parent.localId(), organizations.getById(child.localId()).getParentId());
        assertTrue(organizations.getById(child.localId()).isHierarchyReady());
    }

    /** 改挂到尚未到达的父级时，已有来源与本地下级的旧路径必须失效，补齐后连同层级恢复。 */
    @Test
    void movingToMissingAncestorInvalidatesAndRebuildsTheWholeSubtree() {
        var root = tx.execute(s -> service.saveOrganization(organization("root", null)));
        var parent = tx.execute(s -> service.saveOrganization(organization("parent", "root")));
        var child = tx.execute(s -> service.saveOrganization(organization("child", "parent")));
        var local = new SysOrganization();
        local.setOrgCode("LOCAL"); local.setOrgName("local"); local.setType("dept"); local.setParentId(child.localId());
        tx.execute(s -> organizations.saveOrg(local));
        var moved = organization("parent", "future");
        moved = new OrganizationSaveModel(moved.key(), 2L, moved.base(), moved.extensions());
        var update = moved;
        tx.execute(s -> service.saveOrganization(update));
        for (String id : List.of(parent.localId(), child.localId(), local.getId())) {
            assertFalse(organizations.getById(id).isHierarchyReady());
            assertNull(organizations.getById(id).getPath());
            assertNull(organizations.getById(id).getLevel());
        }
        assertEquals(root.localId(), organizations.getOrgTree(null).get(0).getId());
        assertNull(organizations.getOrgTree(null).get(0).getChildren());

        var future = tx.execute(s -> service.saveOrganization(organization("future", "root")));
        assertEquals(future.localId(), organizations.getById(parent.localId()).getParentId());
        assertEquals(4, organizations.getById(local.getId()).getLevel());
        assertTrue(organizations.getById(local.getId()).getPath().startsWith(organizations.getById(future.localId()).getPath()));
    }

    /** 后续来数不能让已保存的待关联父链闭环，自引用同样必须在任何基础写入前失败。 */
    @Test
    void rejectsCyclesAcrossCallsAndSelfReferences() {
        tx.execute(s -> service.saveOrganization(organization("A", "B")));
        assertEquals("INVALID_ORG_HIERARCHY", assertThrows(IdentityWriteException.class,
                () -> tx.execute(s -> service.saveOrganization(organization("B", "A")))).code());
        assertEquals("INVALID_ORG_HIERARCHY", assertThrows(IdentityWriteException.class,
                () -> tx.execute(s -> service.saveOrganization(organization("self", "self")))).code());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Integer.class));
    }

    /** 后到部门不能接走组织类型的子级，失败后可以用合法的同版本父级快照重新提交。 */
    @Test
    void lateParentTypeIsValidatedAgainstPreviouslyAcceptedChildren() {
        var child = organization("organization", "parent");
        var base = child.base();
        var organization = new OrganizationSaveModel(child.key(), child.sourceVersion(),
                new OrganizationBaseData(base.externalCode(), base.name(), OrganizationUnitType.ORGANIZATION,
                        base.parentExternalId(), null, null, null, null, null, 0, true), List.of());
        tx.execute(s -> service.saveOrganization(organization));
        assertEquals("INVALID_ORG_HIERARCHY", assertThrows(IdentityWriteException.class,
                () -> tx.execute(s -> service.saveOrganization(organization("parent", "missing-root")))).code());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Integer.class));
        tx.execute(s -> service.saveOrganization(organization("parent", null)));
        assertTrue(organizations.getOrgTree(null).get(0).getChildren().get(0).isHierarchyReady());
    }

    /** 接上子树与父级保存、来源版本及必需审计一起提交，失败后的同版本重试仍可完成关联。 */
    @Test
    void lateParentAuditFailureRollsBackAttachmentsAndCanBeRetried() {
        var child = tx.execute(s -> service.saveOrganization(organization("child", "parent")));
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
        assertThrows(IllegalStateException.class,
                () -> tx.execute(s -> service.saveOrganization(organization("parent", null))));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Integer.class));
        assertNull(organizations.getById(child.localId()).getParentId());
        assertFalse(organizations.getById(child.localId()).isHierarchyReady());
        reset(audit);
        tx.execute(s -> service.saveOrganization(organization("parent", null)));
        assertTrue(organizations.getById(child.localId()).isHierarchyReady());
    }

    /** 父级关联严格匹配来源和外部 ID 大小写，其他来源或相似编码到达不能误关联。 */
    @Test
    void pendingParentsKeepExactSourceAndExternalIdIdentity() {
        var child = tx.execute(s -> service.saveOrganization(organization("child", "Parent")));
        tx.execute(s -> service.saveOrganization(organization("parent", null)));
        var other = organization("other-parent-code", null);
        var otherSource = new OrganizationSaveModel(new ExternalIdentityKey("other", "Parent"), other.sourceVersion(), other.base(), other.extensions());
        tx.execute(s -> service.saveOrganization(otherSource));
        assertFalse(organizations.getById(child.localId()).isHierarchyReady());
        var parent = tx.execute(s -> service.saveOrganization(organization("Parent", null)));
        assertEquals(parent.localId(), organizations.getById(child.localId()).getParentId());
        assertTrue(organizations.getById(child.localId()).isHierarchyReady());
    }

    /** 本地状态接口使用局部 DTO，不能因其默认值覆盖待关联状态，也不能清空完整节点的派生字段。 */
    @Test
    void localStatusUpdatesPreservePendingAndCompleteHierarchy() {
        var pending = tx.execute(s -> service.saveOrganization(organization("pending", "missing")));
        tx.executeWithoutResult(s -> organizations.updateStatus(pending.localId(), "1"));
        assertFalse(organizations.getById(pending.localId()).isHierarchyReady());
        assertNull(organizations.getById(pending.localId()).getParentId());
        var root = tx.execute(s -> service.saveOrganization(organization("root", null)));
        String path = organizations.getById(root.localId()).getPath();
        tx.executeWithoutResult(s -> organizations.updateStatus(root.localId(), "1"));
        assertEquals(path, organizations.getById(root.localId()).getPath());
        assertEquals("0", organizations.getById(root.localId()).getParentId());
        assertTrue(organizations.getById(root.localId()).isHierarchyReady());
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

    /** 来源写权限关闭后，扩展组仍按声明的写权限校验，拒绝时不能留下基础资料或来源映射。 */
    @Test
    void sourcePermissionOptOutStillRequiresExtensionWritePermission() {
        source.setPermissionCheckEnabled(false);
        doThrow(new IdentityWriteException("FORBIDDEN", "permission", "没有扩展写权限"))
                .when(access).requirePermission("actor", "write");
        assertEquals("FORBIDDEN", assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 1L, "a", null, List.of(first("T1"))))).code());
        verify(access).requirePermission("actor", "write");
        for (String table : List.of("sys_user", "sys_identity_source_binding", "biz_test_first")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
    }

    @Test
    void rawDataIsExactDeduplicatedAndSeparateFromBusinessHash() {
        var model = user("E1", Long.MAX_VALUE, "alice", null, List.of(first("T1")));
        String raw = " {\n\"id\":9223372036854775807123, \"html\":\"<script>alert(1)</script>\",\"姓名\":\"张三\"} ";
        var created = save(raw(model, raw));
        String businessHash = jdbc.queryForObject("SELECT payload_hash FROM sys_identity_source_binding", String.class);
        var time = jdbc.queryForObject("SELECT recorded_at FROM sys_identity_source_snapshot", java.sql.Timestamp.class);
        clearInvocations(users, audit);
        assertEquals(IdentitySaveOutcome.UNCHANGED, save(raw(model, raw)).outcome());
        assertEquals(time, jdbc.queryForObject("SELECT recorded_at FROM sys_identity_source_snapshot", java.sql.Timestamp.class));
        verifyNoInteractions(audit);
        assertEquals(IdentitySaveOutcome.UNCHANGED, save(raw(model, raw + " ")).outcome());
        verify(users, never()).saveUserRoles(anyString(), anyList());
        assertEquals(businessHash, jdbc.queryForObject("SELECT payload_hash FROM sys_identity_source_binding", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
        UserContext.setCurrentUser("actor", "admin");
        try {
            var view = snapshots.read(IdentityObjectType.USER, created.localId());
            assertEquals(raw + " ", view.rawDataJson());
            assertEquals("9223372036854775807", view.currentSourceVersion());
            assertEquals("CURRENT", view.state());
            assertEquals((raw + " ").getBytes(StandardCharsets.UTF_8).length, view.rawBytes());
            verify(access).requirePermission("actor", "system:user:view");
            verify(access).requirePermission("actor", "system:user:manage");
            verify(access).requireObject(IdentityObjectType.USER, created.localId());
        } finally { UserContext.clear(); }
    }

    @Test
    void unchangedCanFillOldRecordsAndLargeClobWithoutBusinessWrites() {
        var model = user("E1", 1L, "alice", null, List.of(first("T1")));
        save(model);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
        Long extensionVersion = jdbc.queryForObject("SELECT row_version FROM biz_test_first", Long.class);
        String raw = "{\"large\":\"" + "汉".repeat(20000) + "\"}";
        clearInvocations(users);
        assertEquals(IdentitySaveOutcome.UNCHANGED, save(raw(model, raw)).outcome());
        assertEquals(raw, jdbc.queryForObject("SELECT raw_json FROM sys_identity_source_snapshot", String.class));
        assertEquals(extensionVersion, jdbc.queryForObject("SELECT row_version FROM biz_test_first", Long.class));
        verifyNoInteractions(users);
    }

    @Test
    void serialReturnToPreviousBusinessStillLeavesMissingRawStaleUntilExplicitlySupplied() {
        source.setVersionMode(IdentityWriteProperties.VersionMode.SERIAL);
        var original = user("E1", null, "alice", "a@example.com", List.of());
        save(raw(original, "{\"original\":true}"));
        var time = jdbc.queryForObject("SELECT recorded_at FROM sys_identity_source_snapshot", java.sql.Timestamp.class);
        save(user("E1", null, "alice", "b@example.com", List.of()));
        save(original);
        assertEquals(0, jdbc.queryForObject("SELECT is_current FROM sys_identity_source_snapshot", Integer.class));
        assertEquals(time, jdbc.queryForObject("SELECT recorded_at FROM sys_identity_source_snapshot", java.sql.Timestamp.class));
        assertEquals(IdentitySaveOutcome.UNCHANGED, save(raw(original, "{\"original\":true}")).outcome());
        assertEquals(1, jdbc.queryForObject("SELECT is_current FROM sys_identity_source_snapshot", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "[]", "1", "true", "{", "{} {}", "{\"id\":1,\"id\":2}", "{\"nested\":{\"id\":1,\"id\":2}}", "{\"id\":1,}", "{/*comment*/}"})
    void rejectsInvalidRawWithoutLeakingSourceTextOrSavingAnything(String raw) {
        var error = assertThrows(IdentityWriteException.class, () -> save(raw(user("E1", 1L, "alice", null, List.of()), raw)));
        assertEquals("INVALID_RAW_DATA", error.code());
        assertNull(error.getCause());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
    }

    @Test
    void requiredRawAppliesToAcceptedAndUnchangedButStaleOnlyHasSizeGuard() {
        var first = user("E1", 2L, "alice", null, List.of());
        save(raw(first, "{}")); source.setRawDataRequired(true);
        assertEquals("RAW_DATA_REQUIRED", assertThrows(IdentityWriteException.class,
                () -> save(user("E2", 1L, "bob", null, List.of()))).code());
        assertEquals("RAW_DATA_REQUIRED", assertThrows(IdentityWriteException.class, () -> save(first)).code());
        assertEquals("RAW_DATA_REQUIRED", assertThrows(IdentityWriteException.class,
                () -> save(user("E1", 3L, "alice", null, List.of()))).code());
        var stale = user("E1", 1L, "alice", null, List.of());
        assertEquals(IdentitySaveOutcome.STALE_IGNORED, save(stale).outcome());
        assertEquals(IdentitySaveOutcome.STALE_IGNORED, save(raw(stale, "bad json")).outcome());
        properties.setRawDataMaxBytes(2);
        assertEquals("RAW_DATA_TOO_LARGE", assertThrows(IdentityWriteException.class, () -> save(raw(stale, "bad json"))).code());
        assertEquals("{}", jdbc.queryForObject("SELECT raw_json FROM sys_identity_source_snapshot", String.class));
    }

    @Test
    void enforcesUtf8SingleAndCumulativeBatchLimitsBeforeAnyWrite() {
        var first = user("E1", 1L, "alice", null, List.of());
        properties.setRawDataMaxBytes(10);
        assertEquals("RAW_DATA_TOO_LARGE", assertThrows(IdentityWriteException.class,
                () -> save(raw(first, "{\"a\":\"汉\"}"))).code());
        properties.setRawDataMaxBytes(100); properties.setRawDataMaxBatchBytes(3);
        var error = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveUsers(List.of(
                raw(first, "{}"), raw(user("E2", 1L, "bob", null, List.of()), "{}")))));
        assertEquals("RAW_DATA_TOO_LARGE", error.code()); assertEquals("models[1].rawDataJson", error.field());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
    }

    @Test
    void snapshotAuditAndBatchFailureRollbackBaseBindingRoleAndRawTogether() {
        jdbc.update("INSERT INTO sys_role(id, role_code) VALUES ('role', 'employee')");
        source.setDefaultRoleCode("employee");
        var first = raw(user("E1", 1L, "alice", null, List.of(first("T1"))), "{}");
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(argThat(e -> "来源原文留存".equals(e.operationName())));
        assertThrows(IllegalStateException.class, () -> save(first));
        for (String table : List.of("sys_user", "sys_user_role", "sys_identity_source_binding", "sys_identity_source_snapshot", "biz_test_first")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        reset(audit);
        var error = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveUsers(List.of(
                first, raw(user("E2", 1L, "bob", null, List.of()), "invalid")))));
        assertEquals("models[1].rawDataJson", error.field());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
        save(first);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
        assertThrows(IllegalStateException.class, () -> save(raw(first, "{\"new\":true}")));
        assertEquals("{}", jdbc.queryForObject("SELECT raw_json FROM sys_identity_source_snapshot", String.class));
    }

    @Test
    void lateParentReconciliationDoesNotRewriteChildOriginal() {
        var child = organization("child", "parent");
        tx.execute(s -> service.saveOrganization(new OrganizationSaveModel(child.key(), child.sourceVersion(), child.base(), child.extensions(), "{\"parentId\":\"parent\"}")));
        var time = jdbc.queryForObject("SELECT recorded_at FROM sys_identity_source_snapshot", java.sql.Timestamp.class);
        tx.execute(s -> service.saveOrganization(organization("parent", null)));
        assertEquals(time, jdbc.queryForObject("SELECT recorded_at FROM sys_identity_source_snapshot", java.sql.Timestamp.class));
        assertEquals("{\"parentId\":\"parent\"}", jdbc.queryForObject("SELECT raw_json FROM sys_identity_source_snapshot", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
    }

    @Test
    void snapshotPersistenceFailureRollsBackUserAndSourceBinding() {
        jdbc.execute("ALTER TABLE sys_identity_source_snapshot ADD CONSTRAINT test_raw_limit CHECK(raw_bytes < 10)");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> save(raw(user("E1", 1L, "alice", null, List.of()), "{\"value\":true}")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_binding", Integer.class));
        verifyNoInteractions(audit);
    }

    @Test
    void organizationBatchKeepsInputOrderAndRawWithItsOwnKeyAndRollsBackOnInvalidRaw() {
        var child = organization("child", "parent"); var parent = organization("parent", null);
        var childRaw = new OrganizationSaveModel(child.key(), 1L, child.base(), List.of(), "{\"id\":\"child\"}");
        var parentRaw = new OrganizationSaveModel(parent.key(), 1L, parent.base(), List.of(), "{\"id\":\"parent\"}");
        var results = tx.execute(s -> service.saveOrganizations(List.of(childRaw, parentRaw)));
        assertEquals(List.of("child", "parent"), results.stream().map(IdentitySaveResult::externalId).toList());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_source_snapshot", Integer.class));
        UserContext.setCurrentUser("actor", "admin");
        try {
            assertEquals(childRaw.rawDataJson(), snapshots.read(IdentityObjectType.ORGANIZATION, results.get(0).localId()).rawDataJson());
            tx.execute(s -> service.saveOrganization(new OrganizationSaveModel(child.key(), 2L, child.base(), List.of())));
            assertEquals("STALE", snapshots.read(IdentityObjectType.ORGANIZATION, results.get(0).localId()).state());
        } finally { UserContext.clear(); }
        var bad = organization("bad", "child");
        var error = assertThrows(IdentityWriteException.class, () -> tx.execute(s -> service.saveOrganizations(List.of(
                new OrganizationSaveModel(child.key(), 2L, child.base(), List.of(), "{\"new\":true}"),
                new OrganizationSaveModel(bad.key(), 1L, bad.base(), List.of(), "[]")))));
        assertEquals("models[1].rawDataJson", error.field());
        assertEquals(childRaw.rawDataJson(), jdbc.queryForObject("SELECT raw_json FROM sys_identity_source_snapshot WHERE source_key = ?", String.class,
                IdentitySourceStore.hash(IdentityObjectType.ORGANIZATION, child.key())));
    }

    @Test
    void sameVersionBusinessConflictDoesNotReplaceRaw() {
        save(raw(user("E1", 1L, "alice", null, List.of()), "{}"));
        assertEquals("SOURCE_VERSION_CONFLICT", assertThrows(IdentityWriteException.class,
                () -> save(raw(user("E1", 1L, "alice", "changed@example.com", List.of()), "{\"new\":true}"))).code());
        assertEquals("{}", jdbc.queryForObject("SELECT raw_json FROM sys_identity_source_snapshot", String.class));
    }

    private UserSaveModel raw(UserSaveModel model, String raw) {
        return new UserSaveModel(model.key(), model.sourceVersion(), model.base(), model.extensions(), raw);
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
