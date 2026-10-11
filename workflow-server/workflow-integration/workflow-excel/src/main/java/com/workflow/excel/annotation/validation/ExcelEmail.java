package com.workflow.excel.annotation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Email;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验文本的邮箱格式；null 和空文本合法，必填邮箱应搭配 ExcelNotBlank。 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Email
public @interface ExcelEmail {
    /** 校验失败时供业务模块展示的提示，支持约束参数占位符。 */
    String message() default "邮箱格式不正确";

    /** 在不同导入场景中启用此规则；未指定时属于 Default 分组。 */
    Class<?>[] groups() default {};

    /** 供业务模块携带约束级别等元信息，不参与单元格的值转换。 */
    Class<? extends Payload>[] payload() default {};
}
