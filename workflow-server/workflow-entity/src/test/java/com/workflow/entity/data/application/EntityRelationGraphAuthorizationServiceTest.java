package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.InternalPurpose;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.entity.definition.application.model.PublishedRelationPath.Hop;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkField;
import com.workflow.entity.definition.application.model.PublishedRelationPath.LinkValueType;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepType;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.application.DataPermissionEngine;
import com.workflow.entity.permission.application.DataPermissionEngine.ExplicitListPermission;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRelationGraphAuthorizationServiceTest {

    private PublishedRelationPathResolver pathResolver;
    private DataPermissionEngine permissionEngine;
    private EntityRelationGraphInternalCapabilityService capabilityService;
    private SysUser user;
    private EntityRelationGraphAuthorizationService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("u-1", "reader");
        pathResolver = mock(PublishedRelationPathResolver.class);
        permissionEngine = mock(DataPermissionEngine.class);
        SysUserService userService = mock(SysUserService.class);
        user = new SysUser();
        user.setId("u-1");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        when(userService.getById("u-1")).thenReturn(user);
        capabilityService = mock(
                EntityRelationGraphInternalCapabilityService.class);
        service = new EntityRelationGraphAuthorizationService(
                pathResolver,
                mock(EntityActionCapabilityService.class),
                permissionEngine,
                userService,
                capabilityService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void usesFixedServerScopeKeyAndPreservesBoundParametersPerHop() {
        PublishedRelationPath path = path();
        when(pathResolver.validate(path)).thenReturn(path);
        DataPermissionResult permission = DataPermissionResult.withCondition(
                "owner_id = #{permissionParameters.owner}",
                Map.of("owner", "u-1"));
        permission.setMatchedRuleNames(List.of("owner-policy"));
        permission.setReleaseVersion(5);
        permission.setExplanation("使用本列表绑定的允许规则");
        when(permissionEngine.calculateExplicitListPermission(
                any(),
                eq(EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY),
                eq(user)))
                .thenReturn(new ExplicitListPermission(permission, true));

        EntityRelationGraphAuthorizationPlan plan = service.authorizeInternal(
                path, InternalPurpose.PROCESS_COORDINATION);

        assertEquals("u-1", plan.subjectUserId());
        assertEquals(Map.of("owner", "u-1"),
                plan.source().dataScopePlan().parameters());
        assertEquals(
                EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY,
                plan.source().listKey());
        verify(permissionEngine).calculateExplicitListPermission(
                "project",
                EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY,
                user);
        verify(permissionEngine).calculateExplicitListPermission(
                "requirement",
                EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY,
                user);
        verify(capabilityService).require(
                InternalPurpose.PROCESS_COORDINATION);
    }

    @Test
    void rejectsLegacyObserveOrUnboundAllowAll() {
        PublishedRelationPath path = path();
        when(pathResolver.validate(path)).thenReturn(path);
        DataPermissionResult observe = DataPermissionResult.allowAll();
        observe.setExplanation("存量观察期：暂时保持全部可见");
        // 即使旧快照只命中 DENY 并留下 matched 名称，ALLOW 仍来自观察期，
        // 内部入口也必须拒绝，不能把“有 matched”误判成显式允许。
        observe.setMatchedRuleNames(List.of("legacy-deny"));
        when(permissionEngine.calculateExplicitListPermission(
                any(),
                eq(EntityRelationGraphAuthorizationService.INTERNAL_SCOPE_KEY),
                eq(user)))
                .thenReturn(new ExplicitListPermission(observe, false));

        assertThrows(IllegalStateException.class,
                () -> service.authorizeInternal(
                        path, InternalPurpose.VERSION_GRAPH));
    }

    private PublishedRelationPath path() {
        LinkField projectId = new LinkField(
                "projectId",
                LinkValueType.SCALAR_REFERENCE,
                "project_id",
                "project-id");
        Hop hop = new Hop(
                1,
                StepType.RELATION,
                "requirements",
                "project",
                "project-history",
                "project-hash",
                "requirement",
                "requirement-history",
                "requirement-hash",
                null,
                "projectId",
                "requirements",
                "ASSOCIATION",
                true,
                null,
                projectId);
        return new PublishedRelationPath(
                "project",
                "project-history",
                "project-hash",
                List.of(hop));
    }
}
