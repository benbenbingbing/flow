package com.workflow.excel.dynamic;

import com.alibaba.excel.metadata.data.ReadCellData;
import java.util.Objects;

/**
 * 不可变单元格；行与工作表坐标由所属 ExcelRawRow 提供。
 * value 为 String、BigDecimal、Boolean 或 null；FORMULA 值只用于报告，不能执行。
 * format 保留数字显示格式（例如 00000），日期转换使用 use1904Windowing。
 */
public record ExcelRawCell(int columnIndex, ExcelCellType type, Object value,
        Integer formatIndex, String format, boolean use1904Windowing) {
    public ExcelRawCell {
        if (columnIndex < 0) throw new IllegalArgumentException("列索引不能为负数");
        Objects.requireNonNull(type, "单元格类型不能为空");
    }

    /** DTO 转换器与动态读取共用的适配入口，不保留 EasyExcel 的可变对象。 */
    public static ExcelRawCell from(ReadCellData<?> cell, int columnIndex, boolean use1904) {
        if (cell == null) return new ExcelRawCell(columnIndex, ExcelCellType.EMPTY, null, null, null, use1904);
        ExcelCellType type;
        Object value;
        if (cell.getFormulaData() != null) {
            type = ExcelCellType.FORMULA;
            value = cell.getFormulaData().getFormulaValue();
        } else {
            type = switch (cell.getType()) {
                case EMPTY -> ExcelCellType.EMPTY;
                case STRING, DIRECT_STRING -> ExcelCellType.STRING;
                case NUMBER -> ExcelCellType.NUMBER;
                case BOOLEAN -> ExcelCellType.BOOLEAN;
                default -> ExcelCellType.ERROR;
            };
            value = switch (type) {
                // 保留文件中的原始数值，不能先按 EasyExcel 的 15 位 MathContext 舍入。
                case NUMBER -> cell.getOriginalNumberValue() == null ? cell.getNumberValue() : cell.getOriginalNumberValue();
                case BOOLEAN -> cell.getBooleanValue();
                case STRING, ERROR -> cell.getStringValue();
                default -> null;
            };
        }
        var dataFormat = cell.getDataFormatData();
        return new ExcelRawCell(columnIndex, type, value,
                dataFormat == null || dataFormat.getIndex() == null ? null : Short.toUnsignedInt(dataFormat.getIndex()),
                dataFormat == null ? null : dataFormat.getFormat(), use1904);
    }

    /** 空行检测不能忽略公式和错误单元格，即使其缓存结果为空。 */
    public boolean isBlank() {
        return type == ExcelCellType.EMPTY || type == ExcelCellType.STRING && (value == null || value.toString().isBlank());
    }
}
