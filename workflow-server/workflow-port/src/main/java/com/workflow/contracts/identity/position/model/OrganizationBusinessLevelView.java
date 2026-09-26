package com.workflow.contracts.identity.position.model;

/**
 * 流程设计器使用的组织业务层级选项。
 *
 * @param code 业务编码，供后续匹配和引用
 * @param name 展示名称，供界面或日志识别
 * @param sortOrder 排序权重，后续用于稳定展示顺序
 */
public record OrganizationBusinessLevelView(
        String code,
        String name,
        int sortOrder) {

    /**
     * 初始化组织业务层级视图，保存构造参数供后续方法使用。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param name 展示名称，供界面或日志识别
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public OrganizationBusinessLevelView {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("组织业务层级编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("组织业务层级名称不能为空");
        }
    }
}
