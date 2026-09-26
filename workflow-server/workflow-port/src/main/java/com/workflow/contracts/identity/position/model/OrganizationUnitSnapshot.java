package com.workflow.contracts.identity.position.model;

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

    /**
     * 初始化组织单元快照，保存构造参数供后续方法使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续组织单元快照采用的处理分支
     * @param businessLevelCode 业务层级编码，后续用于初始化组织单元快照时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public OrganizationUnitSnapshot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("组织节点 ID 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("组织节点类型不能为空");
        }
    }
}
