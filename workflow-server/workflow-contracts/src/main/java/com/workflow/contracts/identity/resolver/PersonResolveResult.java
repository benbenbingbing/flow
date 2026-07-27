package com.workflow.contracts.identity.resolver;

import java.util.List;

/**
 * 人员解析器固定返回结果。
 *
 * @param principals 人员主体列表
 * @param warnings   非阻断警告
 */
public record PersonResolveResult(
        List<PersonPrincipal> principals,
        List<String> warnings) {

    public PersonResolveResult {
        principals = principals == null ? List.of() : List.copyOf(principals);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static PersonResolveResult users(List<String> userKeys) {
        return new PersonResolveResult(
                userKeys == null
                        ? List.of()
                        : userKeys.stream().map(PersonPrincipal::user).toList(),
                List.of());
    }
}
