package com.workflow.excel.annotation.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 配合 Excel 日期转换器配置文本格式及 Date 字段的时区，不影响未使用转换器的字段。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelDateFormat {
    /** 本地日期/时间的导出格式，也用于导入；空值使用默认格式，遵循 DateTimeFormatter 模式，不包含时区或偏移。 */
    String value() default "";

    /** 额外接受的导入格式；未指定时接受转换器内置的常用格式。 */
    String[] readPatterns() default {};

    /** Date 与本地日期时间互转时使用的时区，避免依赖部署机器的默认时区；LocalDate/LocalDateTime 不转换时区。 */
    String zoneId() default "Asia/Shanghai";
}
