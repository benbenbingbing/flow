package com.workflow.admin.identity.infrastructure.adapter;

import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.application.SysGroupService;
import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.application.PositionAssignmentQueryService;
import com.workflow.admin.identity.position.application.PositionAssignmentService;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.port.system.SysPositionAssignmentPort;
import com.workflow.port.system.SysPositionAssignmentPort.PositionAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IdentityMembershipManagementAdapter implements SysPositionAssignmentPort {
    private final SysUserService users;
    private final SysRoleMapper roles;
    private final SysGroupService groups;
    private final PositionAssignmentService positions;
    private final PositionAssignmentQueryService positionQueries;

    @Override
    public List<PositionAssignment> listAssignmentsByUser(String userId) {
        return positionQueries.byUserForPort(userId).assignments().stream().map(this::project).toList();
    }

    @Override
    public List<PositionAssignment> listAssignmentsInOrganization(String organizationId) {
        return positionQueries.byOrganizationForPort(organizationId).assignments().stream().map(this::project).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRoles(List<String> userIds, List<String> roleIds) {
        var distinctRoles = roleIds.stream().distinct().toList();
        // 提前验证所有角色，避免删除旧关系后才发现角色标识无效。
        if (!distinctRoles.isEmpty() && roles.selectBatchIds(distinctRoles).size() != distinctRoles.size()) {
            throw new IllegalArgumentException("部分角色不存在");
        }
        users.batchAssignRoles(List.copyOf(userIds), distinctRoles);
    }

    public void replaceGroupMembers(String groupId, List<String> userIds) {
        groups.saveGroupUsers(groupId, List.copyOf(userIds));
    }

    @Override
    public void replacePositionMembers(String positionCode, String organizationId, List<String> userIds,
            List<String> expectedUserIds) {
        positions.setMembersForPort(new PositionRequests.SetMembers(positionCode, organizationId,
                List.copyOf(userIds), List.copyOf(expectedUserIds)));
    }

    @Override
    public void removePositionAssignment(String assignmentId, int expectedRevision) {
        positions.removeForPort(assignmentId, new PositionRequests.RemoveAssignment(expectedRevision));
    }

    private PositionAssignment project(PositionViews.AssignmentView assignment) {
        return new PositionAssignment(assignment.id(), assignment.positionCode(), assignment.positionName(),
                assignment.organizationUnitId(), assignment.organizationUnitName(), assignment.userId(),
                assignment.username(), assignment.revision());
    }

}
