package com.workflow.admin.identity.position.application;

import com.workflow.admin.identity.position.api.error.PositionErrorCode;
import com.workflow.admin.identity.position.api.error.PositionManagementException;
import com.workflow.admin.identity.position.api.request.PositionRequests;
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
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 当前集合替换的业务约束与并发冲突，不再测试退役的时间区间及批次状态机。 */
class PositionAssignmentServiceTest {
    private final SysPositionMapper positions = mock(SysPositionMapper.class);
    private final SysPositionAssignmentMapper assignments = mock(SysPositionAssignmentMapper.class);
    private final SysOrganizationMapper organizations = mock(SysOrganizationMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final PositionOrganizationScopeService scope = mock(PositionOrganizationScopeService.class);
    private final PositionAssignmentService service = new PositionAssignmentService(positions, assignments, organizations, users, scope);
    private SysPosition position;
    @BeforeEach void prepare() {
        position = new SysPosition();
        position.setId("position"); position.setPositionCode("MANAGER");
        position.setHolderMode("SINGLE"); position.setApplicableUnitType("DEPT"); position.setStatus("ENABLED");
        when(positions.selectByCode("MANAGER")).thenReturn(position);
        when(positions.selectForUpdate("position")).thenReturn(position);
        SysOrganization unit = new SysOrganization();
        unit.setId("dept"); unit.setType("dept"); unit.setStatus("0");
        when(organizations.selectForUpdate("dept")).thenReturn(unit);
        when(assignments.selectMembersForUpdate(any(), any())).thenReturn(List.of());
        SysUser user = new SysUser(); user.setId("new"); user.setDeptId("dept"); user.setStatus("0");
        when(users.selectForUpdate("new")).thenReturn(user);
    }
    @Test void replacingSingleHolderDeletesOldRelationAndInsertsNewWithoutHistory() {
        SysPositionAssignment old = relation("old");
        when(assignments.selectMembersForUpdate(any(), any())).thenReturn(List.of(old));
        service.setMembers(request(List.of("new"), List.of("old")));
        verify(assignments).deleteById("relation-old");
        verify(assignments).insert(any(SysPositionAssignment.class));
    }
    @Test void staleEditorCannotOverwriteChangedMembership() {
        when(assignments.selectMembersForUpdate(any(), any())).thenReturn(List.of(relation("other")));
        var failure = assertThrows(PositionManagementException.class,
                () -> service.setMembers(request(List.of("new"), List.of("old"))));
        assertEquals(PositionErrorCode.ASSIGNMENT_REVISION_CONFLICT, failure.errorCode());
        verify(assignments, never()).insert(any(SysPositionAssignment.class));
    }
    @Test void duplicateSubmissionDoesNotCreateDuplicateRelations() {
        when(assignments.selectMembersForUpdate(any(), any())).thenReturn(List.of(relation("new")));
        service.setMembers(request(List.of("new"), List.of("old")));
        verify(assignments, never()).insert(any(SysPositionAssignment.class));
        verify(assignments, never()).deleteById(any(String.class));
    }
    @Test void singlePositionRejectsMultipleUsers() {
        var failure = assertThrows(PositionManagementException.class,
                () -> service.setMembers(request(List.of("new", "other"), List.of())));
        assertEquals(PositionErrorCode.SINGLE_POSITION_OCCUPIED, failure.errorCode());
    }
    @Test void rejectsUserOutsideTargetSubtree() {
        SysUser user = new SysUser(); user.setId("new"); user.setDeptId("outside"); user.setStatus("0");
        when(users.selectForUpdate("new")).thenReturn(user);
        var failure = assertThrows(PositionManagementException.class,
                () -> service.setMembers(request(List.of("new"), List.of())));
        assertEquals(PositionErrorCode.ORGANIZATION_SCOPE_FORBIDDEN, failure.errorCode());
    }
    @Test void clearAllowsCleaningDisabledPosition() {
        position.setStatus("DISABLED");
        when(assignments.selectMembersForUpdate(any(), any())).thenReturn(List.of(relation("old")));
        service.setMembers(request(List.of(), List.of("old")));
        verify(assignments).delete(any());
    }
    private PositionRequests.SetMembers request(List<String> desired, List<String> expected) {
        return new PositionRequests.SetMembers("MANAGER", "dept", desired, expected);
    }
    private SysPositionAssignment relation(String user) {
        SysPositionAssignment relation = new SysPositionAssignment();
        relation.setId("relation-" + user); relation.setUserId(user); relation.setPositionId("position");
        relation.setOrganizationUnitId("dept"); relation.setRevision(1);
        return relation;
    }
}
