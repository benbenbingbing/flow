package com.workflow.admin.identity.position.application;

import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.PositionManagementException;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionAssignmentQueryServiceTest {

    @Test
    void userDetailDoesNotDiscloseIdentityOutsideVisibleScope() {
        SysPositionAssignmentMapper assignmentMapper =
                mock(SysPositionAssignmentMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        PositionOrganizationScopeService scopeService =
                mock(PositionOrganizationScopeService.class);
        PositionAssignmentQueryService service =
                new PositionAssignmentQueryService(
                        assignmentMapper,
                        mock(SysOrganizationMapper.class),
                        userMapper,
                        scopeService);
        when(scopeService.visibleUnitIds()).thenReturn(List.of("dept-visible"));
        SysUser target = new SysUser();
        target.setId("user-outside");
        target.setUsername("sensitive-username");
        target.setDeptId("dept-outside");
        when(userMapper.selectById("user-outside")).thenReturn(target);

        PositionManagementException exception = assertThrows(
                PositionManagementException.class,
                () -> service.byUser("user-outside"));

        assertEquals(403, exception.status());
        assertEquals(PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN,
                exception.errorCode());
        verify(assignmentMapper, never()).selectRowsByUser(any(), any());
    }
}
