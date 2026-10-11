package com.workflow.excel.annotation.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ExcelCodeMapping 中的一项映射；代码和名称都必须唯一，确保导入与导出可以互相还原。 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelCode {
    /** DTO 保存的代码文本，转换器会按字段类型转换；枚举字段使用枚举常量名。 */
    String code();

    /** 导出到 Excel 的显示名称，导入时将该名称还原成对应代码。 */
    String label();
}
