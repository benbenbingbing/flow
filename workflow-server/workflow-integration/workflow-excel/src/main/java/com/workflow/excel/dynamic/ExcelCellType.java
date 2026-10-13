package com.workflow.excel.dynamic;

/** 文件原始类型；公式单独标记，禁止将缓存计算结果当成可信导入值。 */
public enum ExcelCellType {
    EMPTY, STRING, NUMBER, BOOLEAN, ERROR, FORMULA
}
