package com.workflow.contracts.process.assignment.model;

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

    /**
     * 初始化人员{@code resolve}结果，保存构造参数供后续方法使用。
     *
     * @param principals {@code principals}，保存在对象中供后续校验、查询或展示
     * @param warnings {@code warnings}，保存在对象中供后续校验、查询或展示
     */
    public PersonResolveResult {
        principals = principals == null ? List.of() : List.copyOf(principals);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 处理用户集合，并将结果传给后续步骤。
     *
     * @param userKeys 用户键集合，作为 {@code PersonResolveResult} 的输入影响后续处理
     * @return 处理后的用户集合结果，供调用方继续处理
     */
    public static PersonResolveResult users(List<String> userKeys) {
        return new PersonResolveResult(
                userKeys == null
                        ? List.of()
                        : userKeys.stream().map(PersonPrincipal::user).toList(),
                List.of());
    }
}
