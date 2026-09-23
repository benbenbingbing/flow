package com.workflow.core.error;

import java.util.List;

/** 已发布表单跨字段校验失败；只携带字段标识和提示，不暴露参与比较的实际数据。 */
public final class FormCrossFieldValidationException extends BusinessConflictException {
    public static final String ERROR_CODE = "FORM_CROSS_FIELD_VALIDATION_FAILED";
    private final List<FieldError> fieldErrors;

    /**
     * 封装字段错误的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param fieldCode 字段编码，后续用于处理字段错误时定位或关联目标
     * @param ruleId 规则ID，后续用于处理字段错误时定位或关联目标
     * @param targetFieldCode 目标字段编码，后续用于处理字段错误时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public record FieldError(String fieldCode, String ruleId, String targetFieldCode, String message) {}

    /**
     * errors 至少包含一个字段错误，调用方按字段展示顺序提供。
     *
     * @param errors {@code errors}，保存在对象中供后续校验、查询或展示
     */
    public FormCrossFieldValidationException(List<FieldError> errors) {
        super(ERROR_CODE, errors.isEmpty() ? "表单跨字段校验失败" : errors.get(0).message());
        this.fieldErrors = List.copyOf(errors);
    }

    /**
     * 读取字段{@code errors}；查询结果供调用方展示或继续处理。
     *
     * @return 字段错误集合，供调用方遍历或展示
     */
    public List<FieldError> getFieldErrors() { return fieldErrors; }
}
