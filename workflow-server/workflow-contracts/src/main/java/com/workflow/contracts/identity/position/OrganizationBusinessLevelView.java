package com.workflow.contracts.identity.position;

/**
 * 流程设计器使用的组织业务层级选项。
 */
public record OrganizationBusinessLevelView(
        String code,
        String name,
        int sortOrder) {

    public OrganizationBusinessLevelView {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("组织业务层级编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("组织业务层级名称不能为空");
        }
    }
}
