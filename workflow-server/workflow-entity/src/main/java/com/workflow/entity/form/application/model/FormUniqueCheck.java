package com.workflow.entity.form.application.model;

/**
 * 表单唯一规则检查结果，供提前预检和事务内最终校验共享。
 */
public record FormUniqueCheck(
        boolean checked,
        boolean available,
        String ruleId,
        String fieldCode,
        String message) {

    /** 没有表单规则、预检关闭、条件不成立或空值被忽略。 */
    public static FormUniqueCheck skipped(
            String ruleId,
            String fieldCode) {
        return new FormUniqueCheck(
                false,
                true,
                ruleId,
                fieldCode,
                null);
    }
}
