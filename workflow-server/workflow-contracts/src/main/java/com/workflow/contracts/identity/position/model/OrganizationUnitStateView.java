package com.workflow.contracts.identity.position.model;

/**
 * 已通过存在性和启用状态校验的实时组织节点投影。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param type 类型标识，决定后续组织单元状态视图采用的处理分支
 * @param directoryRevision 目录修订号，后续用于判断身份数据是否过期
 */
public record OrganizationUnitStateView(
        String id,
        String type,
        String directoryRevision) {

    /**
     * 初始化组织单元状态视图，保存构造参数供后续方法使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param type 类型标识，决定后续组织单元状态视图采用的处理分支
     * @param directoryRevision 目录修订号，后续用于判断身份数据是否过期
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public OrganizationUnitStateView {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("组织节点 ID 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("组织节点类型不能为空");
        }
    }
}
