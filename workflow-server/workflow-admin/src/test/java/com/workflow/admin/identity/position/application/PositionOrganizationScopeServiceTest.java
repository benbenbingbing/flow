package com.workflow.admin.identity.position.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.security.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PositionOrganizationScopeServiceTest {

    private final CurrentUserRoleService roleService =
            mock(CurrentUserRoleService.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final PositionOrganizationScopeService service =
            new PositionOrganizationScopeService(
                    roleService, userMapper, organizationMapper);

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("actor-1", "operator");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void superAdministratorCanReadAllUnits() {
        when(roleService.isSuperAdmin()).thenReturn(true);

        assertNull(service.visibleUnitIds());
    }

    @Test
    void ordinaryAdministratorUsesDepartmentSubtree() {
        when(roleService.isSuperAdmin()).thenReturn(false);
        SysUser actor = enabledUser("actor-1", "org-a", "dept-a");
        when(userMapper.selectById("actor-1")).thenReturn(actor);
        SysOrganization department = enabledUnit("dept-a", "root", "dept");
        SysOrganization child = enabledUnit("team-a", "dept-a", "dept");
        when(organizationMapper.selectById("dept-a")).thenReturn(department);
        when(organizationMapper.selectChildren("dept-a"))
                .thenReturn(List.of(child));
        when(organizationMapper.selectChildren("team-a"))
                .thenReturn(List.of());

        assertEquals(List.of("dept-a", "team-a"), service.visibleUnitIds());
    }

    @Test
    void ordinaryAdministratorWithoutOrganizationAnchorIsDenied() {
        when(roleService.isSuperAdmin()).thenReturn(false);
        when(userMapper.selectById("actor-1"))
                .thenReturn(enabledUser("actor-1", null, null));

        PositionManagementException exception = assertThrows(
                PositionManagementException.class,
                service::visibleUnitIds);

        assertEquals(403, exception.status());
        assertEquals(PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN,
                exception.errorCode());
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
