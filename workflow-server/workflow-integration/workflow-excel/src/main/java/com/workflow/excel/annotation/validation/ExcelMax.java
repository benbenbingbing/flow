package com.workflow.excel.annotation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.DecimalMax;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验整数、BigDecimal、BigInteger 或数值文本的上限；null 合法，金额建议使用 BigDecimal。 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@DecimalMax("0")
public @interface ExcelMax {
    /** 十进制上限文本，避免使用浮点数配置导致金额边界精度丢失。 */
    @OverridesAttribute(constraint = DecimalMax.class, name = "value")
    String value();

    /** 是否允许恰好等于上限，默认允许。 */
    @OverridesAttribute(constraint = DecimalMax.class, name = "inclusive")
    boolean inclusive() default true;

    /** 校验失败时供业务模块展示的提示，支持约束参数占位符。 */
    String message() default "必须满足最大值 {value}，包含边界：{inclusive}";

    /** 在不同导入场景中启用此规则；未指定时属于 Default 分组。 */
    Class<?>[] groups() default {};

    /** 供业务模块携带约束级别等元信息，不参与单元格的值转换。 */
    Class<? extends Payload>[] payload() default {};
}
