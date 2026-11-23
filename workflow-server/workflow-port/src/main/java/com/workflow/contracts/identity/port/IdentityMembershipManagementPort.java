package com.workflow.contracts.identity.port;

import java.util.List;

/** 按当前操作人权限读写角色、用户组和组织职务的成员关系。 */
public interface IdentityMembershipManagementPort {
    record PositionAssignment(String id, String positionCode, String positionName, String organizationId,
                              String organizationName, String userId, String username, int revision) { }

    List<PositionAssignment> positionsOfUser(String userId);
    List<PositionAssignment> positionsInOrganization(String organizationId);

    /** 原子替换指定用户的角色集合；空角色集合表示清空。 */
    void replaceUserRoles(List<String> userIds, List<String> roleIds);

    /** 替换用户组的完整成员集合；空集合表示清空。 */
    void replaceGroupMembers(String groupId, List<String> userIds);

    /** 以当前成员作为并发前置条件，替换指定组织和职务下的成员。 */
    void replacePositionMembers(String positionCode, String organizationId, List<String> userIds,
            List<String> expectedUserIds);

    void removePositionAssignment(String assignmentId, int expectedRevision);
}
