package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.permission.api.response.EntityListScopeBindingDTO;
import com.workflow.entity.permission.api.response.EntityListScopePolicyDTO;
import com.workflow.entity.permission.api.response.EntityListScopeSnapshotDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.api.response.MatchConfigDTO;
import com.workflow.entity.permission.application.DataPermissionEngine.ExplicitListPermission;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeDelegationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataPermissionEngineExplicitListPermissionTest {

    private static final String ENTITY_CODE = "project";
    private static final String INTERNAL_KEY = "__relation_graph_internal__";

    private EntityListScopeService scopeService;
    private PermissionRuleMatcher ruleMatcher;
    private PermissionSqlBuilder sqlBuilder;
    private DataPermissionEngine engine;
    private SysUser user;

    @BeforeEach
    void setUp() {
        scopeService = mock(EntityListScopeService.class);
        EntityListScopeDelegationMapper delegationMapper = mock(
                EntityListScopeDelegationMapper.class);
        ruleMatcher = mock(PermissionRuleMatcher.class);
        sqlBuilder = mock(PermissionSqlBuilder.class);
        EntityListScopeAuditService auditService = mock(
                EntityListScopeAuditService.class);
        engine = new DataPermissionEngine(
                scopeService,
                delegationMapper,
                new ObjectMapper(),
                ruleMatcher,
                sqlBuilder,
                mock(SysUserService.class),
                auditService);
        user = new SysUser();
        user.setId("u-1");
        user.setUsername("reader");
        when(delegationMapper.findActiveByToUserId(
                "u-1", ENTITY_CODE)).thenReturn(List.of());
    }

    @Test
    void legacyObserveWithOnlyMatchedDenyIsNotExplicitAllow() {
        EntityListScopeSnapshotDTO snapshot = snapshotWithBinding("DENY");
        // secureDefaultsVersion 为空表示存量 OBSERVE；最终结果可能仍允许部分
        // 数据，但它绝不能成为高风险内部入口的显式 ALLOW 证据。
        snapshot.setSecureDefaultsVersion(null);
        when(scopeService.getActiveSnapshot(ENTITY_CODE))
                .thenReturn(snapshot);
        when(ruleMatcher.matches(any(), eq(user))).thenReturn(true);
        doNothing().when(sqlBuilder).validateFilter(
                eq(ENTITY_CODE), any());
        when(sqlBuilder.buildFilterSql(
                eq(ENTITY_CODE), any(), eq(user)))
                .thenReturn("secret_flag = 0");

        ExplicitListPermission result = engine
                .calculateExplicitListPermission(
                        ENTITY_CODE, INTERNAL_KEY, user);

        assertTrue(result.permission().isHasPermission());
        assertFalse(result.explicitAllowMatched());
    }

    @Test
    void matchedAllowBindingProducesExplicitEvidence() {
        EntityListScopeSnapshotDTO snapshot = snapshotWithBinding("ALLOW");
        snapshot.setSecureDefaultsVersion(1);
        when(scopeService.getActiveSnapshot(ENTITY_CODE))
                .thenReturn(snapshot);
        when(ruleMatcher.matches(any(), eq(user))).thenReturn(true);
        doNothing().when(sqlBuilder).validateFilter(
                eq(ENTITY_CODE), any());
        when(sqlBuilder.buildFilterSql(
                eq(ENTITY_CODE), any(), eq(user)))
                .thenReturn("owner_id = 'u-1'");

        ExplicitListPermission result = engine
                .calculateExplicitListPermission(
                        ENTITY_CODE, INTERNAL_KEY, user);

        assertTrue(result.permission().isHasPermission());
        assertTrue(result.explicitAllowMatched());
    }

    private EntityListScopeSnapshotDTO snapshotWithBinding(String effect) {
        EntityListScopePolicyDTO policy = new EntityListScopePolicyDTO();
        policy.setId("policy-1");
        policy.setPolicyKey("internal-policy");
        policy.setPolicyName("内部关系图范围");
        policy.setEnabled(1);
        policy.setFilterConfig(new FilterConfigDTO());

        EntityListScopeBindingDTO binding =
                new EntityListScopeBindingDTO();
        binding.setId("binding-1");
        binding.setPolicyId(policy.getId());
        binding.setListKey(INTERNAL_KEY);
        binding.setRuleEffect(effect);
        binding.setEnabled(1);
        binding.setMatchConfig(new MatchConfigDTO());

        EntityListScopeSnapshotDTO snapshot =
                new EntityListScopeSnapshotDTO();
        snapshot.setEntityCode(ENTITY_CODE);
        snapshot.setVersion(3);
        snapshot.setPolicies(List.of(policy));
        snapshot.setBindings(List.of(binding));
        return snapshot;
    }
}
