package com.workflow.excel.validation;

/**
 * 一个字段的校验错误，不保存原始单元格值，便于业务模块安全地记录或展示提示。
 *
 * @param fieldName 属性路径，用于定位 DTO 字段；嵌套校验时可能包含子属性或集合索引
 * @param message 已完成插值的错误提示，供业务模块生成导入结果
 */
public record ExcelValidationError(String fieldName, String message) {
}
