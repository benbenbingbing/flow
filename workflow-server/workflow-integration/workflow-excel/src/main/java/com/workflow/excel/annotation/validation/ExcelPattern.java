package com.workflow.excel.annotation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 要求整个文本匹配正则表达式，可用于编码、手机号等格式校验；null 合法。 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Pattern(regexp = ".*")
public @interface ExcelPattern {
    /** 整个文本需要匹配的表达式，由业务模块指定其格式规则。 */
    @OverridesAttribute(constraint = Pattern.class, name = "regexp")
    String regexp();

    /** 正则匹配选项，例如忽略大小写；会传给底层 Pattern 校验器。 */
    @OverridesAttribute(constraint = Pattern.class, name = "flags")
    Pattern.Flag[] flags() default {};

    /** 校验失败时供业务模块展示的提示，支持约束参数占位符。 */
    String message() default "格式不正确";

    /** 在不同导入场景中启用此规则；未指定时属于 Default 分组。 */
    Class<?>[] groups() default {};

    /** 供业务模块携带约束级别等元信息，不参与单元格的值转换。 */
    Class<? extends Payload>[] payload() default {};
}
