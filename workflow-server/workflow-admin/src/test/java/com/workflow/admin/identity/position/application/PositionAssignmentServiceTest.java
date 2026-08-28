package com.workflow.admin.identity.position.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.position.api.PositionErrorCode;
import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentBatchMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignment;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PositionAssignmentServiceTest {

    private final SysPositionMapper positionMapper =
            mock(SysPositionMapper.class);
    private final SysPositionAssignmentMapper assignmentMapper =
            mock(SysPositionAssignmentMapper.class);
    private final SysOrganizationMapper organizationMapper =
            mock(SysOrganizationMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final PositionOrganizationScopeService scopeService =
            mock(PositionOrganizationScopeService.class);
    private final PositionAssignmentService service =
            new PositionAssignmentService(
                    positionMapper,
                    assignmentMapper,
                    mock(SysPositionAssignmentBatchMapper.class),
                    organizationMapper,
                    userMapper,
                    scopeService,
                    new ObjectMapper());

    private SysPosition position;
    private SysOrganization target;

    @BeforeEach
    void setUp() {
        position = new SysPosition();
        position.setId("position-1");
        position.setPositionCode("DEPARTMENT_MANAGER");
        position.setApplicableUnitType(SysPosition.ApplicableUnitType.DEPT.name());
        position.setHolderMode(SysPosition.HolderMode.SINGLE.name());
        position.setStatus(SysPosition.Status.ENABLED.name());
        when(positionMapper.selectByCodes(List.of("DEPARTMENT_MANAGER")))
                .thenReturn(List.of(position));

        target = enabledUnit("dept-target", "org-root", "dept");
        when(organizationMapper.selectById("dept-target"))
                .thenReturn(target);
    }

    @Test
    void precheckRejectsUserOutsideTargetUnitSubtree() {
        SysUser user = enabledUser("user-outside", "org-root", "dept-other");
        when(userMapper.selectById("user-outside")).thenReturn(user);
        when(organizationMapper.selectById("dept-other"))
                .thenReturn(enabledUnit("dept-other", "org-root", "dept"));
        when(organizationMapper.selectById("org-root"))
                .thenReturn(enabledUnit("org-root", "0", "org"));

        PositionViews.PrecheckResult result = service.precheck(
                batch("user-outside", false));

        assertFalse(result.valid());
        assertEquals(PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN.name(),
                result.items().get(0).errorCode());
    }

    @Test
    void precheckRejectsOccupiedSinglePositionWithoutExplicitTransfer() {
        SysUser user = enabledUser("user-new", "org-root", "dept-target");
        when(userMapper.selectById("user-new")).thenReturn(user);
        SysPositionAssignment existing = new SysPositionAssignment();
        existing.setId("assignment-existing");
        existing.setUserId("user-old");
        when(assignmentMapper.selectOverlaps(
                any(), any(), any(), isNull(), isNull()))
                .thenReturn(List.of(existing));

        PositionViews.PrecheckResult result = service.precheck(
                batch("user-new", false));

        assertFalse(result.valid());
        assertEquals(PositionErrorCode.SINGLE_POSITION_OCCUPIED.name(),
                result.items().get(0).errorCode());
    }

    private static PositionRequests.AssignmentBatch batch(
            String userId, boolean replaceExisting) {
        return new PositionRequests.AssignmentBatch(
                true,
                "组织负责人调整",
                List.of(new PositionRequests.AssignmentItem(
                        "DEPARTMENT_MANAGER",
                        "dept-target",
                        userId,
                        OffsetDateTime.of(
                                2026, 9, 1, 0, 0, 0, 0,
                                ZoneOffset.ofHours(8)),
                        null,
                        true,
                        0,
                        replaceExisting)));
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
