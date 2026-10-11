package com.workflow.excel.annotation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 要求文本非 null 且包含非空白字符，用于必填的姓名、编码等文本列。 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@NotBlank
public @interface ExcelNotBlank {
    /** 校验失败时供业务模块展示的提示，支持约束参数占位符。 */
    String message() default "不能为空或仅包含空白字符";

    /** 在不同导入场景中启用此规则；未指定时属于 Default 分组。 */
    Class<?>[] groups() default {};

    /** 供业务模块携带约束级别等元信息，不参与单元格的值转换。 */
    Class<? extends Payload>[] payload() default {};
}
