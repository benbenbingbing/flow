package com.workflow.admin.identity.user.application;

import com.workflow.admin.auth.infrastructure.AuthRefreshSessionMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.position.application.PositionAssignmentQueryService;
import com.workflow.admin.identity.position.application.PositionOrganizationScopeService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserServiceOrganizationMembershipTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final SysUserService service = new SysUserService(
            userMapper,
            mock(SysRoleMapper.class),
            mock(SysUserRoleMapper.class),
            mock(AuthRefreshSessionMapper.class),
            organizationMapper,
            mock(PositionAssignmentQueryService.class),
            mock(PositionOrganizationScopeService.class));

    @Test
    void allowsMovingOrganizationWhenDepartmentIsExplicitlyCleared() {
        SysUser existing = enabledUser("user-1", "org-a", "dept-a");
        when(userMapper.selectById("user-1")).thenReturn(existing);
        when(organizationMapper.selectById("org-b"))
                .thenReturn(enabledUnit("org-b", "0", "org"));
        SysUser requested = enabledUser("user-1", "org-b", "   ");

        assertDoesNotThrow(() -> service.saveUser(requested));

        verify(userMapper).updateById(requested);
    }

    @Test
    void unrelatedUpdateKeepsExistingOrganizationMembership() {
        SysUser existing = enabledUser("user-1", "org-a", "dept-a");
        when(userMapper.selectById("user-1")).thenReturn(existing);
        when(organizationMapper.selectById("org-a"))
                .thenReturn(enabledUnit("org-a", "0", "org"));
        when(organizationMapper.selectById("dept-a"))
                .thenReturn(enabledUnit("dept-a", "org-a", "dept"));
        SysUser requested = enabledUser("user-1", null, null);
        requested.setNickname("新昵称");

        assertDoesNotThrow(() -> service.saveUser(requested));

        verify(userMapper).updateById(requested);
    }

    @Test
    void rejectsDepartmentOutsideUsersDeclaredOrganization() {
        SysUser existing = enabledUser("user-1", "org-a", "dept-a");
        when(userMapper.selectById("user-1")).thenReturn(existing);
        when(organizationMapper.selectById("org-a"))
                .thenReturn(enabledUnit("org-a", "0", "org"));
        when(organizationMapper.selectById("dept-b"))
                .thenReturn(enabledUnit("dept-b", "org-b", "dept"));
        when(organizationMapper.selectById("org-b"))
                .thenReturn(enabledUnit("org-b", "0", "org"));
        SysUser requested = enabledUser("user-1", "org-a", "dept-b");

        assertThrows(IllegalArgumentException.class,
                () -> service.saveUser(requested));

        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    private static SysUser enabledUser(
            String id, String orgId, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setOrgId(orgId);
        user.setDeptId(deptId);
        user.setStatus(SysUser.Status.ENABLED.getValue());
        return user;
    }

    private static SysOrganization enabledUnit(
            String id, String parentId, String type) {
        SysOrganization unit = new SysOrganization();
        unit.setId(id);
        unit.setParentId(parentId);
        unit.setType(type);
        unit.setStatus(SysOrganization.Status.ENABLED.getValue());
        return unit;
    }
}
