package com.workflow.admin.identity.position.infrastructure.adapter;

import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.position.OrganizationPositionErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationPositionDirectoryAdapterTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final OrganizationPositionDirectoryAdapter adapter =
            new OrganizationPositionDirectoryAdapter(
                    userMapper,
                    organizationMapper,
                    mock(SysPositionMapper.class),
                    mock(SysPositionAssignmentMapper.class),
                    mock(SysDictItemMapper.class));

    @Test
    void usernameCaptureAllowsExactlyThirtyTwoOrganizationNodes() {
        SysUser user = enabledUser("user-1", "alice", "unit-31", "unit-0");
        when(userMapper.selectById("alice")).thenReturn(null);
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        stubChain(32);

        InitiatorOrganizationSnapshot snapshot =
                adapter.captureInitiatorSnapshot("alice");

        assertEquals(32, snapshot.units().size());
        assertEquals("unit-0", snapshot.units().get(0).id());
        assertEquals("unit-31", snapshot.units().get(31).id());
    }

    @Test
    void captureRejectsThirtyThreeOrganizationNodes() {
        SysUser user = enabledUser("user-1", "alice", "unit-32", "unit-0");
        when(userMapper.selectById("user-1")).thenReturn(user);
        stubChain(33);

        OrganizationPositionDirectoryException exception = assertThrows(
                OrganizationPositionDirectoryException.class,
                () -> adapter.captureInitiatorSnapshot("user-1"));

        assertEquals(OrganizationPositionErrorCode.HIERARCHY_EXHAUSTED,
                exception.errorCode());
    }

    @Test
    void activeUnitLookupDistinguishesMissingAndDisabled() {
        when(organizationMapper.selectById("missing")).thenReturn(null);
        SysOrganization disabled = unit("disabled", "0", "org", "1");
        when(organizationMapper.selectById("disabled")).thenReturn(disabled);

        OrganizationPositionDirectoryException missing = assertThrows(
                OrganizationPositionDirectoryException.class,
                () -> adapter.requireActiveOrganizationUnit("missing"));
        OrganizationPositionDirectoryException inactive = assertThrows(
                OrganizationPositionDirectoryException.class,
                () -> adapter.requireActiveOrganizationUnit("disabled"));

        assertEquals(OrganizationPositionErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
                missing.errorCode());
        assertEquals(OrganizationPositionErrorCode.ORGANIZATION_UNIT_DISABLED,
                inactive.errorCode());
    }

    private void stubChain(int size) {
        Map<String, SysOrganization> units = new HashMap<>();
        for (int index = 0; index < size; index++) {
            String id = "unit-" + index;
            String parentId = index == size - 1
                    ? "0" : "unit-" + (index + 1);
            String type = index == size - 1 ? "org" : "dept";
            units.put(id, unit(id, parentId, type, "0"));
        }
        units.forEach((id, unit) -> when(organizationMapper.selectById(id))
                .thenReturn(unit));
    }

    private static SysUser enabledUser(
            String id, String username, String orgId, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setOrgId(orgId);
        user.setDeptId(deptId);
        user.setStatus(SysUser.Status.ENABLED.getValue());
        return user;
    }

    private static SysOrganization unit(
            String id, String parentId, String type, String status) {
        SysOrganization unit = new SysOrganization();
        unit.setId(id);
        unit.setOrgName(id);
        unit.setParentId(parentId);
        unit.setType(type);
        unit.setStatus(status);
        return unit;
    }
}
