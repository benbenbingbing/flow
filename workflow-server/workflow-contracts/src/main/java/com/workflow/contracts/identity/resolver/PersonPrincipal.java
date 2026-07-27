package com.workflow.contracts.identity.resolver;

import java.util.Objects;

/**
 * 人员解析结果中的稳定主体引用。
 *
 * @param type 主体类型
 * @param key  用户名、用户组编码、角色编码或组织编码
 */
public record PersonPrincipal(PersonPrincipalType type, String key) {

    public PersonPrincipal {
        Objects.requireNonNull(type, "type");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("人员主体编码不能为空");
        }
        key = key.trim();
    }

    public static PersonPrincipal user(String key) {
        return new PersonPrincipal(PersonPrincipalType.USER, key);
    }
}
