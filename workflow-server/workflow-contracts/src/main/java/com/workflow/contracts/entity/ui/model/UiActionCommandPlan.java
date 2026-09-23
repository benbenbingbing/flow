package com.workflow.contracts.entity.ui.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关联内容本地写操作的结构化执行计划。
 *
 * @param commands {@code commands}，保存在对象中供后续校验、查询或展示
 * @param result 结果，保存在对象中供后续校验、查询或展示
 */
public record UiActionCommandPlan(
        List<UiActionMutationCommand> commands,
        Map<String, Object> result) {

    /**
     * 初始化界面动作命令方案，保存构造参数供后续方法使用。
     *
     * @param commands {@code commands}，保存在对象中供后续校验、查询或展示
     * @param result 结果，保存在对象中供后续校验、查询或展示
     */
    public UiActionCommandPlan {
        commands = commands == null ? List.of() : List.copyOf(commands);
        result = result == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
}
