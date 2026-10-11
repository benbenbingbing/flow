package com.workflow.excel.annotation.converter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 配合 ExcelStringConverter 对导入和导出的文本执行清理，保留业务所需的前导零和文本类型。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelStringFormat {
    /** 是否使用 String.strip 去除首尾空白；保留原始空白时还需关闭读取配置的 autoTrim。 */
    boolean trim() default true;

    /** 清理后为空字符串时是否转换为 null，供 ExcelNotNull 等必填规则继续校验。 */
    boolean emptyToNull() default true;
}
