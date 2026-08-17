package com.workflow.process.assignment.application;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonPrincipalType;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PersonResolverRuntimeSecurityTest {

    @Test
    void excludesDisabledDeletedAndUnknownUsersFromResolverResults() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByUsername("active"))
                .thenReturn(user("active", SysUser.Status.ENABLED, 0));
        when(userMapper.selectByUsername("disabled"))
                .thenReturn(user("disabled", SysUser.Status.DISABLED, 0));
        when(userMapper.selectByUsername("deleted"))
                .thenReturn(user("deleted", SysUser.Status.ENABLED, 1));

        PersonResolver resolver = resolver(List.of(
                PersonPrincipal.user("active"),
                PersonPrincipal.user("disabled"),
                PersonPrincipal.user("deleted"),
                PersonPrincipal.user("unknown"),
                PersonPrincipal.user("active")));
        PersonResolverRuntimeService service = new PersonResolverRuntimeService(
                List.of(resolver),
                userMapper,
                mock(SysRoleMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysGroupMapper.class),
                mock(SysUserGroupMapper.class),
                mock(SysOrganizationMapper.class));

        assertEquals(
                List.of("active"),
                service.resolveUsernames("securityResolver", request()));
        assertEquals(
                List.of("active"),
                service.resolvePrincipalUsernames(List.of(
                        PersonPrincipal.user("active"),
                        PersonPrincipal.user("disabled"),
                        PersonPrincipal.user("deleted"),
                        PersonPrincipal.user("unknown"))));
    }

    @Test
    void doesNotExpandDisabledRolesOrGroupsReturnedByAResolver() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysUserGroupMapper userGroupMapper = mock(SysUserGroupMapper.class);

        SysRole disabledRole = new SysRole();
        disabledRole.setId("role-1");
        disabledRole.setRoleCode("disabled-role");
        disabledRole.setStatus(SysRole.Status.DISABLED.getValue());
        disabledRole.setDeleted(0);
        when(roleMapper.selectList(any())).thenReturn(List.of(disabledRole));
        when(userRoleMapper.selectUserIdsByRoleId("role-1"))
                .thenReturn(List.of("active"));

        SysGroup disabledGroup = new SysGroup();
        disabledGroup.setId("group-1");
        disabledGroup.setGroupCode("disabled-group");
        disabledGroup.setStatus(SysGroup.Status.DISABLED.getValue());
        disabledGroup.setDeleted(0);
        when(groupMapper.selectList(any())).thenReturn(List.of(disabledGroup));
        when(userGroupMapper.selectUserIdsByGroupId("group-1"))
                .thenReturn(List.of("active"));
        when(userMapper.selectByUsername("active"))
                .thenReturn(user("active", SysUser.Status.ENABLED, 0));

        PersonResolver resolver = resolver(List.of(
                new PersonPrincipal(
                        PersonPrincipalType.ROLE, "disabled-role"),
                new PersonPrincipal(
                        PersonPrincipalType.GROUP, "disabled-group")));
        PersonResolverRuntimeService service = new PersonResolverRuntimeService(
                List.of(resolver),
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                mock(SysOrganizationMapper.class));

        assertEquals(
                List.of(),
                service.resolveUsernames("securityResolver", request()),
                "停用角色或用户组不能继续扩大受控接口的候选范围");
        assertEquals(
                List.of(),
                service.resolvePrincipalUsernames(List.of(
                        new PersonPrincipal(
                                PersonPrincipalType.ROLE,
                                "disabled-role"),
                        new PersonPrincipal(
                                PersonPrincipalType.GROUP,
                                "disabled-group"))));
    }

    private PersonResolver resolver(List<PersonPrincipal> principals) {
        return new PersonResolver() {
            @Override
            public PersonResolverDescriptor descriptor() {
                return new PersonResolverDescriptor(
                        "securityResolver",
                        "安全边界测试解析器",
                        "验证解析结果只能包含有效用户",
                        1,
                        1,
                        Set.of(PersonResolveUsage.ASSIGNEE),
                        Map.of(),
                        false);
            }

            @Override
            public PersonResolveResult resolve(PersonResolveRequest request) {
                return new PersonResolveResult(principals, List.of());
            }
        };
    }

    private PersonResolveRequest request() {
        return new PersonResolveRequest(
                1,
                "trace-1",
                "idempotency-1",
                PersonResolveUsage.ASSIGNEE,
                "process-config-1",
                "process-definition-1",
                "process-instance-1",
                "business-1",
                "approve",
                "经理审批",
                "task-1",
                "expense",
                "entity-1",
                "starter",
                "operator",
                Map.of(),
                Map.of(),
                Map.of());
    }

    private SysUser user(String username, SysUser.Status status, int deleted) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setStatus(status.getValue());
        user.setDeleted(deleted);
        return user;
    }
}
