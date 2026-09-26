package com.workflow.contracts.identity.position.model;

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

    /**
     * 初始化{@code initiator}组织快照，保存构造参数供后续方法使用。
     *
     * @param snapshotVersion 快照版本，保存在对象中供后续校验、查询或展示
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param organizationId 组织 ID，供后续身份归属和访问判断
     * @param departmentId 部门 ID，供后续身份归属和访问判断
     * @param units {@code units}，保存在对象中供后续校验、查询或展示
     * @param capturedAt {@code captured}时间，后续用于判断有效期或展示该事件的发生时间
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
