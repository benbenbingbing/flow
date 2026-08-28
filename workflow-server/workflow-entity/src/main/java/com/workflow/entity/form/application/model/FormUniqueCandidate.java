package com.workflow.entity.form.application.model;

import java.util.Map;

/**
 * 唯一规则对一条最终记录的求值结果。
 *
 * @param applicable 当前记录是否进入规则约束范围
 * @param ignored     是否因空值忽略而无需校验
 * @param normalizedValue 规范化后的比较值；ignored=true 时为空
 * @param record      旧记录与提交补丁合并后的最终记录
 */
public record FormUniqueCandidate(
        boolean applicable,
        boolean ignored,
        String normalizedValue,
        Map<String, Object> record) {
}
