package com.workflow.contracts.process.variable.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 原子设置及删除变量；null 表示写入 null，删除必须显式指定名称。
 * @param meta 幂等操作标识
 * @param taskId 目标作用域标识
 * @param setValues 要设置的业务变量
 * @param removeNames 要删除的变量名称
 */
public record TaskVariablePatch(
        CommandMeta meta,
        String taskId,
        Map<String,Object> setValues,
        Set<String> removeNames) {
    public TaskVariablePatch {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        setValues = ProcessValues.snapshot(setValues);
        removeNames = removeNames == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(removeNames));
        if (!Collections.disjoint(setValues.keySet(), removeNames)) throw new IllegalArgumentException("同一变量不能同时设置和删除"); }

}
