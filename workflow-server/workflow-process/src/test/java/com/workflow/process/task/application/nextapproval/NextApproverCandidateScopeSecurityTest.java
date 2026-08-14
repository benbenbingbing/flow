package com.workflow.process.task.application.nextapproval;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NextApproverCandidateScopeSecurityTest {

    @Test
    void disabledRolesAndGroupsDoNotExpandTheAllowedCandidateScope() {
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
                .thenReturn(List.of("alice"));

        SysGroup disabledGroup = new SysGroup();
        disabledGroup.setId("group-1");
        disabledGroup.setGroupCode("disabled-group");
        disabledGroup.setStatus(SysGroup.Status.DISABLED.getValue());
        disabledGroup.setDeleted(0);
        when(groupMapper.selectList(any())).thenReturn(List.of(disabledGroup));
        when(userGroupMapper.selectUserIdsByGroupId("group-1"))
                .thenReturn(List.of("alice"));

        SysUser alice = new SysUser();
        alice.setId("user-1");
        alice.setUsername("alice");
        alice.setStatus(SysUser.Status.ENABLED.getValue());
        alice.setDeleted(0);
        when(userMapper.selectByUsername("alice")).thenReturn(alice);

        NextApproverCandidateService service = new NextApproverCandidateService(
                mock(NextApprovalRouteService.class),
                mock(PersonResolverRuntimeService.class),
                userMapper,
                roleMapper,
                userRoleMapper,
                groupMapper,
                userGroupMapper,
                mock(SysOrganizationMapper.class));

        assertEquals(
                List.of(),
                service.resolveAllowed(
                        null,
                        targetWithDisabledScopes(),
                        PersonResolveUsage.CANDIDATE),
                "停用角色或用户组不能继续扩大节点允许选择的人员范围");
    }

    private NextApprovalTarget targetWithDisabledScopes() {
        UserTask task = new UserTask();
        task.setId("manager-review");
        task.setName("经理审批");
        NextApproverSelectionPolicy policy = new NextApproverSelectionPolicy(
                true,
                1,
                true,
                true,
                "CANDIDATE",
                true,
                NextApproverSelectionPolicy.SourceType.SCOPE,
                List.of(
                        new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.ROLE,
                                List.of("disabled-role"),
                                false),
                        new NextApproverSelectionPolicy.Scope(
                                NextApproverSelectionPolicy.ScopeType.GROUP,
                                List.of("disabled-group"),
                                false)),
                null,
                Map.of(),
                "policy-scope-1");
        return new NextApprovalTarget(task, Map.of(), policy);
    }
}
