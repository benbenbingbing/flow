package com.workflow.contracts.process.assignment.model;

import java.util.Objects;

/**
 * 人员解析结果中的稳定主体引用。
 *
 * @param type 主体类型
 * @param key  用户名、用户组编码、角色编码或组织编码
 */
public record PersonPrincipal(PersonPrincipalType type, String key) {

    /**
     * 初始化人员{@code principal}，保存构造参数供后续方法使用。
     *
     * @param type 类型标识，决定后续人员{@code principal}采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public PersonPrincipal {
        Objects.requireNonNull(type, "type");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("人员主体编码不能为空");
        }
        key = key.trim();
    }

    /**
     * 处理用户，并将结果传给后续步骤。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的用户结果，供调用方继续处理
     */
    public static PersonPrincipal user(String key) {
        return new PersonPrincipal(PersonPrincipalType.USER, key);
    }
}
