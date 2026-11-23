package com.workflow.port.system;

import java.util.List;

/** 查询及维护岗位任职关系，更新时使用预期成员或版本校验并发。 */
public interface SysPositionAssignmentPort {
    record PositionAssignment(String id, String positionCode, String positionName, String organizationId,
                              String organizationName, String userId, String username, int revision) { }

    /** 读操作。查询该用户当前的岗位任职。 */
    List<PositionAssignment> listAssignmentsByUser(String userId);

    /** 读操作。查询该组织的岗位任职和用于条件变更的修订号。 */
    List<PositionAssignment> listAssignmentsInOrganization(String organizationId);

    /** 写操作。以当前成员作为并发前置条件，替换指定组织和职务下的成员。 */
    void replacePositionMembers(String positionCode, String organizationId, List<String> userIds,
            List<String> expectedUserIds);

    /** 写操作。仅在任职修订号与读取值一致时删除该任职。 */
    void removePositionAssignment(String assignmentId, int expectedRevision);
}
