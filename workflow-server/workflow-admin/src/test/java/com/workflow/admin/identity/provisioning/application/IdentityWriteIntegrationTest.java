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

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64) PRIMARY KEY, username VARCHAR(50) UNIQUE, password VARCHAR(100) NOT NULL, nickname VARCHAR(50), email VARCHAR(100), phone VARCHAR(20), avatar VARCHAR(255), status CHAR(1), org_id VARCHAR(64), dept_id VARCHAR(64), password_reset_required INT DEFAULT 0, token_version BIGINT DEFAULT 0, deleted INT DEFAULT 0, create_time TIMESTAMP, update_time TIMESTAMP)");
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
        for (String name : List.of("first", "second", "local")) {
            jdbc.execute("CREATE TABLE biz_test_" + name + " (user_id VARCHAR(64) PRIMARY KEY, value_text VARCHAR(64), row_version BIGINT NOT NULL DEFAULT 0)");
        }
        jdbc.execute("CREATE TABLE biz_test_numbers (user_id VARCHAR(64) PRIMARY KEY, small INTEGER, large BIGINT, amount DECIMAL(30, 8), row_version BIGINT NOT NULL DEFAULT 0)");
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(IdentityPersistenceMapper.class);
        configuration.addMapper(SysOrganizationMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var persistence = session.getMapper(IdentityPersistenceMapper.class);
        var organizationMapper = session.getMapper(SysOrganizationMapper.class);
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
        organizations = new SysOrganizationServiceImpl(organizationMapper, userMapper, mock(SysDictItemMapper.class));
        audit = mock(SystemAuditPort.class);
        var hierarchy = new IdentityOrganizationHierarchyService(store, persistence, organizationMapper);
        service = new IdentityWriteService(store, access, extensions, persistence, mapper, users, userMapper, organizations, hierarchy, audit);
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
        var other = organization("Parent", null);
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
