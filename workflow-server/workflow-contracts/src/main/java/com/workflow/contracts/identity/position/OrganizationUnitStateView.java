package com.workflow.contracts.identity.position;

/**
 * 已通过存在性和启用状态校验的实时组织节点投影。
 */
public record OrganizationUnitStateView(
        String id,
        String type,
        String directoryRevision) {

    public OrganizationUnitStateView {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("组织节点 ID 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("组织节点类型不能为空");
        }
    }
}
