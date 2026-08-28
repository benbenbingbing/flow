package com.workflow.contracts.identity.position;

import java.time.Instant;
import java.util.List;

/**
 * 流程发起时冻结的发起人组织上下文。
 *
 * @param snapshotVersion 快照协议版本，V1 固定为 1
 * @param userId 发起人本地用户 ID
 * @param username 发起人本地用户名
 * @param organizationId 发起时所属组织 ID
 * @param departmentId 发起时所属部门 ID，可为空
 * @param units 从最近的归属节点到根节点的完整链
 * @param capturedAt 快照捕获时刻
 */
public record InitiatorOrganizationSnapshot(
        int snapshotVersion,
        String userId,
        String username,
        String organizationId,
        String departmentId,
        List<OrganizationUnitSnapshot> units,
        Instant capturedAt) {

    public static final int VERSION = 1;

    public InitiatorOrganizationSnapshot {
        if (snapshotVersion != VERSION) {
            throw new IllegalArgumentException("不支持的发起人组织快照版本: " + snapshotVersion);
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("发起人用户 ID 不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("发起人用户名不能为空");
        }
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("发起人组织 ID 不能为空");
        }
        units = units == null ? List.of() : List.copyOf(units);
        if (units.isEmpty()) {
            throw new IllegalArgumentException("发起人组织链不能为空");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("快照捕获时刻不能为空");
        }
    }
}
