package com.workflow.contracts.identity.position;

/**
 * 发起人组织链中的单个冻结节点。
 *
 * @param id 组织节点 ID
 * @param name 捕获时名称，仅用于展示和审计
 * @param type 稳定节点类型，当前为 {@code org/dept}
 * @param businessLevelCode 捕获时业务层级编码，可为空
 */
public record OrganizationUnitSnapshot(
        String id,
        String name,
        String type,
        String businessLevelCode) {

    public OrganizationUnitSnapshot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("组织节点 ID 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("组织节点类型不能为空");
        }
    }
}
