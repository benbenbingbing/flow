package com.workflow.service.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.permission.api.response.*;
import com.workflow.entity.permission.application.*;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeDelegationMapper;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeDelegation;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 通过真实 MyBatis/JDBC 执行待办权限，覆盖特殊 ID、规则合并、委托和分页计数。 */
class HasTodoPermissionBindingTest {
    private static final String SPECIAL_ID = "审批\\' OR 1=1 -- ${value} #{value}";
    private EmbeddedDatabase database;
    private SqlSession session;
    private EntityDataDynamicMapper mapper;
    private ProcessTaskAccessPort taskAccess;
    private PermissionSqlBuilder builder;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("todo-bindings-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE wf_expense (id VARCHAR(256) PRIMARY KEY, status VARCHAR(32), "
                + "deleted INT DEFAULT 0, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        for (String id : List.of("own", SPECIAL_ID, "delegated", "blocked", "outsider", "deleted")) {
            jdbc.update("INSERT INTO wf_expense (id, status, deleted) VALUES (?, ?, ?)",
                    id, "blocked".equals(id) ? "SECRET" : "OPEN", "deleted".equals(id) ? 1 : 0);
        }
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), database));
        configuration.addMapper(EntityDataDynamicMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(EntityDataDynamicMapper.class);
        EntityPhysicalTableResolver resolver = mock(EntityPhysicalTableResolver.class);
        when(resolver.resolve("expense")).thenReturn("wf_expense");
        taskAccess = mock(ProcessTaskAccessPort.class);
        builder = new PermissionSqlBuilder(null, null, null, List.of(), null, resolver, null, taskAccess);
    }

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POLICY", "CONDITION"})
    void combinedRulesAndDelegationsKeepTheirOwnBindings(String delegationScope) {
        EntityListScopeService scopeService = mock(EntityListScopeService.class);
        EntityListScopeDelegationMapper delegations = mock(EntityListScopeDelegationMapper.class);
        PermissionRuleMatcher matcher = mock(PermissionRuleMatcher.class);
        when(matcher.matches(any(), any())).thenReturn(true);
        SysUserService users = mock(SysUserService.class);
        when(users.getById("delegator")).thenReturn(user("delegator"));
        when(taskAccess.findActionableEntityDataIds("reader", "expense"))
                .thenReturn(List.of("own", SPECIAL_ID, "blocked", "deleted", "own", " "));
        when(taskAccess.findActionableEntityDataIds("delegator", "expense"))
                .thenReturn(List.of("delegated", "blocked"));

        EntityListScopePolicyDTO allow = policy("todo");
        EntityListScopePolicyDTO deny = policy("secret");
        FilterConfigDTO.StatusLimitDTO statusLimit = new FilterConfigDTO.StatusLimitDTO();
        statusLimit.setEnabled(true);
        statusLimit.setValues(List.of("SECRET"));
        deny.getFilterConfig().setStatusLimit(statusLimit);
        EntityListScopeSnapshotDTO snapshot = new EntityListScopeSnapshotDTO();
        snapshot.setSecureDefaultsVersion(1);
        snapshot.setVersion(1);
        snapshot.setPolicies(List.of(allow, deny));
        snapshot.setBindings(List.of(binding("todo", "ALLOW"), binding("secret", "DENY")));
        when(scopeService.getActiveSnapshot("expense")).thenReturn(snapshot);
        EntityListScopeDelegation delegation = new EntityListScopeDelegation();
        delegation.setFromUserId("delegator");
        delegation.setDelegateScope(delegationScope);
        delegation.setPolicyId("todo");
        delegation.setDelegateConfig("{\"type\":\"HAS_TODO\"}");
        when(delegations.findActiveByToUserId("reader", "expense")).thenReturn(List.of(delegation));
        DataPermissionEngine engine = new DataPermissionEngine(scopeService, delegations,
                new ObjectMapper(), matcher, builder, users, mock(EntityListScopeAuditService.class));

        DataPermissionResult permission = engine.calculatePermission("expense", "default", user("reader"));
        String sql = permission.getSqlCondition();
        Map<String, Object> parameters = permission.getSqlParameters();
        assertTrue(permission.isHasPermission());
        assertFalse(sql.contains("CONVERT"));
        assertFalse(sql.contains(SPECIAL_ID));
        Set<String> expected = Set.of("own", SPECIAL_ID, "delegated");
        assertEquals(3, mapper.countWithPermission("wf_expense", sql, parameters));
        assertEquals(expected, ids(mapper.selectPageWithPermission("wf_expense", sql, parameters, 0, 10)));
        assertEquals(expected, ids(mapper.selectListWithPermission("wf_expense", sql, parameters)));
        assertEquals(SPECIAL_ID, mapper.selectByIdWithPermission("wf_expense", SPECIAL_ID, sql, parameters).get("id"));
        assertNull(mapper.selectByIdWithPermission("wf_expense", "outsider", sql, parameters));
        assertNull(mapper.selectByIdWithPermission("wf_expense", "blocked", sql, parameters));
        Map<String, Object> condition = Map.of("status", "OPEN");
        assertEquals(3, mapper.countByConditionWithPermission("wf_expense", condition, sql, parameters));
        assertEquals(expected, ids(mapper.selectPageByConditionWithPermission(
                "wf_expense", condition, sql, parameters, 0, 10)));
    }

    @Test
    void emptyTodoScopeDoesNotCreateAnEmptyInClause() {
        when(taskAccess.findActionableEntityDataIds("reader", "expense")).thenReturn(List.of());
        Map<String, Object> parameters = new LinkedHashMap<>();
        String sql = builder.buildFilterSql("expense", policy("todo").getFilterConfig(), user("reader"), parameters);
        assertEquals(0, mapper.countWithPermission("wf_expense", sql, parameters));
        assertTrue(parameters.isEmpty());
    }

    private Set<String> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (String) row.get("id")).collect(Collectors.toSet());
    }

    private SysUser user(String id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(id);
        return user;
    }

    private EntityListScopePolicyDTO policy(String id) {
        EntityListScopePolicyDTO policy = new EntityListScopePolicyDTO();
        policy.setId(id);
        policy.setEnabled(1);
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("HAS_TODO");
        policy.setFilterConfig(filter);
        return policy;
    }

    private EntityListScopeBindingDTO binding(String policyId, String effect) {
        EntityListScopeBindingDTO binding = new EntityListScopeBindingDTO();
        binding.setPolicyId(policyId);
        binding.setListKey("default");
        binding.setEnabled(1);
        binding.setRuleEffect(effect);
        binding.setMatchConfig(new MatchConfigDTO());
        return binding;
    }
}
