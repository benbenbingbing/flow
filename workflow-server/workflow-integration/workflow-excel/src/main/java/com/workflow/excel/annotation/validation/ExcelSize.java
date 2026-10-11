package com.workflow.excel.annotation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 限制文本长度或集合、Map、数组的元素数，边界包含端点；null 合法，必填应另加非空约束。 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Size
public @interface ExcelSize {
    /** 最小长度，参与读取时的字段校验，包含此边界。 */
    @OverridesAttribute(constraint = Size.class, name = "min")
    int min() default 0;

    /** 最大长度，参与读取时的字段校验，包含此边界。 */
    @OverridesAttribute(constraint = Size.class, name = "max")
    int max() default Integer.MAX_VALUE;

    /** 校验失败时供业务模块展示的提示，支持约束参数占位符。 */
    String message() default "长度必须在 {min} 到 {max} 之间";

    /** 在不同导入场景中启用此规则；未指定时属于 Default 分组。 */
    Class<?>[] groups() default {};

    /** 供业务模块携带约束级别等元信息，不参与单元格的值转换。 */
    Class<? extends Payload>[] payload() default {};
}
