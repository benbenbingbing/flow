package com.workflow.excel.annotation.converter;

import com.workflow.excel.annotation.validation.ExcelCode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 配合 ExcelCodeConverter 定义静态代码与显示名称的双向映射，动态字典由业务模块自行提供转换器。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelCodeMapping {
    /** 代码与名称映射，至少一项；不同代码不能映射到相同名称或相同 Java 值。 */
    ExcelCode[] value();

    /** 导入除显示名称外是否也允许直接填写配置中的代码。 */
    boolean allowCode() default true;

    /** 未知代码或名称是否报错；设为 false 时按字段类型保留原值，不能转换成目标类型的值仍报错。 */
    boolean strict() default true;
}
