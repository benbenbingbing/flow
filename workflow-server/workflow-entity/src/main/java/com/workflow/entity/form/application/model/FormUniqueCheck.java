package com.workflow.entity.form.application.model;

/**
 * 表单唯一规则检查结果，供提前预检和事务内最终校验共享。
 *
 * @param checked {@code checked}，保存在对象中供后续校验、查询或展示
 * @param available 可用，保存在对象中供后续校验、查询或展示
 * @param ruleId 规则ID，后续用于处理表单唯一检查时定位或关联目标
 * @param fieldCode 字段编码，后续用于处理表单唯一检查时定位或关联目标
 * @param message 消息，保存在对象中供后续校验、查询或展示
 */
public record FormUniqueCheck(
        boolean checked,
        boolean available,
        String ruleId,
        String fieldCode,
        String message) {

    /**
     * 没有表单规则、预检关闭、条件不成立或空值被忽略。
     *
     * @param ruleId 规则ID，后续用于处理{@code skipped}时定位或关联目标
     * @param fieldCode 字段编码，后续用于处理{@code skipped}时定位或关联目标
     * @return 处理后的{@code skipped}结果，供调用方继续处理
     */
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
