package com.workflow.excel.dynamic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保留实际文件坐标的行；rowIndex 从 0 开始，错误展示使用 rowNumber()。
 * cells 是不可变稀疏列映射，缺失列没有键；可安全保留至后续批次处理。
 */
public record ExcelRawRow(int sheetNo, String sheetName, int rowIndex, boolean use1904Windowing,
        Map<Integer, ExcelRawCell> cells) {
    public ExcelRawRow {
        cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells));
    }

    public int rowNumber() { return rowIndex + 1; }

    public boolean isBlank() { return cells.values().stream().allMatch(ExcelRawCell::isBlank); }
}
