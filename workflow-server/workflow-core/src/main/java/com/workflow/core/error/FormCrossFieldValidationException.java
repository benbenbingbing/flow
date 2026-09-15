package com.workflow.core.error;

import java.util.List;

/** 已发布表单跨字段校验失败；只携带字段标识和提示，不暴露参与比较的实际数据。 */
public final class FormCrossFieldValidationException extends BusinessConflictException {
    public static final String ERROR_CODE = "FORM_CROSS_FIELD_VALIDATION_FAILED";
    private final List<FieldError> fieldErrors;

    public record FieldError(String fieldCode, String ruleId, String targetFieldCode, String message) {}

    /** errors 至少包含一个字段错误，调用方按字段展示顺序提供。 */
    public FormCrossFieldValidationException(List<FieldError> errors) {
        super(ERROR_CODE, errors.isEmpty() ? "表单跨字段校验失败" : errors.get(0).message());
        this.fieldErrors = List.copyOf(errors);
    }

    public List<FieldError> getFieldErrors() { return fieldErrors; }
}
