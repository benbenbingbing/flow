package com.workflow.excel.annotation.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.RoundingMode;

/** 配合 ExcelAmountConverter 配置 BigDecimal 金额转换，规则同时作用于导入和导出。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelAmountFormat {
    /** 保留的小数位数，必须非负；默认按分保留两位。 */
    int scale() default 2;

    /** 超出小数位数时的处理方式；默认禁止隐式舍入，需要四舍五入时显式指定 HALF_UP。 */
    RoundingMode roundingMode() default RoundingMode.UNNECESSARY;

    /** 是否接受标准英文逗号千分位分组，错误分组会报错，不会直接删除逗号。 */
    boolean allowThousands() default true;

    /** 导入允许的可选货币前缀，如「￥」；不进行币种换算，导出也不自动附加前缀。 */
    String currencySymbol() default "";

    /** 默认按文本导出以保留精度；设为 false 时输出数值，超过 Excel 15 位有效数字则报错。 */
    boolean writeAsText() default true;
}
